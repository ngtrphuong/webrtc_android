package com.dds.webrtclib;


import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

import androidx.annotation.Nullable;

import com.dds.webrtclib.bean.MediaType;
import com.dds.webrtclib.bean.MyIceServer;
import com.dds.webrtclib.ws.IWebSocket;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class PeerConnectionHelper {

    public final static String TAG = "dds_webRtcHelper";

    public static final int VIDEO_RESOLUTION_WIDTH = 320;
    public static final int VIDEO_RESOLUTION_HEIGHT = 240;
    public static final int FPS = 10;
    public static final String VIDEO_CODEC_H264 = "H264";
    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final String AUDIO_TRACK_ID = "ARDAMSa0";

    public PeerConnectionFactory _factory;
    public MediaStream _localStream;
    public VideoTrack _localVideoTrack;
    public AudioTrack _localAudioTrack;
    public VideoCapturer captureAndroid;
    public VideoSource videoSource;
    public AudioSource audioSource;

    public ArrayList<String> _connectionIdArray;
    public Map<String, Peer> _connectionPeerDic;

    public String _myId;
    public IViewCallback viewCallback;

    public ArrayList<PeerConnection.IceServer> ICEServers;
    public boolean videoEnable;
    public int _mediaType;

    private AudioManager mAudioManager;



    enum Role {Caller, Receiver,}

    private Role _role;

    private IWebSocket _webSocket;

    private Context _context;

    private EglBase _rootEglBase;

    @Nullable
    private SurfaceTextureHelper surfaceTextureHelper;

    private final ExecutorService executor;

    public PeerConnectionHelper(IWebSocket webSocket, MyIceServer[] iceServers) {
        this._connectionPeerDic = new HashMap<>();
        this._connectionIdArray = new ArrayList<>();
        this.ICEServers = new ArrayList<>();

        _webSocket = webSocket;
        executor = Executors.newSingleThreadExecutor();
        if (iceServers != null) {
            for (MyIceServer myIceServer : iceServers) {
                PeerConnection.IceServer iceServer = PeerConnection.IceServer
                        .builder(myIceServer.uri)
                        .setUsername(myIceServer.username)
                        .setPassword(myIceServer.password)
                        .createIceServer();
                ICEServers.add(iceServer);
            }
        }
    }

    // Set the callback of the interface
    public void setViewCallback(IViewCallback callback) {
        viewCallback = callback;
    }

    // ===================================webSocket callback information=======================================

    public void initContext(Context context, EglBase eglBase) {
        _context = context;
        _rootEglBase = eglBase;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void onJoinToRoom(ArrayList<String> connections, String myId, boolean isVideoEnable, int mediaType) {
        videoEnable = isVideoEnable;
        _mediaType = mediaType;
        executor.execute(() -> {
            _connectionIdArray.addAll(connections);
            _myId = myId;
            if (_factory == null) {
                _factory = createConnectionFactory();
            }
            if (_localStream == null) {
                createLocalStream();
            }

            createPeerConnections();
            addStreams();
            createOffers();
        });

    }

    public void onRemoteJoinToRoom(String socketId) {
        executor.execute(() -> {
            if (_localStream == null) {
                createLocalStream();
            }
            try {
                Peer mPeer = new Peer(socketId);
                mPeer.pc.addStream(_localStream);
                _connectionIdArray.add(socketId);
                _connectionPeerDic.put(socketId, mPeer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

    }

    public void onRemoteIceCandidate(String socketId, IceCandidate iceCandidate) {
        executor.execute(() -> {
            Peer peer = _connectionPeerDic.get(socketId);
            if (peer != null) {
                peer.pc.addIceCandidate(iceCandidate);
            }
        });

    }

    public void onRemoteIceCandidateRemove(String socketId, List<IceCandidate> iceCandidates) {
        // TODO remove
        executor.execute(() -> Log.d(TAG, "send onRemoteIceCandidateRemove"));

    }

    public void onRemoteOutRoom(String socketId) {
        executor.execute(() -> closePeerConnection(socketId));

    }

    public void onReceiveOffer(String socketId, String description) {
        executor.execute(() -> {
            _role = Role.Receiver;
            Peer mPeer = _connectionPeerDic.get(socketId);
            String sessionDescription = description;
            if (videoEnable) {
                sessionDescription = preferCodec(description, VIDEO_CODEC_H264, false);
            }

            SessionDescription sdp = new SessionDescription(SessionDescription.Type.OFFER, sessionDescription);

            if (mPeer != null) {
                mPeer.pc.setRemoteDescription(mPeer, sdp);
            }
        });

    }

    public void onReceiverAnswer(String socketId, String sdp) {
        executor.execute(() -> {
            Peer mPeer = _connectionPeerDic.get(socketId);
            SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
            if (mPeer != null) {
                mPeer.pc.setRemoteDescription(mPeer, sessionDescription);
            }
        });

    }

    private PeerConnectionFactory createConnectionFactory() {
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(_context)
                        .createInitializationOptions());

        final VideoEncoderFactory encoderFactory;
        final VideoDecoderFactory decoderFactory;

        encoderFactory = new DefaultVideoEncoderFactory(
                _rootEglBase.getEglBaseContext(),
                true,
                true);
        decoderFactory = new DefaultVideoDecoderFactory(_rootEglBase.getEglBaseContext());

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();

        return PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(JavaAudioDeviceModule.builder(_context).createAudioDeviceModule())
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
    }

    // Create a local stream
    private void createLocalStream() {
        _localStream = _factory.createLocalMediaStream("ARDAMS");
        // Audio
        audioSource = _factory.createAudioSource(createAudioConstraints());
        _localAudioTrack = _factory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        _localStream.addTrack(_localAudioTrack);

        if (videoEnable) {
            // Create the name of the device that needs to be passed in
            captureAndroid = createVideoCapture();
            // Video
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", _rootEglBase.getEglBaseContext());
            videoSource = _factory.createVideoSource(captureAndroid.isScreencast());
            if (_mediaType == MediaType.TYPE_MEETING) {
                // videoSource.adaptOutputFormat(200, 200, 15);
            }
            captureAndroid.initialize(surfaceTextureHelper, _context, videoSource.getCapturerObserver());
            captureAndroid.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS);
            _localVideoTrack = _factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
            _localStream.addTrack(_localVideoTrack);
        }


        if (viewCallback != null) {
            viewCallback.onSetLocalStream(_localStream, _myId);
        }

    }

    // Create all connections
    private void createPeerConnections() {
        for (Object str : _connectionIdArray) {
            Peer peer = new Peer((String) str);
            _connectionPeerDic.put((String) str, peer);
        }
    }

    // Add flow for all connections
    private void addStreams() {
        Log.v(TAG, "Add flow for all connections");
        for (Map.Entry<String, Peer> entry : _connectionPeerDic.entrySet()) {
            if (_localStream == null) {
                createLocalStream();
            }
            try {
                entry.getValue().pc.addStream(_localStream);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    // Create offer for all connections
    private void createOffers() {
        for (Map.Entry<String, Peer> entry : _connectionPeerDic.entrySet()) {
            _role = Role.Caller;
            Peer mPeer = entry.getValue();
            mPeer.pc.createOffer(mPeer, offerOrAnswerConstraint());
        }

    }

    // Close channel flow
    private void closePeerConnection(String connectionId) {
        Peer mPeer = _connectionPeerDic.get(connectionId);
        if (mPeer != null) {
            mPeer.pc.close();
        }
        _connectionPeerDic.remove(connectionId);
        _connectionIdArray.remove(connectionId);
        if (viewCallback != null) {
            viewCallback.onCloseWithId(connectionId);
        }

    }


    //**************************************Logic control**************************************
    // Adjust the camera front and rear
    public void switchCamera() {
        if (captureAndroid == null) return;
        if (captureAndroid instanceof CameraVideoCapturer) {
            CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) captureAndroid;
            cameraVideoCapturer.switchCamera(null);
        } else {
            Log.d(TAG, "Will not switch camera, video caputurer is not a camera");
        }

    }

    // Mute yourself
    public void toggleMute(boolean enable) {
        if (_localAudioTrack != null) {
            _localAudioTrack.setEnabled(enable);
        }
    }

    public void toggleSpeaker(boolean enable) {
        if (mAudioManager != null) {
            mAudioManager.setSpeakerphoneOn(enable);
        }

    }

    // Exit the room
    public void exitRoom() {
        if (viewCallback != null) {
            viewCallback = null;
        }
        executor.execute(() -> {
            ArrayList myCopy;
            myCopy = (ArrayList) _connectionIdArray.clone();
            for (Object Id : myCopy) {
                closePeerConnection((String) Id);
            }
            if (_connectionIdArray != null) {
                _connectionIdArray.clear();
            }
            if (audioSource != null) {
                audioSource.dispose();
                audioSource = null;
            }

            if (videoSource != null) {
                videoSource.dispose();
                videoSource = null;
            }

            if (captureAndroid != null) {
                try {
                    captureAndroid.stopCapture();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                captureAndroid.dispose();
                captureAndroid = null;
            }

            if (surfaceTextureHelper != null) {
                surfaceTextureHelper.dispose();
                surfaceTextureHelper = null;
            }


            if (_factory != null) {
                _factory.dispose();
                _factory = null;
            }

            if (_webSocket != null) {
                _webSocket.close();
                _webSocket = null;
            }


        });


    }


    private VideoCapturer createVideoCapture() {
        VideoCapturer videoCapturer;
        if (useCamera2()) {
            videoCapturer = createCameraCapture(new Camera2Enumerator(_context));
        } else {
            videoCapturer = createCameraCapture(new Camera1Enumerator(true));
        }
        return videoCapturer;
    }

    private VideoCapturer createCameraCapture(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private boolean useCamera2() {
        return Camera2Enumerator.isSupported(_context);
    }


    //**************************************Various constraints******************************************/
    private static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
    private static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
    private static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";
    private static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";

    private MediaConstraints createAudioConstraints() {
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "true"));
        return audioConstraints;
    }

    private MediaConstraints offerOrAnswerConstraint() {
        MediaConstraints mediaConstraints = new MediaConstraints();
        ArrayList<MediaConstraints.KeyValuePair> keyValuePairs = new ArrayList<>();
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        keyValuePairs.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", String.valueOf(videoEnable)));
        mediaConstraints.mandatory.addAll(keyValuePairs);
        return mediaConstraints;
    }

    //**************************************Inner class ******************************************/
    private class Peer implements SdpObserver, PeerConnection.Observer {
        private PeerConnection pc;
        private String socketId;

        public Peer(String socketId) {
            this.pc = createPeerConnection();
            this.socketId = socketId;

        }


        //****************************PeerConnection.Observer****************************/
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.i(TAG, "onSignalingChange: " + signalingState);
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.i(TAG, "onIceConnectionChange: " + iceConnectionState.toString());
        }

        @Override
        public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
            Log.i(TAG, "onConnectionChange: " + newState.toString());
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {
            Log.i(TAG, "onIceConnectionReceivingChange:" + b);

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            Log.i(TAG, "onIceGatheringChange:" + iceGatheringState.toString());

        }


        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            // Send IceCandidate
            _webSocket.sendIceCandidate(socketId, iceCandidate);
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
            Log.i(TAG, "onIceCandidatesRemoved:");
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            if (viewCallback != null) {
                viewCallback.onAddRemoteStream(mediaStream, socketId);
            }
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            if (viewCallback != null) {
                viewCallback.onCloseWithId(socketId);
            }
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {

        }

        @Override
        public void onRenegotiationNeeded() {

        }

        @Override
        public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {

        }

        @Override
        public void onTrack(RtpTransceiver transceiver) {

        }


        //****************************SdpObserver****************************/

        @Override
        public void onCreateSuccess(SessionDescription origSdp) {
            Log.v(TAG, "SDP Created Successfully       " + origSdp.type);
            //Set up local SDP

            String sdpDescription = origSdp.description;
            if (videoEnable) {
                sdpDescription = preferCodec(sdpDescription, VIDEO_CODEC_H264, false);
            }

            final SessionDescription sdp = new SessionDescription(origSdp.type, sdpDescription);


            pc.setLocalDescription(Peer.this, sdp);
        }

        @Override
        public void onSetSuccess() {
            Log.v(TAG, "SDP connection is successful        " + pc.signalingState().toString());

            if (pc.signalingState() == PeerConnection.SignalingState.HAVE_REMOTE_OFFER) {
                pc.createAnswer(Peer.this, offerOrAnswerConstraint());
            } else if (pc.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                //Determine the connection status to send an offer locally
                if (_role == Role.Receiver) {
                    //Recipient, send Answer
                    _webSocket.sendAnswer(socketId, pc.getLocalDescription().description);

                } else if (_role == Role.Caller) {
                    //Sender, send your own offer
                    _webSocket.sendOffer(socketId, pc.getLocalDescription().description);
                }

            } else if (pc.signalingState() == PeerConnection.SignalingState.STABLE) {
                // Stable Signaling
                if (_role == Role.Receiver) {
                    _webSocket.sendAnswer(socketId, pc.getLocalDescription().description);

                }
            }

        }

        @Override
        public void onCreateFailure(String s) {

        }

        @Override
        public void onSetFailure(String s) {

        }


        //Initialize the RTCPeerConnection connection pipe
        private PeerConnection createPeerConnection() {
            if (_factory == null) {
                _factory = createConnectionFactory();
            }
            // Pipeline connection abstract class implementation method
            PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(ICEServers);
            return _factory.createPeerConnection(rtcConfig, this);
        }

    }


    // ===================================Alternative encoding priority========================================
    private static String preferCodec(String sdpDescription, String codec, boolean isAudio) {
        final String[] lines = sdpDescription.split("\r\n");
        final int mLineIndex = findMediaDescriptionLine(isAudio, lines);
        if (mLineIndex == -1) {
            Log.w(TAG, "No mediaDescription line, so can't prefer " + codec);
            return sdpDescription;
        }
        // A list with all the payload types with name |codec|. The payload types are integers in the
        // range 96-127, but they are stored as strings here.
        final List<String> codecPayloadTypes = new ArrayList<>();
        // a=rtpmap:<payload type> <encoding name>/<clock rate> [/<encoding parameters>]
        final Pattern codecPattern = Pattern.compile("^a=rtpmap:(\\d+) " + codec + "(/\\d+)+[\r]?$");
        for (String line : lines) {
            Matcher codecMatcher = codecPattern.matcher(line);
            if (codecMatcher.matches()) {
                codecPayloadTypes.add(codecMatcher.group(1));
            }
        }
        if (codecPayloadTypes.isEmpty()) {
            Log.w(TAG, "No payload types with name " + codec);
            return sdpDescription;
        }

        final String newMLine = movePayloadTypesToFront(codecPayloadTypes, lines[mLineIndex]);
        if (newMLine == null) {
            return sdpDescription;
        }
        Log.d(TAG, "Change media description from: " + lines[mLineIndex] + " to " + newMLine);
        lines[mLineIndex] = newMLine;
        return joinString(Arrays.asList(lines), "\r\n", true /* delimiterAtEnd */);
    }

    private static int findMediaDescriptionLine(boolean isAudio, String[] sdpLines) {
        final String mediaDescription = isAudio ? "m=audio " : "m=video ";
        for (int i = 0; i < sdpLines.length; ++i) {
            if (sdpLines[i].startsWith(mediaDescription)) {
                return i;
            }
        }
        return -1;
    }

    private static @Nullable
    String movePayloadTypesToFront(
            List<String> preferredPayloadTypes, String mLine) {
        // The format of the media description line should be: m=<media> <port> <proto> <fmt> ...
        final List<String> origLineParts = Arrays.asList(mLine.split(" "));
        if (origLineParts.size() <= 3) {
            Log.e(TAG, "Wrong SDP media description format: " + mLine);
            return null;
        }
        final List<String> header = origLineParts.subList(0, 3);
        final List<String> unpreferredPayloadTypes =
                new ArrayList<>(origLineParts.subList(3, origLineParts.size()));
        unpreferredPayloadTypes.removeAll(preferredPayloadTypes);
        // Reconstruct the line with |preferredPayloadTypes| moved to the beginning of the payload
        // types.
        final List<String> newLineParts = new ArrayList<>();
        newLineParts.addAll(header);
        newLineParts.addAll(preferredPayloadTypes);
        newLineParts.addAll(unpreferredPayloadTypes);
        return joinString(newLineParts, " ", false /* delimiterAtEnd */);
    }

    private static String joinString(
            Iterable<? extends CharSequence> s, String delimiter, boolean delimiterAtEnd) {
        Iterator<? extends CharSequence> iter = s.iterator();
        if (!iter.hasNext()) {
            return "";
        }
        StringBuilder buffer = new StringBuilder(iter.next());
        while (iter.hasNext()) {
            buffer.append(delimiter).append(iter.next());
        }
        if (delimiterAtEnd) {
            buffer.append(delimiter);
        }
        return buffer.toString();
    }

}



