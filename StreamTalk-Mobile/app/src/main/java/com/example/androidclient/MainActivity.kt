package com.example.androidclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppNavigation()
        }
    }
}

// 👇 Bộ điều hướng đơn giản
@Composable
fun AppNavigation(viewModel: ChatViewModel = viewModel()) {
    // Biến trạng thái: Đã tham gia hay chưa?
    var isJoined by remember { mutableStateOf(false) }

    if (!isJoined) {
        // Chưa tham gia -> Hiện màn hình Login
        LoginScreen(onJoinClick = { name ->
            viewModel.joinChat(name) // Gọi hàm set tên và kết nối
            isJoined = true // Chuyển màn hình
        })
    } else {
        // Đã tham gia -> Hiện màn hình Chat
        ChatScreen(viewModel)
    }
}

// 👇 Màn hình Đăng nhập mới toanh
@Composable
fun LoginScreen(onJoinClick: (String) -> Unit) {
    var nameInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "👋 Xin chào!", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6200EE))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Nhập tên để vào phòng chat", color = Color.Gray)

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            label = { Text("Tên của bạn") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { if (nameInput.isNotBlank()) onJoinClick(nameInput) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
        ) {
            Text("Vào phòng Chat", fontSize = 18.sp)
        }
    }
}

// 👇 Màn hình Chat (Giữ nguyên logic cũ, chỉ sửa chút UI)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    var textInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        // Header hiện tên mình
        Surface(shadowElevation = 4.dp, color = Color.White) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "💬 Phòng Chat chung", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "👤 ${viewModel.myName}", fontSize = 14.sp, color = Color(0xFF6200EE))
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(viewModel.messages) { msg -> MessageBubble(msg) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(Color.White).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Nhập tin nhắn...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            Button(
                onClick = {
                    if (textInput.isNotBlank()) {
                        viewModel.sendMessage(textInput)
                        textInput = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE)),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Gửi")
            }
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessage) {
    val alignment = if (msg.isMine) Alignment.End else Alignment.Start
    val bubbleColor = if (msg.isMine) Color(0xFF6200EE) else Color.White
    val textColor = if (msg.isMine) Color.White else Color.Black
    val cornerShape = if (msg.isMine) RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp)
    else RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp)

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        if (!msg.isMine) {
            Text(text = msg.user, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 2.dp, start = 8.dp))
        }
        Surface(
            color = bubbleColor,
            shape = cornerShape,
            shadowElevation = 2.dp,
            modifier = Modifier.widthIn(max = 250.dp)
        ) {
            Text(text = msg.content, color = textColor, modifier = Modifier.padding(12.dp), fontSize = 16.sp)
        }
    }
}