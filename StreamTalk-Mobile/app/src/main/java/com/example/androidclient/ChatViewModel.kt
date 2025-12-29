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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

data class ChatMessage(
    val user: String,
    val content: String,
    val image: String? = null,
    val fileData: String? = null,
    val fileName: String? = null,
    val isMine: Boolean
)

// Dùng để báo cho MainActivity biết có tín hiệu Video tới
data class WebRTCSignal(val type: String, val data: JSONObject)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    val messages = mutableStateListOf<ChatMessage>()
    var myName = ""
    private var mSocket: Socket? = null
    private val chatDao = ChatDatabase.getDatabase(application).chatDao()

    // Flow để đẩy sự kiện Video Call ra UI
    private val _webRTCEvent = MutableSharedFlow<WebRTCSignal>()
    val webRTCEvent = _webRTCEvent.asSharedFlow()

    // ⚠️ Đổi IP này thành IP máy tính của bạn
    private val SERVER_URL = "http://192.168.98.167:3000"

    fun joinChat(name: String) {
        myName = name
        loadHistoryFromDb()
        connectSocket()
    }

    private fun loadHistoryFromDb() {
        viewModelScope.launch(Dispatchers.IO) {
            val history = chatDao.getAllMessages()
            launch(Dispatchers.Main) {
                history.forEach { entity ->
                    messages.add(ChatMessage(entity.user, entity.content, entity.image, entity.fileData, entity.fileName, entity.user == myName))
                }
            }
        }
    }

    private fun connectSocket() {
        try {
            val options = IO.Options().apply { forceNew = true }
            mSocket = IO.socket(SERVER_URL, options)

            mSocket?.on(Socket.EVENT_CONNECT) {
                // System message logic...
            }

            mSocket?.on("chat_message") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    val user = data.optString("user")
                    // ... (Logic nhận tin nhắn cũ giữ nguyên)
                    val content = data.optString("content")
                    val image = data.optString("image").takeIf { it.isNotEmpty() }
                    val fileData = data.optString("fileData").takeIf { it.isNotEmpty() }
                    val fileName = data.optString("fileName").takeIf { it.isNotEmpty() }

                    saveAndShowMessage(ChatMessage(user, content, image, fileData, fileName, user == myName))
                }
            }

            // 👇 LẮNG NGHE TÍN HIỆU WEB RTC TỪ SERVER
            // Server cần emit các sự kiện: "offer", "answer", "candidate"
            listOf("offer", "answer", "candidate").forEach { event ->
                mSocket?.on(event) { args ->
                    if (args.isNotEmpty()) {
                        val data = args[0]
                        // Kiểm tra nếu data là String (JSON string) thì parse, nếu là JSONObject thì dùng luôn
                        val jsonObject = if (data is String) JSONObject(data) else data as JSONObject
                        viewModelScope.launch {
                            _webRTCEvent.emit(WebRTCSignal(event, jsonObject))
                        }
                    }
                }
            }

            mSocket?.connect()
        } catch (e: Exception) { e.printStackTrace() }
    }

    // 👇 HÀM GỬI TÍN HIỆU VIDEO LÊN SERVER
    fun sendSignal(type: String, dataJsonString: String) {
        // Parse string thành JSON object để socket gửi đi đẹp hơn
        try {
            val json = JSONObject(dataJsonString)
            mSocket?.emit(type, json)
        } catch (e: Exception) {
            mSocket?.emit(type, dataJsonString)
        }
    }

    fun sendMessage(content: String) {
        val json = JSONObject().apply { put("user", myName); put("content", content) }
        mSocket?.emit("chat_message", json)
    }

    // ... (Giữ nguyên các hàm sendImage, sendFile, encodeBase64, getFileName của bạn)
    // Tôi rút gọn để tiết kiệm chỗ hiển thị, bạn copy code cũ vào đây

    fun sendImage(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val base64 = encodeImageToBase64(context, uri)
            if(base64 != null) {
                val json = JSONObject().apply { put("user", myName); put("content", "Đã gửi ảnh"); put("image", base64) }
                mSocket?.emit("chat_message", json)
            }
        }
    }

    fun sendFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val fName = getFileName(context, uri) ?: "unknown"
            val base64 = encodeFileToBase64(context, uri)
            if(base64 != null) {
                val json = JSONObject().apply { put("user", myName); put("content", "Đã gửi file: $fName"); put("fileData", base64); put("fileName", fName) }
                mSocket?.emit("chat_message", json)
            }
        }
    }

    private fun saveAndShowMessage(msg: ChatMessage) {
        viewModelScope.launch(Dispatchers.Main) {
            messages.add(msg)
            launch(Dispatchers.IO) {
                chatDao.insertMessage(MessageEntity(user = msg.user, content = msg.content, image = msg.image, fileData = msg.fileData, fileName = msg.fileName, isMine = msg.isMine))
            }
        }
    }

    private fun encodeFileToBase64(context: Context, uri: Uri): String? =
        context.contentResolver.openInputStream(uri)?.use { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }

    private fun getFileName(context: Context, uri: Uri): String? {
        // Logic cũ của bạn
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use {
                if (it.moveToFirst()) result = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }
        if (result == null) result = uri.path?.substringAfterLast('/')
        return result
    }

    private fun encodeImageToBase64(context: Context, uri: Uri): String? {
        // Logic cũ của bạn
        return context.contentResolver.openInputStream(uri)?.use {
            val bm = BitmapFactory.decodeStream(it)
            val os = ByteArrayOutputStream()
            bm.compress(Bitmap.CompressFormat.JPEG, 50, os)
            "data:image/jpeg;base64," + Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP)
        }
    }

    override fun onCleared() {
        super.onCleared()
        mSocket?.disconnect()
    }
}