package com.dds.skywebrtc.engine;


import android.view.View;

import java.util.List;

/**
 * rtc base class
 */
public interface IEngine {

    /**
     * Initialization
     */
    void init(EngineCallback callback);

    /**
     * Join room
     */
    void joinRoom(List<String> userIds);

    /**
     * Someone enters the room
     */
    void userIn(String userId);

    /**
     * User rejected
     * @param userId userId
     * @param type type
     */
    void userReject(String userId,int type);

    /**
     * User network disconnect
     * @param userId userId
     */
    void disconnected(String userId);

    /**
     * receive Offer
     */
    void receiveOffer(String userId, String description);

    /**
     * receive Answer
     */
    void receiveAnswer(String userId, String sdp);

    /**
     * receive IceCandidate
     */
    void receiveIceCandidate(String userId, String id, int label, String candidate);

    /**
     * Leave the room
     *
     * @param userId userId
     */
    void leaveRoom(String userId);

    /**
     * Open local preview
     */
    View startPreview(boolean isOverlay);

    /**
     * Close local preview
     */
    void stopPreview();

    /**
     * Start remote streaming
     */
    void startStream();

    /**
     * Stop remote streaming
     */
    void stopStream();

    /**
     * Start remote preview
     */
    View setupRemoteVideo(String userId, boolean isO);

    /**
     * Turn off remote preview
     */
    void stopRemoteVideo();

    /**
     * Switch camera
     */
    void switchCamera();

    /**
     * Set mute
     */
    boolean muteAudio(boolean enable);

    /**
     * Turn on the speaker
     */
    boolean toggleSpeaker(boolean enable);

    /**
     * Release everything
     */
    void release();

}
