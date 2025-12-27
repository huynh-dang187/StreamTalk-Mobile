package com.example.androidclient

import android.content.Context
// 👇 ĐÂY LÀ PHẦN QUAN TRỌNG BẠN ĐANG THIẾU
import org.webrtc.Camera2Enumerator
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class WebRTCClient(private val context: Context) {

    // Factory tạo ra mọi thứ trong WebRTC
    val rootEglBase: EglBase = EglBase.create()
    private var peerConnectionFactory: PeerConnectionFactory

    // Video capture
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null

    init {
        // 1. Khởi tạo WebRTC
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        // 2. Tạo Factory
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    // Hàm khởi động Camera và hiển thị lên SurfaceViewRenderer
    fun startLocalVideo(surface: SurfaceViewRenderer) {
        // Cấu hình hiển thị Surface
        surface.init(rootEglBase.eglBaseContext, null)
        surface.setMirror(true) // Soi gương
        surface.setEnableHardwareScaler(true)

        // 1. Tạo Video Capturer (Mở Camera trước)
        videoCapturer = createCameraCapturer()

        // 2. Tạo Video Source & Track
        // Độ phân giải HD (1280x720), 30fps
        videoSource = peerConnectionFactory.createVideoSource(false)
        videoCapturer?.initialize(SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext), context, videoSource?.capturerObserver)
        videoCapturer?.startCapture(1280, 720, 30)

        videoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)

        // 3. Gắn Video vào màn hình (Surface)
        videoTrack?.addSink(surface)
    }

    // Hàm tìm Camera trước
    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        // Tìm camera trước
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        // Nếu không có cam trước thì lấy cam sau
        for (deviceName in deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }
}