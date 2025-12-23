package com.example.androidclient

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.launch
import org.json.JSONObject // 👈 Thư viện để đóng gói JSON

// 1. Định nghĩa cấu trúc tin nhắn
data class ChatMessage(
    val user: String,
    val content: String,
    val isMine: Boolean // Để biết tin này của mình hay của người khác
)

class ChatViewModel : ViewModel() {
    // List bây giờ chứa ChatMessage chứ không phải String nữa
    val messages = mutableStateListOf<ChatMessage>()

    // Tên người dùng (Tạm thời fix cứng, bài sau sẽ cho nhập)
    private val myName = "User Android"

    private var mSocket: Socket? = null
    // ⚠️ Nhớ check lại IP của bạn nhé
    private val SERVER_URL = "http://192.168.148.167:3000"

    init {
        connectSocket()
    }

    private fun connectSocket() {
        try {
            val options = IO.Options().apply { forceNew = true }
            mSocket = IO.socket(SERVER_URL, options)

            mSocket?.on(Socket.EVENT_CONNECT) {
                // Khi kết nối xong, tự thêm 1 tin báo
                addMessageToList("System", "✅ Đã vào phòng chat", false)
            }

            // 2. Nhận tin nhắn dạng JSON Object
            mSocket?.on("chat_message") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    val user = data.getString("user")
                    val content = data.getString("content")

                    // Logic: Nếu tên người gửi trùng tên mình -> Là tin của mình (isMine = true)
                    val isMine = (user == myName)

                    addMessageToList(user, content, isMine)
                }
            }

            mSocket?.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 3. Gửi tin nhắn dạng JSON Object
    fun sendMessage(content: String) {
        val jsonObject = JSONObject()
        jsonObject.put("user", myName)
        jsonObject.put("content", content)

        mSocket?.emit("chat_message", jsonObject)
    }

    private fun addMessageToList(user: String, content: String, isMine: Boolean) {
        viewModelScope.launch {
            messages.add(ChatMessage(user, content, isMine))
        }
    }
}