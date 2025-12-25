package com.example.androidclient

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

// Data class cho UI (Vẫn giữ nguyên để không phải sửa UI)
data class ChatMessage(
    val user: String,
    val content: String,
    val image: String? = null,
    val fileData: String? = null,
    val fileName: String? = null,
    val isMine: Boolean
)

// 👇 QUAN TRỌNG: Đổi từ ViewModel -> AndroidViewModel(application) để lấy Context làm DB
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    val messages = mutableStateListOf<ChatMessage>()
    var myName = ""
    private var mSocket: Socket? = null

    // 👇 Khởi tạo Database
    private val database = ChatDatabase.getDatabase(application)
    private val chatDao = database.chatDao()

    private val SERVER_URL = "http://192.168.148.167:3000" // ⚠️ Check IP

    fun joinChat(name: String) {
        myName = name

        // 1. Load lịch sử tin nhắn cũ từ Database ngay khi tham gia
        loadHistoryFromDb()

        // 2. Sau đó mới kết nối mạng
        connectSocket()
    }

    // 👇 Hàm mới: Load tin nhắn từ DB lên màn hình
    private fun loadHistoryFromDb() {
        viewModelScope.launch(Dispatchers.IO) {
            val history = chatDao.getAllMessages()

            // Chuyển về luồng chính để cập nhật UI
            launch(Dispatchers.Main) {
                history.forEach { entity ->
                    // Convert từ Entity (DB) -> ChatMessage (UI)
                    messages.add(
                        ChatMessage(
                            user = entity.user,
                            content = entity.content,
                            image = entity.image,
                            fileData = entity.fileData,
                            fileName = entity.fileName,
                            isMine = (entity.user == myName) // Check lại xem tin này có phải của mình với cái tên hiện tại ko
                        )
                    )
                }
            }
        }
    }

    // 👇 Hàm mới: Lưu 1 tin nhắn vào DB
    private fun saveMessageToDb(msg: ChatMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = MessageEntity(
                user = msg.user,
                content = msg.content,
                image = msg.image,
                fileData = msg.fileData,
                fileName = msg.fileName,
                isMine = msg.isMine
            )
            chatDao.insertMessage(entity)
        }
    }

    private fun connectSocket() {
        try {
            val options = IO.Options().apply { forceNew = true }
            mSocket = IO.socket(SERVER_URL, options)

            mSocket?.on(Socket.EVENT_CONNECT) {
                // Không lưu tin nhắn hệ thống vào DB cho đỡ rác
                addMessageToUi("System", "👋 Chào mừng $myName!", null, null, null, false, saveToDb = false)
            }

            mSocket?.on("chat_message") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    val user = data.optString("user")
                    val content = data.optString("content")
                    val image = data.optString("image").takeIf { it.isNotEmpty() }
                    val fileData = data.optString("fileData").takeIf { it.isNotEmpty() }
                    val fileName = data.optString("fileName").takeIf { it.isNotEmpty() }
                    val isMine = (user == myName)

                    // Khi nhận tin nhắn -> Thêm vào list VÀ Lưu vào DB
                    addMessageToUi(user, content, image, fileData, fileName, isMine, saveToDb = true)
                }
            }
            mSocket?.connect()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun sendMessage(content: String) {
        val json = JSONObject()
        json.put("user", myName)
        json.put("content", content)
        mSocket?.emit("chat_message", json)
    }

    fun sendImage(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val base64Image = encodeImageToBase64(context, uri)
            if (base64Image != null) {
                val json = JSONObject()
                json.put("user", myName)
                json.put("content", "Đã gửi một ảnh")
                json.put("image", base64Image)
                mSocket?.emit("chat_message", json)
            }
        }
    }

    fun sendFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val fileName = getFileName(context, uri) ?: "file_unknown"
            val base64File = encodeFileToBase64(context, uri)
            if (base64File != null) {
                val json = JSONObject()
                json.put("user", myName)
                json.put("content", "Đã gửi file: $fileName")
                json.put("fileData", base64File)
                json.put("fileName", fileName)
                mSocket?.emit("chat_message", json)
            }
        }
    }

    // Hàm helper để thêm tin nhắn vào UI và gọi lưu DB
    private fun addMessageToUi(user: String, content: String, image: String?, fileData: String?, fileName: String?, isMine: Boolean, saveToDb: Boolean) {
        viewModelScope.launch {
            val msg = ChatMessage(user, content, image, fileData, fileName, isMine)
            messages.add(msg)

            if (saveToDb) {
                saveMessageToDb(msg)
            }
        }
    }

    // --- CÁC HÀM XỬ LÝ FILE/BASE64 GIỮ NGUYÊN ---
    private fun encodeFileToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null) Base64.encodeToString(bytes, Base64.NO_WRAP) else null
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) result = cursor.getString(index)
                }
            } finally { cursor?.close() }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) result = result?.substring(cut + 1)
        }
        return result
    }

    private fun encodeImageToBase64(context: Context, uri: Uri): String? {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
        return "data:image/jpeg;base64," + Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    override fun onCleared() {
        super.onCleared()
        mSocket?.disconnect()
    }
}