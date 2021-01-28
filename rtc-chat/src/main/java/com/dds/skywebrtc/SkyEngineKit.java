package com.dds.skywebrtc;

import android.content.Context;
import android.util.Log;

import com.dds.skywebrtc.except.NotInitializedException;
import com.dds.skywebrtc.inter.ISkyEvent;

/**
 * Main control class
 * Created by dds on 2019/8/19.
 */
public class SkyEngineKit {
    private final static String TAG = "dds_AVEngineKit";
    private static SkyEngineKit avEngineKit;
    private CallSession mCurrentCallSession;
    private ISkyEvent mEvent;


    public static SkyEngineKit Instance() {
        SkyEngineKit var;
        if ((var = avEngineKit) != null) {
            return var;
        } else {
            throw new NotInitializedException();
        }
    }

    // Initialization
    public static void init(ISkyEvent iSocketEvent) {
        if (avEngineKit == null) {
            avEngineKit = new SkyEngineKit();
            avEngineKit.mEvent = iSocketEvent;
        }
    }


    public void sendRefuseOnPermissionDenied(String room, String inviteId) {
        // Uninitialized
        if (avEngineKit == null) {
            Log.e(TAG, "startOutCall error,please init first");
            return;
        }
        if (mCurrentCallSession != null) {
            endCall();
        } else {
            avEngineKit.mEvent.sendRefuse(room, inviteId, EnumType.RefuseType.Hangup.ordinal());
        }
    }

    public void sendDisconnected(String room, String toId) {
        // Uninitialized
        if (avEngineKit == null) {
            Log.e(TAG, "startOutCall error,please init first");
            return;
        }
        avEngineKit.mEvent.sendDisConnect(room, toId);
    }

    // dial number
    public boolean startOutCall(Context context, final String room, final String targetId,
                                final boolean audioOnly) {
        // Uninitialized
        if (avEngineKit == null) {
            Log.e(TAG, "startOutCall error,please init first");
            return false;
        }
        // Busy
        if (mCurrentCallSession != null && mCurrentCallSession.getState() != EnumType.CallState.Idle) {
            Log.i(TAG, "startCall error,currentCallSession is exist");
            return false;
        }
        // Initial session
        mCurrentCallSession = new CallSession(context, room, audioOnly, mEvent);
        mCurrentCallSession.setTargetId(targetId);
        mCurrentCallSession.setIsComing(false);
        mCurrentCallSession.setCallState(EnumType.CallState.Outgoing);
        // Create room
        mCurrentCallSession.createHome(room, 2);


        return true;
    }

    // answer the phone
    public boolean startInCall(Context context, final String room, final String targetId,
                               final boolean audioOnly) {
        if (avEngineKit == null) {
            Log.e(TAG, "startInCall error,init is not set");
            return false;
        }
        // Busy
        if (mCurrentCallSession != null && mCurrentCallSession.getState() != EnumType.CallState.Idle) {
            // Sending->Busy...
            Log.i(TAG, "startInCall busy,currentCallSession is exist,start sendBusyRefuse!");
            mCurrentCallSession.sendBusyRefuse(room, targetId);
            return false;
        }
        // Initial session
        mCurrentCallSession = new CallSession(context, room, audioOnly, mEvent);
        mCurrentCallSession.setTargetId(targetId);
        mCurrentCallSession.setIsComing(true);
        mCurrentCallSession.setCallState(EnumType.CallState.Incoming);

        // Start ringing and reply
        mCurrentCallSession.shouldStartRing();
        mCurrentCallSession.sendRingBack(targetId, room);


        return true;
    }

    // Hang up the session
    public void endCall() {
        Log.d(TAG, "endCall mCurrentCallSession != null is " + (mCurrentCallSession != null));
        if (mCurrentCallSession != null) {
            // Stop ringing
            mCurrentCallSession.shouldStopRing();

            if (mCurrentCallSession.isComing()) {
                if (mCurrentCallSession.getState() == EnumType.CallState.Incoming) {
                    // Received the invitation, but did not agree, send rejection
                    mCurrentCallSession.sendRefuse();
                } else {
                    // Already connected, hang up
                    mCurrentCallSession.leave();
                }
            } else {
                if (mCurrentCallSession.getState() == EnumType.CallState.Outgoing) {
                    mCurrentCallSession.sendCancel();
                } else {
                    // Already connected, hang up
                    mCurrentCallSession.leave();
                }
            }
            mCurrentCallSession.setCallState(EnumType.CallState.Idle);
        }

    }

    // Join room
    public void joinRoom(Context context, String room) {
        if (avEngineKit == null) {
            Log.e(TAG, "joinRoom error,init is not set");
            return;
        }
        // Busy
        if (mCurrentCallSession != null && mCurrentCallSession.getState() != EnumType.CallState.Idle) {
            Log.e(TAG, "joinRoom error,currentCallSession is exist");
            return;
        }
        mCurrentCallSession = new CallSession(context, room, false, mEvent);
        mCurrentCallSession.setIsComing(true);
        mCurrentCallSession.joinHome(room);
    }

    public void createAndJoinRoom(Context context, String room) {
        if (avEngineKit == null) {
            Log.e(TAG, "joinRoom error,init is not set");
            return;
        }
        // Busy
        if (mCurrentCallSession != null && mCurrentCallSession.getState() != EnumType.CallState.Idle) {
            Log.e(TAG, "joinRoom error,currentCallSession is exist");
            return;
        }
        mCurrentCallSession = new CallSession(context, room, false, mEvent);
        mCurrentCallSession.setIsComing(false);
        mCurrentCallSession.createHome(room, 9);
    }

    // Leave the room
    public void leaveRoom() {
        if (avEngineKit == null) {
            Log.e(TAG, "leaveRoom error,init is not set");
            return;
        }
        if (mCurrentCallSession != null) {
            mCurrentCallSession.leave();
            mCurrentCallSession.setCallState(EnumType.CallState.Idle);
        }
    }

    // Get a conversation instance
    public CallSession getCurrentSession() {
        return this.mCurrentCallSession;
    }


}
