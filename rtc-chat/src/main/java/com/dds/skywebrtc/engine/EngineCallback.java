package com.dds.skywebrtc.engine;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

/**
 * Created by dds on 2020/4/12.
 * Frame callback
 */
public interface EngineCallback {


    /**
     * Successfully joined the room
     */
    void joinRoomSucc();

    /**
     * Exit the room successfully
     */
    void exitRoom();

    /**
     * Connection refused
     * @param type type
     */
    void reject(int type);

    void disconnected();

    void onSendIceCandidate(String userId, IceCandidate candidate);

    void onSendOffer(String userId, SessionDescription description);

    void onSendAnswer(String userId, SessionDescription description);

    void onRemoteStream(String userId);

}
