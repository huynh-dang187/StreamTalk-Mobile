package com.example.androidclient

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// 👇 Đảm bảo link Ngrok này là mới nhất nhé!
const val WEB_URL = "https://streamtalk-mobile.onrender.com"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Xin quyền Camera/Mic ngay khi mở App để WebView có thể dùng
        checkPermissions()

        setContent {
            WebViewScreen()
        }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
        if (!permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions, 1)
        }
    }
}

@Composable
fun WebViewScreen() {
    // Biến để giữ tham chiếu tới WebView, dùng cho nút Back
    var webView: WebView? by remember { mutableStateOf(null) }
    val context = LocalContext.current

    // 👇 Xử lý nút Back vật lý trên điện thoại
    BackHandler(enabled = true) {
        if (webView?.canGoBack() == true) {
            webView?.goBack() // Quay lại trang trước
        } else {
            // Thoát ứng dụng nếu không còn trang nào để lùi
            (context as? Activity)?.finish()
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )

                // 👇 CẤU HÌNH BẮT BUỘC ĐỂ WEBRTC CHẠY ĐƯỢC
                settings.apply {
                    javaScriptEnabled = true          // Cho phép JS chạy
                    domStorageEnabled = true          // Lưu trữ cục bộ
                    mediaPlaybackRequiresUserGesture = false // Tự phát Video
                    allowContentAccess = true
                    allowFileAccess = true
                    databaseEnabled = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }

                // 👇 QUAN TRỌNG: Tự động đồng ý khi Web xin quyền Camera/Mic
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest?) {
                        request?.grant(request.resources)
                    }
                }

                // Mở link ngay trong App, không nhảy ra Chrome ngoài
                webViewClient = WebViewClient()

                // Ẩn thanh cuộn cho đẹp
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false

                loadUrl(WEB_URL)

                // Gán tham chiếu để nút Back hoạt động
                webView = this
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}