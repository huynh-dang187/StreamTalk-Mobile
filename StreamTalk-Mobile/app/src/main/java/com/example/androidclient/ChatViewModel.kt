package com.example.androidclient

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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

// 1. Cập nhật cấu trúc tin nhắn: Thêm trường 'image' (có thể null)
data class ChatMessage(
    val user: String,
    val content: String, // Nội dung chữ (nếu có)
    val image: String?,  // Nội dung ảnh Base64 (nếu có)
    val isMine: Boolean
)

class ChatViewModel : ViewModel() {
    val messages = mutableStateListOf<ChatMessage>()
    var myName = ""
    private var mSocket: Socket? = null

    // ⚠️ IP CỦA BẠN
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
                addMessage("System", "👋 Chào mừng $myName!", null, false)
            }

            // 2. Nhận tin nhắn (Check cả chữ và ảnh)
            mSocket?.on("chat_message") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    val user = data.optString("user")
                    val content = data.optString("content")
                    val image = data.optString("image") // Lấy chuỗi ảnh (nếu có)

                    // Nếu trường image rỗng thì gán là null
                    val finalImage = if (image.isNotEmpty()) image else null

                    val isMine = (user == myName)
                    addMessage(user, content, finalImage, isMine)
                }
            }
            mSocket?.connect()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun sendMessage(content: String) {
        val json = JSONObject()
        json.put("user", myName)
        json.put("content", content)
        json.put("image", "") // Không có ảnh
        mSocket?.emit("chat_message", json)
    }

    // 3. HÀM MỚI: Gửi ảnh
    fun sendImage(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) { // Chạy ở luồng phụ để không đơ máy
            try {
                // Nén ảnh và chuyển thành Base64
                val base64Image = encodeImageToBase64(context, uri)

                if (base64Image != null) {
                    val json = JSONObject()
                    json.put("user", myName)
                    json.put("content", "Đã gửi một ảnh") // Tin nhắn phụ
                    json.put("image", base64Image) // Chuỗi ảnh dài ngoằng nằm ở đây

                    mSocket?.emit("chat_message", json)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Hàm phụ: Biến Uri -> Base64 String
    private fun encodeImageToBase64(context: Context, uri: Uri): String? {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null

        // Nén ảnh xuống còn 50% chất lượng để gửi cho nhanh
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
        val byteArray = outputStream.toByteArray()

        return "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun addMessage(user: String, content: String, image: String?, isMine: Boolean) {
        viewModelScope.launch {
            messages.add(ChatMessage(user, content, image, isMine))
        }
    }

    override fun onCleared() {
        super.onCleared()
        mSocket?.disconnect()
    }
}