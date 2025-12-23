package com.example.androidclient

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.net.URISyntaxException

// 👇 QUAN TRỌNG: Chỉ giữ 2 dòng import này của Socket.IO
// Tuyệt đối KHÔNG import java.net.Socket hay kotlinx.coroutines.Dispatchers.IO
import io.socket.client.IO
import io.socket.client.Socket


class ChatViewModel : ViewModel() {
    // List tin nhắn
    val messages = mutableStateListOf<String>()

    private var mSocket: Socket? = null

    // ⚠️ Đổi IP này thành IP máy tính của bạn
    private val SERVER_URL = "http://192.168.148.167:3000"

    init {
        connectSocket()
    }

    private fun connectSocket() {
        try {
            // Cấu hình Socket
            val options = IO.Options().apply {
                forceNew = true
            }

            // Khởi tạo socket
            mSocket = IO.socket(SERVER_URL, options)

            // 1. Lắng nghe sự kiện kết nối thành công
            mSocket?.on(Socket.EVENT_CONNECT) {
                Log.d("SocketIO", "Đã kết nối")
                addMessage("✅ Đã kết nối tới Server!")
            }

            // 2. Lắng nghe tin nhắn từ Server
            mSocket?.on("chat_message") { args ->
                if (args.isNotEmpty()) {
                    val msg = args[0].toString()
                    addMessage(msg)
                }
            }

            // 3. Lắng nghe lỗi kết nối
            mSocket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                val err = if (args.isNotEmpty()) args[0].toString() else "Lỗi không xác định"
                Log.e("SocketIO", "Lỗi: $err")
                addMessage("❌ Lỗi kết nối: $err")
            }

            // Bắt đầu kết nối
            mSocket?.connect()

        } catch (e: URISyntaxException) {
            e.printStackTrace()
            addMessage("❌ Lỗi URI: ${e.message}")
        } catch (e: Exception) {
            e.printStackTrace()
            addMessage("❌ Lỗi Code: ${e.message}")
        }
    }

    // Gửi tin nhắn
    fun sendMessage(msg: String) {
        mSocket?.emit("chat_message", msg)
    }

    // Helper cập nhật UI
    private fun addMessage(msg: String) {
        viewModelScope.launch {
            messages.add(msg)
        }
    }

    override fun onCleared() {
        super.onCleared()
        mSocket?.disconnect()
        mSocket?.off()
    }
}