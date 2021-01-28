package com.dds.skywebrtc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.dds.skywebrtc.engine.EngineCallback;
import com.dds.skywebrtc.engine.webrtc.WebRTCEngine;
import com.dds.skywebrtc.inter.ISkyEvent;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Session layer
 * Created by dds on 2019/8/19.
 *
 */
public class CallSession implements EngineCallback {
    private static final String TAG = "CallSession";
    private WeakReference<CallSessionCallback> sessionCallback;
    private ExecutorService executor;
    private Handler handler = new Handler(Looper.getMainLooper());
    // session parameters
    private boolean mIsAudioOnly;
    // Room list
    private List<String> mUserIDList;
    // Single chat partner Id/group chat inviter
    public String mTargetId;
    // Room Id
    private String mRoomId;
    // myId
    public String mMyId;
    // Room size
    private int mRoomSize;

    private boolean mIsComing;
    private EnumType.CallState _callState = EnumType.CallState.Idle;
    private long startTime;

    private AVEngine iEngine;
    private ISkyEvent mEvent;

    public CallSession(Context context, String roomId, boolean audioOnly, ISkyEvent event) {
        executor = Executors.newSingleThreadExecutor();
        this.mIsAudioOnly = audioOnly;
        this.mRoomId = roomId;

        this.mEvent = event;
        iEngine = AVEngine.createEngine(new WebRTCEngine(audioOnly, context));
        iEngine.init(this);
    }


    // ----------------------------------------Various controls--------------------------------------------

    // Create room
    public void createHome(String room, int roomSize) {
        executor.execute(() -> {
            if (mEvent != null) {
                mEvent.createRoom(room, roomSize);
            }
        });
    }

    // Join room
    public void joinHome(String roomId) {
        executor.execute(() -> {
            _callState = EnumType.CallState.Connecting;
            if (mEvent != null) {
                mEvent.sendJoin(roomId);
            }
        });

    }

    //Start ringing
    public void shouldStartRing() {
        if (mEvent != null) {
            mEvent.shouldStartRing(true);
        }
    }

    // Turn off the ring
    public void shouldStopRing() {
        Log.d(TAG, "shouldStopRing mEvent != null is " + (mEvent != null));
        if (mEvent != null) {
            mEvent.shouldStopRing();
        }
    }

    // Send ring reply
    public void sendRingBack(String targetId, String room) {
        executor.execute(() -> {
            if (mEvent != null) {
                mEvent.sendRingBack(targetId, room);
            }
        });
    }

    // Send rejection signal
    public void sendRefuse() {
        executor.execute(() -> {
            if (mEvent != null) {
                // Cancel outgoing
                mEvent.sendRefuse(mRoomId, mTargetId, EnumType.RefuseType.Hangup.ordinal());
            }
        });

    }

    // Reject when sending is busy
    void sendBusyRefuse(String room, String targetId) {
        executor.execute(() -> {
            if (mEvent != null) {
                // Cancel outgoing
                mEvent.sendRefuse(room, targetId, EnumType.RefuseType.Busy.ordinal());
            }
        });

    }

    // Send cancel signal
    public void sendCancel() {
        executor.execute(() -> {
            if (mEvent != null) {
                // Cancel outgoing
                List<String> list = new ArrayList<>();
                list.add(mTargetId);
                mEvent.sendCancel(mRoomId, list);
            }
        });

    }

    // Leave the room
    public void leave() {
        executor.execute(() -> {
            if (mEvent != null) {
                mEvent.sendLeave(mRoomId, mMyId);
            }
        });
        // Hangup the call
        release(EnumType.CallEndReason.Hangup);

    }

    // Switch to voice answering
    public void sendTransAudio() {
        executor.execute(() -> {
            if (mEvent != null) {
                // Send to the other side, switch to voice
                mEvent.sendTransAudio(mTargetId);
            }
        });
    }

    // Set mute
    public boolean toggleMuteAudio(boolean enable) {
        return iEngine.muteAudio(enable);
    }

    // Set up speakers
    public boolean toggleSpeaker(boolean enable) {

        return iEngine.toggleSpeaker(enable);
    }

    // Switch to voice call
    public void switchToAudio() {
        mIsAudioOnly = true;
        // Tell remote
        sendTransAudio();
        // Local switch
        if (sessionCallback.get() != null) {
            sessionCallback.get().didChangeMode(true);
        }

    }

    // Adjust the camera front and rear
    public void switchCamera() {
        iEngine.switchCamera();
    }

    // Release resources
    private void release(EnumType.CallEndReason reason) {
        executor.execute(() -> {
            // Release the call
            iEngine.release();
            // Set status to Idle
            _callState = EnumType.CallState.Idle;

            //Interface callback
            if (sessionCallback.get() != null) {
                sessionCallback.get().didCallEndWithReason(reason);
            }
        });
    }

    //------------------------------------receive---------------------------------------------------

    // Successfully joined the room
    public void onJoinHome(String myId, String users, int roomSize) {
        // start the timer
        mRoomSize = roomSize;
        startTime = 0;
        handler.post(() -> executor.execute(() -> {
            mMyId = myId;
            List<String> strings;
            if (!TextUtils.isEmpty(users)) {
                String[] split = users.split(",");
                strings = Arrays.asList(split);
                mUserIDList = strings;
            }

            // send invitation
            if (!mIsComing) {
                if (roomSize == 2) {
                    List<String> inviteList = new ArrayList<>();
                    inviteList.add(mTargetId);
                    mEvent.sendInvite(mRoomId, inviteList, mIsAudioOnly);
                }
            } else {
                iEngine.joinRoom(mUserIDList);
            }

            if (!isAudioOnly()) {
                // Screen preview
                if (sessionCallback.get() != null) {
                    sessionCallback.get().didCreateLocalVideoTrack();
                }

            }


        }));


    }

    // New members enter
    public void newPeer(String userId) {
        handler.post(() -> executor.execute(() -> {
            // Other people join the room
            iEngine.userIn(userId);

            // Turn off the ring
            if (mEvent != null) {
                mEvent.shouldStopRing();
            }
            // Change interface
            _callState = EnumType.CallState.Connected;
            if (sessionCallback.get() != null) {
                startTime = System.currentTimeMillis();
                sessionCallback.get().didChangeState(_callState);

            }
        }));

    }

    // The other party has declined
    public void onRefuse(String userId, int type) {
        iEngine.userReject(userId, type);
    }

    // The other party has ringed
    public void onRingBack(String userId) {
        if (mEvent != null) {
            mEvent.shouldStartRing(false);
        }
    }

    // Switch to voice
    public void onTransAudio(String userId) {
        mIsAudioOnly = true;
        // Local switch
        if (sessionCallback.get() != null) {
            sessionCallback.get().didChangeMode(true);
        }
    }

    // The other party's network is disconnected
    public void onDisConnect(String userId) {
        executor.execute(() -> {
            iEngine.disconnected(userId);
        });
    }

    // The other party cancels the call
    public void onCancel(String userId) {
        Log.d(TAG, "onCancel userId = " + userId);
        shouldStopRing();
        release(EnumType.CallEndReason.RemoteHangup);
    }

    public void onReceiveOffer(String userId, String description) {
        executor.execute(() -> {
            iEngine.receiveOffer(userId, description);
        });

    }

    public void onReceiverAnswer(String userId, String sdp) {
        executor.execute(() -> {
            iEngine.receiveAnswer(userId, sdp);
        });

    }

    public void onRemoteIceCandidate(String userId, String id, int label, String candidate) {
        executor.execute(() -> {
            iEngine.receiveIceCandidate(userId, id, label, candidate);
        });

    }

    // The other party leaves the room
    public void onLeave(String userId) {
        if (mRoomSize > 2) {
            // Return to the interface
            if (sessionCallback.get() != null) {
                sessionCallback.get().didUserLeave(userId);
            }
        }
        executor.execute(() -> iEngine.leaveRoom(userId));


    }


    // --------------------------------Interface display related--------------------------------------------/

    public long getStartTime() {
        return startTime;
    }

    public View setupLocalVideo(boolean isOverlay) {
        return iEngine.startPreview(isOverlay);
    }


    public View setupRemoteVideo(String userId, boolean isOverlay) {
        return iEngine.setupRemoteVideo(userId, isOverlay);
    }


    //------------------------------------Various parameters----------------------------------------------/

    public void setIsAudioOnly(boolean _isAudioOnly) {
        this.mIsAudioOnly = _isAudioOnly;
    }

    public boolean isAudioOnly() {
        return mIsAudioOnly;
    }

    public void setTargetId(String targetIds) {
        this.mTargetId = targetIds;
    }

    public void setIsComing(boolean isComing) {
        this.mIsComing = isComing;
    }

    public boolean isComing() {
        return mIsComing;
    }

    public void setRoom(String _room) {
        this.mRoomId = _room;
    }

    public String getRoomId() {
        return mRoomId;
    }

    public EnumType.CallState getState() {
        return _callState;
    }

    public void setCallState(EnumType.CallState callState) {
        this._callState = callState;
    }

    public void setSessionCallback(CallSessionCallback sessionCallback) {
        this.sessionCallback = new WeakReference<>(sessionCallback);
    }

    //-----------------------------Engine callback-----------------------------------------

    @Override
    public void joinRoomSucc() {
        // Turn off the ring
        if (mEvent != null) {
            mEvent.shouldStopRing();
        }
        // Change interface
        _callState = EnumType.CallState.Connected;
        if (sessionCallback.get() != null) {
            startTime = System.currentTimeMillis();
            sessionCallback.get().didChangeState(_callState);

        }
    }

    @Override
    public void exitRoom() {
        // Set status to Idle
        if (mRoomSize == 2) {
            handler.post(() -> {
                release(EnumType.CallEndReason.Hangup);
            });
        }


    }

    @Override
    public void reject(int type) {
        shouldStopRing();
        handler.post(() -> {
            switch (type) {
                case 0:
                    release(EnumType.CallEndReason.Busy);
                    break;
                case 1:
                    release(EnumType.CallEndReason.RemoteHangup);
                    break;

            }
            ;
        });
    }

    @Override
    public void disconnected() {
        handler.post(() -> {
            release(EnumType.CallEndReason.SignalError);
        });
    }

    @Override
    public void onSendIceCandidate(String userId, IceCandidate candidate) {
        executor.execute(() -> {
            if (mEvent != null) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Log.d("dds_test", "onSendIceCandidate");
                mEvent.sendIceCandidate(userId, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp);
            }
        });

    }

    @Override
    public void onSendOffer(String userId, SessionDescription description) {
        executor.execute(() -> {
            if (mEvent != null) {
                Log.d("dds_test", "onSendOffer");
                mEvent.sendOffer(userId, description.description);
            }
        });

    }

    @Override
    public void onSendAnswer(String userId, SessionDescription description) {
        executor.execute(() -> {
            if (mEvent != null) {
                Log.d("dds_test", "onSendAnswer");
                mEvent.sendAnswer(userId, description.description);
            }
        });

    }

    @Override
    public void onRemoteStream(String userId) {
        // 画面预览
        if (sessionCallback.get() != null) {
            sessionCallback.get().didReceiveRemoteVideoTrack(userId);
        }
    }

    public interface CallSessionCallback {
        void didCallEndWithReason(EnumType.CallEndReason var1);

        void didChangeState(EnumType.CallState var1);

        void didChangeMode(boolean isAudioOnly);

        void didCreateLocalVideoTrack();

        void didReceiveRemoteVideoTrack(String userId);

        void didUserLeave(String userId);

        void didError(String error);

    }


}
