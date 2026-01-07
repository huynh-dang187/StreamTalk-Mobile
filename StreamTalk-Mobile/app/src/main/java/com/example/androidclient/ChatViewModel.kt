//package com.example.androidclient
//
//import android.app.Application
//import android.content.Context
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import android.net.Uri
//import android.provider.OpenableColumns
//import android.util.Base64
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateListOf
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.setValue
//import androidx.lifecycle.AndroidViewModel
//import androidx.lifecycle.viewModelScope
//import io.socket.client.IO
//import io.socket.client.Socket
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.flow.MutableSharedFlow
//import kotlinx.coroutines.flow.asSharedFlow
//import kotlinx.coroutines.launch
//import org.json.JSONObject
//import java.io.ByteArrayOutputStream
//import java.io.InputStream
//
//// Dữ liệu tin nhắn chat
//data class ChatMessage(
//    val user: String,
//    val content: String,
//    val image: String? = null,
//    val fileData: String? = null,
//    val fileName: String? = null,
//    val isMine: Boolean
//)
//
//// Dữ liệu tín hiệu WebRTC (Answer, Candidate) để đẩy ra UI xử lý
//data class WebRTCSignal(val type: String, val data: JSONObject)
//
//class ChatViewModel(application: Application) : AndroidViewModel(application) {
//
//    // Danh sách tin nhắn hiển thị trên UI
//    val messages = mutableStateListOf<ChatMessage>()
//    var myName = ""
//    private var mSocket: Socket? = null
//
//    // Database để lưu tin nhắn offline
//    private val chatDao = ChatDatabase.getDatabase(application).chatDao()
//
//    // Flow để bắn sự kiện WebRTC (Answer, Candidate) ra cho MainActivity xử lý
//    private val _webRTCEvent = MutableSharedFlow<WebRTCSignal>()
//    val webRTCEvent = _webRTCEvent.asSharedFlow()
//
//    // 👇 TRẠNG THÁI CUỘC GỌI ĐẾN (Để hiện màn hình Đổ chuông - Incoming Call)
//    var incomingCallState by mutableStateOf<JSONObject?>(null)
//
//    // ⚠️ QUAN TRỌNG: Thay link Ngrok của bạn vào đây mỗi khi chạy lại Ngrok
//    private val SERVER_URL = "http://192.168.250.167:3000"
//
//    // Hàm gọi khi người dùng nhập tên và bấm "Vào Chat"
//    fun joinChat(name: String) {
//        myName = name
//        loadHistoryFromDb()
//        connectSocket()
//    }
//
//    // Tải tin nhắn cũ từ Database
//    private fun loadHistoryFromDb() {
//        viewModelScope.launch(Dispatchers.IO) {
//            val history = chatDao.getAllMessages()
//            launch(Dispatchers.Main) {
//                history.forEach { entity ->
//                    messages.add(
//                        ChatMessage(
//                            entity.user,
//                            entity.content,
//                            entity.image,
//                            entity.fileData,
//                            entity.fileName,
//                            entity.user == myName
//                        )
//                    )
//                }
//            }
//        }
//    }
//
//    // Kết nối tới Server Socket.IO
//    private fun connectSocket() {
//        try {
//            android.util.Log.e("SOCKET_DEBUG", "1. Bắt đầu kết nối tới: $SERVER_URL")
//
//            val options = IO.Options().apply {
//                forceNew = true
//                reconnection = true
//                // Header vượt tường lửa Ngrok
//                extraHeaders = mapOf("ngrok-skip-browser-warning" to listOf("true"))
//            }
//            mSocket = IO.socket(SERVER_URL, options)
//
//            // 1. KHI KẾT NỐI THÀNH CÔNG
//            mSocket?.on(Socket.EVENT_CONNECT) {
//                android.util.Log.e("SOCKET_DEBUG", "✅ Đã kết nối thành công với Server! ID: ${mSocket?.id()}")
//            }
//
//            // 2. KHI BỊ LỖI (QUAN TRỌNG NHẤT)
//            mSocket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
//                val error = if (args.isNotEmpty()) args[0].toString() else "Unknown"
//                android.util.Log.e("SOCKET_DEBUG", "❌ LỖI KẾT NỐI: $error")
//            }
//
//            // 1. Lắng nghe tin nhắn chat
//            mSocket?.on("chat_message") { args ->
//                if (args.isNotEmpty()) {
//                    val data = args[0] as JSONObject
//                    val user = data.optString("user")
//                    val content = data.optString("content")
//                    val image = data.optString("image").takeIf { it.isNotEmpty() }
//                    val fileData = data.optString("fileData").takeIf { it.isNotEmpty() }
//                    val fileName = data.optString("fileName").takeIf { it.isNotEmpty() }
//
//                    // Lưu và hiển thị tin nhắn
//                    saveAndShowMessage(ChatMessage(user, content, image, fileData, fileName, user == myName))
//                }
//            }
//
//            // 2. Lắng nghe cuộc gọi đến (Offer) -> Hiện màn hình đổ chuông
//            mSocket?.on("offer") { args ->
//                if (args.isNotEmpty()) {
//                    val data = args[0]
//                    // Chuyển đổi dữ liệu về JSON Object chuẩn
//                    val json = if (data is String) JSONObject(data) else data as JSONObject
//
//                    // Lưu trạng thái để UI hiện popup "Trả lời/Từ chối"
//                    viewModelScope.launch {
//                        incomingCallState = json
//                    }
//                }
//            }
//
//            // 3. Lắng nghe các tín hiệu WebRTC khác (Answer, Candidate) -> Đẩy ra MainActivity xử lý
//            listOf("answer", "candidate").forEach { event ->
//                mSocket?.on(event) { args ->
//                    if (args.isNotEmpty()) {
//                        val data = args[0]
//                        val json = if (data is String) JSONObject(data) else data as JSONObject
//
//                        viewModelScope.launch {
//                            _webRTCEvent.emit(WebRTCSignal(event, json))
//                        }
//                    }
//                }
//            }
//
//            mSocket?.connect()
//        } catch (e: Exception) {
//            android.util.Log.e("SOCKET_DEBUG", "🔥 Exception chết chương trình: ${e.message}")
//            e.printStackTrace()
//        }
//    }
//
//    // Hàm từ chối cuộc gọi
//    fun rejectCall() {
//        incomingCallState = null
//        // (Tùy chọn) Có thể gửi sự kiện 'reject' lên server nếu muốn
//    }
//
//    // Hàm gửi tín hiệu WebRTC (Offer, Answer, Candidate) lên Server
//    fun sendSignal(type: String, dataJsonString: String) {
//        try {
//            val json = JSONObject(dataJsonString)
//            mSocket?.emit(type, json)
//        } catch (e: Exception) {
//            mSocket?.emit(type, dataJsonString)
//        }
//    }
//
//    // Gửi tin nhắn văn bản
//    fun sendMessage(content: String) {
//        val json = JSONObject().apply {
//            put("user", myName)
//            put("content", content)
//        }
//        mSocket?.emit("chat_message", json)
//    }
//
//    // Gửi ảnh
//    fun sendImage(context: Context, uri: Uri) {
//        viewModelScope.launch(Dispatchers.IO) {
//            val base64 = encodeImageToBase64(context, uri)
//            if (base64 != null) {
//                val json = JSONObject().apply {
//                    put("user", myName)
//                    put("content", "Đã gửi ảnh")
//                    put("image", base64)
//                }
//                mSocket?.emit("chat_message", json)
//            }
//        }
//    }
//
//    // Gửi file
//    fun sendFile(context: Context, uri: Uri) {
//        viewModelScope.launch(Dispatchers.IO) {
//            val fName = getFileName(context, uri) ?: "unknown"
//            val base64 = encodeFileToBase64(context, uri)
//            if (base64 != null) {
//                val json = JSONObject().apply {
//                    put("user", myName)
//                    put("content", "Đã gửi file: $fName")
//                    put("fileData", base64)
//                    put("fileName", fName)
//                }
//                mSocket?.emit("chat_message", json)
//            }
//        }
//    }
//
//    // Lưu tin nhắn vào Room DB và cập nhật List UI
//    private fun saveAndShowMessage(msg: ChatMessage) {
//        viewModelScope.launch(Dispatchers.Main) {
//            messages.add(msg)
//            launch(Dispatchers.IO) {
//                chatDao.insertMessage(
//                    MessageEntity(
//                        user = msg.user,
//                        content = msg.content,
//                        image = msg.image,
//                        fileData = msg.fileData,
//                        fileName = msg.fileName,
//                        isMine = msg.isMine
//                    )
//                )
//            }
//        }
//    }
//
//    // Các hàm tiện ích xử lý File/Ảnh
//    private fun encodeFileToBase64(context: Context, uri: Uri): String? {
//        return context.contentResolver.openInputStream(uri)?.use {
//            Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)
//        }
//    }
//
//    private fun getFileName(context: Context, uri: Uri): String? {
//        var result: String? = null
//        if (uri.scheme == "content") {
//            context.contentResolver.query(uri, null, null, null, null)?.use {
//                if (it.moveToFirst()) {
//                    result = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
//                }
//            }
//        }
//        if (result == null) {
//            result = uri.path?.substringAfterLast('/')
//        }
//        return result
//    }
//
//    private fun encodeImageToBase64(context: Context, uri: Uri): String? {
//        return context.contentResolver.openInputStream(uri)?.use {
//            val bm = BitmapFactory.decodeStream(it)
//            val os = ByteArrayOutputStream()
//            bm.compress(Bitmap.CompressFormat.JPEG, 50, os)
//            "data:image/jpeg;base64," + Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP)
//        }
//    }
//
//    override fun onCleared() {
//        super.onCleared()
//        mSocket?.disconnect()
//    }
//}