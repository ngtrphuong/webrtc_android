package com.dds.webrtclib.ws;

import org.webrtc.IceCandidate;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dds on 2019/1/3.
 * android_shuai@163.com
 */
public interface ISignalingEvents {

    // WebSocket connection is successful
    void onWebSocketOpen();

    // webSocket connection failed
    void onWebSocketOpenFailed(String msg);

    // Enter the room
    void onJoinToRoom(ArrayList<String> connections, String myId);

    // A new user enters the room
    void onRemoteJoinToRoom(String socketId);

    void onRemoteIceCandidate(String socketId, IceCandidate iceCandidate);

    void onRemoteIceCandidateRemove(String socketId, List<IceCandidate> iceCandidates);


    void onRemoteOutRoom(String socketId);

    void onReceiveOffer(String socketId, String sdp);

    void onReceiverAnswer(String socketId, String sdp);

}
