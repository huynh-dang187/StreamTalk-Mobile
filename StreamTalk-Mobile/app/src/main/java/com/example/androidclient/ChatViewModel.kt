package com.example.androidclient

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

// 1. Cập nhật cấu trúc tin nhắn
data class ChatMessage(
    val user: String,
    val content: String,
    val image: String? = null,
    val fileData: String? = null, // Base64 của file
    val fileName: String? = null, // Tên file
    val isMine: Boolean
)

class ChatViewModel : ViewModel() {
    val messages = mutableStateListOf<ChatMessage>()
    var myName = ""
    private var mSocket: Socket? = null

    // ⚠️ Nhớ kiểm tra lại IP của bạn
    private val SERVER_URL = "http://192.168.148.167:3000"

    fun joinChat(name: String) {
        myName = name
        connectSocket()
    }

    private fun connectSocket() {
        try {
            val options = IO.Options().apply { forceNew = true }
            mSocket = IO.socket(SERVER_URL, options)

            mSocket?.on(Socket.EVENT_CONNECT) {
                addMessage("System", "👋 Chào mừng $myName!", null, null, null, false)
            }

            // 2. Nhận tin nhắn (Cập nhật lấy thêm fileData, fileName)
            mSocket?.on("chat_message") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    val user = data.optString("user")
                    val content = data.optString("content")

                    val image = data.optString("image").takeIf { it.isNotEmpty() }
                    val fileData = data.optString("fileData").takeIf { it.isNotEmpty() }
                    val fileName = data.optString("fileName").takeIf { it.isNotEmpty() }

                    val isMine = (user == myName)
                    addMessage(user, content, image, fileData, fileName, isMine)
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

    // 3. HÀM MỚI: Gửi File
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

    // Hàm mã hóa File -> Base64
    private fun encodeFileToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null) {
                // Không nén như ảnh, giữ nguyên chất lượng
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else null
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    // Hàm lấy tên file
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

    private fun addMessage(user: String, content: String, image: String?, fileData: String?, fileName: String?, isMine: Boolean) {
        viewModelScope.launch {
            messages.add(ChatMessage(user, content, image, fileData, fileName, isMine))
        }
    }

    override fun onCleared() {
        super.onCleared()
        mSocket?.disconnect()
    }
}