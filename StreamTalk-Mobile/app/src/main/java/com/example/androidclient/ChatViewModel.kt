package com.example.androidclient

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URISyntaxException

// Dữ liệu tin nhắn
data class ChatMessage(
    val user: String,
    val content: String,
    val isMine: Boolean
)

class ChatViewModel : ViewModel() {
    val messages = mutableStateListOf<ChatMessage>()

    // 👇 1. SỬA Ở ĐÂY: Không fix cứng tên nữa, để rỗng ban đầu
    var myName = ""

    private var mSocket: Socket? = null
    // ⚠️ Check lại IP lần cuối nhé
    private val SERVER_URL = "http://192.168.148.167:3000"

    // 👇 2. XÓA khối init { connectSocket() } cũ đi
    // Chúng ta sẽ không kết nối ngay khi mở App nữa

    // 👇 3. THÊM HÀM MỚI: Chỉ kết nối khi người dùng bấm nút "Join"
    fun joinChat(name: String) {
        myName = name // Lưu tên người dùng nhập vào
        connectSocket() // Bắt đầu kết nối
    }

    private fun connectSocket() {
        try {
            val options = IO.Options().apply { forceNew = true }
            mSocket = IO.socket(SERVER_URL, options)

            mSocket?.on(Socket.EVENT_CONNECT) {
                // Gửi tin nhắn báo danh (Optional)
                addMessageToList("System", "👋 Chào mừng $myName tham gia!", false)
            }

            mSocket?.on("chat_message") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    val user = data.getString("user")
                    val content = data.getString("content")

                    // So sánh tên người gửi với tên mình
                    val isMine = (user == myName)
                    addMessageToList(user, content, isMine)
                }
            }

            mSocket?.on(Socket.EVENT_CONNECT_ERROR) {
                addMessageToList("System", "❌ Lỗi kết nối", false)
            }

            mSocket?.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendMessage(content: String) {
        val jsonObject = JSONObject()
        jsonObject.put("user", myName) // Gửi kèm tên thật
        jsonObject.put("content", content)
        mSocket?.emit("chat_message", jsonObject)
    }

    private fun addMessageToList(user: String, content: String, isMine: Boolean) {
        viewModelScope.launch {
            messages.add(ChatMessage(user, content, isMine))
        }
    }

    override fun onCleared() {
        super.onCleared()
        mSocket?.disconnect()
    }
}