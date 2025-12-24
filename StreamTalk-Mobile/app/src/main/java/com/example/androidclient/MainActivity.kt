package com.example.androidclient

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64 // Import thư viện giải mã
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image // Import Image chuẩn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap // Import chuyển đổi Bitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage // Vẫn giữ để dùng cho ImagePreviewDialog (vì load từ Uri)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppNavigation() }
    }
}

// 👇 HÀM MỚI: Tự tay giải mã chuỗi Base64 thành ảnh
fun decodeBase64ToBitmap(base64Str: String): ImageBitmap? {
    return try {
        // Nếu chuỗi có chứa header "data:image/jpeg;base64," thì cắt bỏ nó đi
        val cleanBase64 = if (base64Str.contains(",")) {
            base64Str.substringAfter(",")
        } else {
            base64Str
        }

        // Giải mã từ String -> Byte Array -> Bitmap
        val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

        // Chuyển sang định dạng của Compose
        bitmap?.asImageBitmap()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun AppNavigation(viewModel: ChatViewModel = viewModel()) {
    var isJoined by remember { mutableStateOf(false) }
    if (!isJoined) {
        LoginScreen { name -> viewModel.joinChat(name); isJoined = true }
    } else {
        ChatScreen(viewModel)
    }
}

@Composable
fun LoginScreen(onJoinClick: (String) -> Unit) {
    var nameInput by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("👋 Xin chào!", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6200EE))
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, label = { Text("Tên của bạn") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { if (nameInput.isNotBlank()) onJoinClick(nameInput) }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))) { Text("Vào phòng Chat") }
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    var textInput by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { uri ->
        selectedImageUri = uri
    }

    if (selectedImageUri != null) {
        ImagePreviewDialog(
            uri = selectedImageUri!!,
            onDismiss = { selectedImageUri = null },
            onSend = {
                viewModel.sendImage(context, selectedImageUri!!)
                selectedImageUri = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        Surface(shadowElevation = 4.dp, color = Color.White) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("💬 Phòng Chat", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text("👤 ${viewModel.myName}", fontSize = 14.sp, color = Color(0xFF6200EE))
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(viewModel.messages) { msg -> MessageBubble(msg) }
        }

        Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Default.Add, "Chọn ảnh", tint = Color(0xFF6200EE))
            }
            TextField(value = textInput, onValueChange = { textInput = it }, modifier = Modifier.weight(1f), placeholder = { Text("Nhập tin nhắn...") }, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent))
            Button(onClick = { if (textInput.isNotBlank()) { viewModel.sendMessage(textInput); textInput = "" } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE)), modifier = Modifier.padding(start = 8.dp)) { Text("Gửi") }
        }
    }
}

@Composable
fun ImagePreviewDialog(uri: Uri, onDismiss: () -> Unit, onSend: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Gửi ảnh này?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))
                // Ở đây vẫn dùng AsyncImage vì nó load từ Uri (File) nên không lỗi
                AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxWidth().height(250.dp).background(Color.LightGray, RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = onDismiss) { Text("Hủy") }
                    Button(onClick = onSend, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))) { Text("Gửi ngay") }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessage) {
    val alignment = if (msg.isMine) Alignment.End else Alignment.Start
    val bubbleColor = if (msg.isMine) Color(0xFF6200EE) else Color.White
    val textColor = if (msg.isMine) Color.White else Color.Black
    val cornerShape = if (msg.isMine) RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp) else RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = alignment) {
        if (!msg.isMine) Text(msg.user, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 2.dp, start = 8.dp))

        Surface(color = bubbleColor, shape = cornerShape, shadowElevation = 2.dp, modifier = Modifier.widthIn(max = 280.dp)) {
            Column(modifier = Modifier.padding(8.dp)) {

                // 👇 ĐÃ SỬA: Logic hiển thị ảnh dùng hàm giải mã
                if (msg.image != null && msg.image.isNotEmpty()) {
                    // Dùng remember để không phải giải mã lại liên tục khi cuộn
                    val imageBitmap = remember(msg.image) { decodeBase64ToBitmap(msg.image) }

                    if (imageBitmap != null) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "Gửi ảnh",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 250.dp)
                                .padding(bottom = 4.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Nếu giải mã lỗi thì hiện text báo lỗi (để debug)
                        Text("[Lỗi hiển thị ảnh]", color = Color.Red, fontSize = 12.sp)
                    }
                }

                if (msg.content.isNotEmpty()) {
                    Text(text = msg.content, color = textColor, fontSize = 16.sp)
                }
            }
        }
    }
}