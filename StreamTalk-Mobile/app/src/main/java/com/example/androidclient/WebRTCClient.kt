//package com.example.androidclient
//
//import android.content.Context
//import android.util.Log
//import org.json.JSONObject
//import org.webrtc.*
//import java.util.regex.Pattern
//
//class WebRTCClient(
//    private val context: Context,
//    private val onSendParams: (String, String) -> Unit
//) {
//    private val TAG = "SOCKET_DEBUG"
//    private val rootEglBase: EglBase = EglBase.create()
//    private var peerConnectionFactory: PeerConnectionFactory
//    private var peerConnection: PeerConnection? = null
//    private var localVideoTrack: VideoTrack? = null
//    private var localAudioTrack: AudioTrack? = null
//    private var videoCapturer: VideoCapturer? = null
//    private val pendingIceCandidates = mutableListOf<IceCandidate>()
//
//    private val iceServers = listOf(
//        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
//        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
//        // OpenRelay TURN (Cứu cánh)
//        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80").setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
//        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443").setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
//        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp").setUsername("openrelayproject").setPassword("openrelayproject").createIceServer()
//    )
//
//    init {
//        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).setEnableInternalTracer(true).createInitializationOptions())
//
//        // Quay về Default Factory (Ổn định nhất)
//        peerConnectionFactory = PeerConnectionFactory.builder()
//            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
//            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
//            .createPeerConnectionFactory()
//    }
//
//    // --- CÁC HÀM KHỞI TẠO MEDIA ---
//    fun startLocalVideo(surface: SurfaceViewRenderer) {
//        surface.init(rootEglBase.eglBaseContext, null)
//        surface.setMirror(true)
//        surface.setEnableHardwareScaler(true)
//        surface.setZOrderMediaOverlay(true)
//
//        videoCapturer = createCameraCapturer()
//        val videoSource = peerConnectionFactory.createVideoSource(videoCapturer!!.isScreencast)
//        videoCapturer?.initialize(SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext), context, videoSource.capturerObserver)
//        videoCapturer?.startCapture(640, 480, 30) // VGA cho nhẹ
//
//        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)
//        localVideoTrack?.addSink(surface)
//    }
//
//    fun startLocalAudio() {
//        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
//        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)
//    }
//
//    fun createPeerConnection(remoteSurface: SurfaceViewRenderer) {
//        remoteSurface.init(rootEglBase.eglBaseContext, null)
//        remoteSurface.setEnableHardwareScaler(true)
//
//        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
//            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
//            iceTransportsType = PeerConnection.IceTransportsType.ALL
//        }
//
//        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
//            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
//                Log.e(TAG, "❄️ TRẠNG THÁI ICE: $newState")
//                if (newState == PeerConnection.IceConnectionState.CONNECTED) Log.e(TAG, "🎉 KẾT NỐI P2P THÀNH CÔNG!")
//            }
//
//            override fun onIceCandidate(candidate: IceCandidate?) {
//                if (candidate != null) {
//                    val json = JSONObject().apply {
//                        put("sdpMid", candidate.sdpMid); put("sdpMLineIndex", candidate.sdpMLineIndex); put("candidate", candidate.sdp)
//                    }
//                    onSendParams("candidate", json.toString())
//                }
//            }
//
//            override fun onTrack(transceiver: RtpTransceiver?) {
//                val track = transceiver?.receiver?.track()
//                if (track is VideoTrack) {
//                    Log.e(TAG, "📺 Tìm thấy Video Track -> Gắn vào màn hình!")
//                    track.addSink(remoteSurface)
//                }
//            }
//
//            override fun onAddStream(stream: MediaStream?) {}
//            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
//            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {}
//            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
//            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
//            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
//            override fun onRemoveStream(stream: MediaStream?) {}
//            override fun onDataChannel(dc: DataChannel?) {}
//            override fun onRenegotiationNeeded() {}
//            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
//        })
//
//        if (localVideoTrack != null) peerConnection?.addTrack(localVideoTrack, listOf("stream_id"))
//        if (localAudioTrack != null) peerConnection?.addTrack(localAudioTrack, listOf("stream_id"))
//    }
//
//    // --- CALL FLOW (Đã thêm logic Ép Codec VP8) ---
//    fun call() {
//        val constraints = MediaConstraints().apply {
//            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
//            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
//        }
//        peerConnection?.createOffer(object : SdpObserverAdapter() {
//            override fun onCreateSuccess(desc: SessionDescription?) {
//                if (desc != null) {
//                    // 👇 THUỐC ĐẶC TRỊ: ÉP DÙNG VP8
//                    val newDesc = SessionDescription(desc.type, forceVP8(desc.description))
//
//                    peerConnection?.setLocalDescription(object : SdpObserverAdapter() {}, newDesc)
//                    val json = JSONObject().apply {
//                        put("type", newDesc.type.canonicalForm())
//                        put("sdp", newDesc.description)
//                    }
//                    onSendParams("offer", json.toString())
//                }
//            }
//        }, constraints)
//    }
//
//    fun onRemoteSessionReceived(json: JSONObject) {
//        val type = json.optString("type")
//        if (json.has("candidate")) {
//            val candidate = IceCandidate(json.optString("sdpMid"), json.optInt("sdpMLineIndex"), json.optString("candidate"))
//            if (peerConnection?.remoteDescription == null) pendingIceCandidates.add(candidate)
//            else peerConnection?.addIceCandidate(candidate)
//            return
//        }
//
//        // 👇 THUỐC ĐẶC TRỊ: ÉP DÙNG VP8 CHO REMOTE DESCRIPTION
//        val sdp = forceVP8(json.optString("sdp"))
//        val sessionDescription = SessionDescription(SessionDescription.Type.fromCanonicalForm(type.lowercase()), sdp)
//
//        peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {
//            override fun onSetSuccess() {
//                pendingIceCandidates.forEach { peerConnection?.addIceCandidate(it) }
//                pendingIceCandidates.clear()
//            }
//        }, sessionDescription)
//
//        if (type.equals("offer", ignoreCase = true)) {
//            val constraints = MediaConstraints().apply {
//                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
//                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
//            }
//            peerConnection?.createAnswer(object : SdpObserverAdapter() {
//                override fun onCreateSuccess(desc: SessionDescription?) {
//                    if (desc != null) {
//                        // 👇 THUỐC ĐẶC TRỊ: ÉP DÙNG VP8 CHO ANSWER
//                        val newDesc = SessionDescription(desc.type, forceVP8(desc.description))
//
//                        peerConnection?.setLocalDescription(object : SdpObserverAdapter() {}, newDesc)
//                        val answerJson = JSONObject().apply {
//                            put("type", newDesc.type.canonicalForm())
//                            put("sdp", newDesc.description)
//                        }
//                        onSendParams("answer", answerJson.toString())
//                    }
//                }
//            }, constraints)
//        }
//    }
//
//    // --- HÀM MAGIC: ÉP BUỘC DÙNG VP8 ---
//    private fun forceVP8(sdp: String): String {
//        return try {
//            var newSdp = sdp
//            val lines = sdp.split("\r\n".toRegex()).toTypedArray()
//            var mLineIndex = -1
//            var map = HashMap<String, String>()
//
//            // Tìm m=video line
//            for (i in lines.indices) {
//                if (lines[i].startsWith("m=video")) {
//                    mLineIndex = i
//                    continue
//                }
//                // Tìm các codec map (a=rtpmap:96 VP8/90000)
//                if (lines[i].startsWith("a=rtpmap:")) {
//                    val parts = lines[i].split(" ".toRegex()).toTypedArray()
//                    val payloadType = parts[0].split(":".toRegex()).toTypedArray()[1]
//                    val codecName = parts[1].split("/".toRegex()).toTypedArray()[0]
//                    map[codecName] = payloadType
//                }
//            }
//
//            if (mLineIndex != -1 && map.containsKey("VP8")) {
//                val vp8Payload = map["VP8"]
//                val mLineParts = lines[mLineIndex].split(" ".toRegex()).toTypedArray()
//
//                // Đưa VP8 lên đầu danh sách ưu tiên
//                val newMLine = StringBuilder()
//                newMLine.append(mLineParts[0]).append(" ").append(mLineParts[1]).append(" ").append(mLineParts[2]).append(" ").append(vp8Payload)
//
//                for (i in 3 until mLineParts.size) {
//                    if (mLineParts[i] != vp8Payload) {
//                        newMLine.append(" ").append(mLineParts[i])
//                    }
//                }
//                lines[mLineIndex] = newMLine.toString()
//                newSdp = lines.joinToString("\r\n")
//                Log.e(TAG, "✅ Đã ép buộc dùng Codec VP8 thành công!")
//            }
//            newSdp
//        } catch (e: Exception) {
//            Log.e(TAG, "Lỗi khi ép VP8: ${e.message}")
//            sdp
//        }
//    }
//
//    fun close() {
//        try {
//            videoCapturer?.stopCapture(); videoCapturer?.dispose(); peerConnection?.close(); rootEglBase.release()
//        } catch (e: Exception) {}
//    }
//
//    private fun createCameraCapturer(): VideoCapturer? {
//        val enumerator = Camera2Enumerator(context)
//        enumerator.deviceNames.forEach { if (enumerator.isFrontFacing(it)) return enumerator.createCapturer(it, null) }
//        enumerator.deviceNames.forEach { if (enumerator.isBackFacing(it)) return enumerator.createCapturer(it, null) }
//        return null
//    }
//}
//
//open class SdpObserverAdapter : SdpObserver {
//    override fun onCreateSuccess(p0: SessionDescription?) {}
//    override fun onSetSuccess() {}
//    override fun onCreateFailure(p0: String?) { Log.e("SOCKET_DEBUG", "SDP Lỗi: $p0") }
//    override fun onSetFailure(p0: String?) { Log.e("SOCKET_DEBUG", "SDP Lỗi: $p0") }
//}