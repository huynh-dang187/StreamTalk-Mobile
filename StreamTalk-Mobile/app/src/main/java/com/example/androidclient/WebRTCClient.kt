package com.example.androidclient

import android.content.Context
import org.json.JSONObject
import org.webrtc.*

class WebRTCClient(
    private val context: Context,
    // Callback này sẽ gọi viewModel.sendSignal
    private val onSendParams: (String, String) -> Unit
) {
    private val rootEglBase: EglBase = EglBase.create()
    private var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null

    private var videoCapturer: VideoCapturer? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun initSurfaceView(view: SurfaceViewRenderer, isMirror: Boolean) {
        view.init(rootEglBase.eglBaseContext, null)
        view.setMirror(isMirror)
        view.setEnableHardwareScaler(true)
    }

    fun startLocalVideo(surface: SurfaceViewRenderer) {
        initSurfaceView(surface, true)

        videoCapturer = createCameraCapturer()
        // Nếu không tìm thấy camera thì return luôn để tránh crash
        if (videoCapturer == null) return

        val videoSource = peerConnectionFactory.createVideoSource(videoCapturer!!.isScreencast)

        videoCapturer?.initialize(SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext), context, videoSource.capturerObserver)
        videoCapturer?.startCapture(1280, 720, 30)

        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)
        localVideoTrack?.addSink(surface)

        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)
    }

    fun createPeerConnection(remoteSurface: SurfaceViewRenderer) {
        initSurfaceView(remoteSurface, false)

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    val json = JSONObject().apply {
                        put("sdpMid", candidate.sdpMid)
                        put("sdpMLineIndex", candidate.sdpMLineIndex)
                        put("candidate", candidate.sdp)
                    }
                    onSendParams("candidate", json.toString())
                }
            }

            override fun onAddStream(stream: MediaStream?) {
                // Nhận Video của đối phương
                stream?.videoTracks?.get(0)?.addSink(remoteSurface)
            }

            // 👇 ĐÃ SỬA: Thêm hàm này vào để hết lỗi abstract member
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                // Không cần xử lý gì đặc biệt ở đây
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {}
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {}
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dc: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
        })

        if (localVideoTrack != null && localAudioTrack != null) {
            peerConnection?.addTrack(localVideoTrack, listOf("stream_id"))
            peerConnection?.addTrack(localAudioTrack, listOf("stream_id"))
        }
    }

    fun call() {
        peerConnection?.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection?.setLocalDescription(SdpObserverAdapter(), desc)
                if (desc != null) {
                    val json = JSONObject().apply {
                        put("type", desc.type.canonicalForm())
                        put("sdp", desc.description)
                    }
                    onSendParams("offer", json.toString())
                }
            }
        }, MediaConstraints())
    }

    fun onRemoteSessionReceived(json: JSONObject) {
        val type = json.optString("type")
        val description = json.optString("sdp")

        // Nếu là candidate
        if (json.has("candidate")) {
            val candidate = IceCandidate(json.optString("sdpMid"), json.optInt("sdpMLineIndex"), json.optString("candidate"))
            peerConnection?.addIceCandidate(candidate)
            return
        }

        // Nếu là Offer/Answer
        val sessionDescription = SessionDescription(SessionDescription.Type.fromCanonicalForm(type.lowercase()), description)
        peerConnection?.setRemoteDescription(SdpObserverAdapter(), sessionDescription)

        if (type.equals("offer", ignoreCase = true)) {
            peerConnection?.createAnswer(object : SdpObserverAdapter() {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    peerConnection?.setLocalDescription(SdpObserverAdapter(), desc)
                    if (desc != null) {
                        val answerJson = JSONObject().apply {
                            put("type", desc.type.canonicalForm())
                            put("sdp", desc.description)
                        }
                        onSendParams("answer", answerJson.toString())
                    }
                }
            }, MediaConstraints())
        }
    }

    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        // Ưu tiên camera trước
        enumerator.deviceNames.forEach {
            if (enumerator.isFrontFacing(it)) return enumerator.createCapturer(it, null)
        }
        enumerator.deviceNames.forEach {
            if (enumerator.isBackFacing(it)) return enumerator.createCapturer(it, null)
        }
        return null
    }

    fun close() {
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            peerConnection?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}