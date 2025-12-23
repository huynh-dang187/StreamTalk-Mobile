package com.example.androidclient // ĐÚNG VỚI PACKAGE CỦA BẠN

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
// Lưu ý: Nếu dòng dưới bị đỏ, hãy bấm Alt+Enter để Import, hoặc xem phần chú ý bên dưới
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Gọi màn hình Chat ra
            ChatScreen()
        }
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    // Biến lưu chữ đang nhập trong ô chat
    var textInput by remember { mutableStateOf("") }

    // Bố cục chính: Cột dọc
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Tiêu đề
        Text(
            text = "StreamTalk Mobile",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.Blue,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Danh sách tin nhắn (Chiếm phần lớn màn hình)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            // reverseLayout = true // Bỏ comment dòng này nếu muốn tin mới nhất nằm dưới cùng (kiểu Messenger)
        ) {
            items(viewModel.messages) { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEEEEEE)),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(12.dp),
                        color = Color.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Ô nhập liệu và Nút Gửi
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Nhập tin nhắn...") }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(onClick = {
                if (textInput.isNotBlank()) {
                    viewModel.sendMessage(textInput) // Gửi đi
                    textInput = "" // Xóa ô nhập
                }
            }) {
                Text("Gửi")
            }
        }
    }
}