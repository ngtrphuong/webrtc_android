package com.dds.core.voip;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.dds.core.util.OSUtils;
import com.dds.skywebrtc.CallSession;
import com.dds.skywebrtc.EnumType;
import com.dds.skywebrtc.SkyEngineKit;
import com.dds.webrtc.R;

/**
 * Created by dds on 2018/7/26.
 * android_shuai@163.com
 * Voice call control interface
 */
public class FragmentAudio extends Fragment implements CallSession.CallSessionCallback, View.OnClickListener {
    private ImageView minimizeImageView;
    private ImageView portraitImageView;  // profile picture
    private TextView nameTextView;        // User's Nickname
    private TextView descTextView;        // Status prompt
    private Chronometer durationTextView;    // Call duration

    private ImageView muteImageView;
    private ImageView outgoingHangupImageView;
    private ImageView speakerImageView;
    private ImageView incomingHangupImageView;
    private ImageView acceptImageView;
    private TextView tvStatus;
    private LinearLayout lytParent;

    private SkyEngineKit gEngineKit;

    private View outgoingActionContainer;
    private View incomingActionContainer;


    private boolean micEnabled = false;  // Mute
    private boolean isSpeakerOn = false;// speaker
    private CallSingleActivity activity;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (CallSingleActivity) getActivity();
        if (activity != null) {
            gEngineKit = activity.getEngineKit();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_audio, container, false);
        initView(view);
        init();
        return view;
    }

    private void initView(View view) {
        lytParent = view.findViewById(R.id.lytParent);
        minimizeImageView = view.findViewById(R.id.minimizeImageView);
        portraitImageView = view.findViewById(R.id.portraitImageView);
        nameTextView = view.findViewById(R.id.nameTextView);
        descTextView = view.findViewById(R.id.descTextView);
        durationTextView = view.findViewById(R.id.durationTextView);
        muteImageView = view.findViewById(R.id.muteImageView);
        outgoingHangupImageView = view.findViewById(R.id.outgoingHangupImageView);
        speakerImageView = view.findViewById(R.id.speakerImageView);
        incomingHangupImageView = view.findViewById(R.id.incomingHangupImageView);
        acceptImageView = view.findViewById(R.id.acceptImageView);
        tvStatus = view.findViewById(R.id.tvStatus);
        outgoingActionContainer = view.findViewById(R.id.outgoingActionContainer);
        incomingActionContainer = view.findViewById(R.id.incomingActionContainer);

        acceptImageView.setOnClickListener(this);
        incomingHangupImageView.setOnClickListener(this);
        outgoingHangupImageView.setOnClickListener(this);
        muteImageView.setOnClickListener(this);
        speakerImageView.setOnClickListener(this);
        minimizeImageView.setOnClickListener(this);

        durationTextView.setVisibility(View.GONE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M || OSUtils.isMiui() || OSUtils.isFlyme()) {
            lytParent.post(() -> {
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) minimizeImageView.getLayoutParams();
                params.topMargin = com.dds.core.util.Utils.getStatusBarHeight();
                minimizeImageView.setLayoutParams(params);

            });
        }
    }

    private void init() {
        CallSession currentSession = gEngineKit.getCurrentSession();
        // If already connected
        if (currentSession != null && currentSession.getState() == EnumType.CallState.Connected) {
            descTextView.setVisibility(View.GONE); // Hint
            outgoingActionContainer.setVisibility(View.VISIBLE);
            durationTextView.setVisibility(View.VISIBLE);
            startRefreshTime();
        } else {
            // If not connected
            if (activity.isOutgoing()) {
                descTextView.setText(R.string.av_waiting);
                outgoingActionContainer.setVisibility(View.VISIBLE);
                incomingActionContainer.setVisibility(View.GONE);
            } else {
                descTextView.setText(R.string.av_audio_invite);
                outgoingActionContainer.setVisibility(View.GONE);
                incomingActionContainer.setVisibility(View.VISIBLE);
            }
        }

    }

    @Override
    public void onDetach() {
        super.onDetach();
        activity = null;
    }

    // ======================================Interface callback================================
    @Override
    public void didCallEndWithReason(EnumType.CallEndReason callEndReason) {
        switch (callEndReason) {
            case Busy:
                tvStatus.setText(R.string.busy_party);
                break;
            case SignalError:
                tvStatus.setText(R.string.signaling_error);
                break;
            case Hangup:
                tvStatus.setText(R.string.hang_up);
                break;
            case MediaError:
                tvStatus.setText(R.string.media_error);
                break;
            case RemoteHangup:
                tvStatus.setText(R.string.remote_hangup);
                break;
            case OpenCameraFailure:
                tvStatus.setText(R.string.error_open_camera);
                break;
            case Timeout:
                tvStatus.setText(R.string.timeout);
                break;
            case AcceptByOtherClient:
                tvStatus.setText(R.string.accept_by_other_client);
                break;
        }
        incomingActionContainer.setVisibility(View.GONE);
        outgoingActionContainer.setVisibility(View.GONE);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (activity != null) {
                activity.finish();
            }
        }, 1500);
    }

    @Override
    public void didChangeState(EnumType.CallState state) {
        runOnUiThread(() -> {
            if (state == EnumType.CallState.Connected) {
                incomingActionContainer.setVisibility(View.GONE);
                outgoingActionContainer.setVisibility(View.VISIBLE);
                descTextView.setVisibility(View.GONE);

                startRefreshTime();
            } else {
                // do nothing now
            }
        });
    }

    @Override
    public void didChangeMode(boolean isAudio) {

    }

    @Override
    public void didCreateLocalVideoTrack() {

    }

    @Override
    public void didReceiveRemoteVideoTrack(String userId) {

    }

    @Override
    public void didUserLeave(String userId) {

    }

    @Override
    public void didError(String error) {

    }


    private void runOnUiThread(Runnable runnable) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(runnable);
        }
    }

    //    public void onBackPressed() {
//        CallSession session = gEngineKit.getCurrentSession();
//        if (session != null) {
//            SkyEngineKit.Instance().endCall();
//            activity.finish();
//        } else {
//            activity.finish();
//        }
//    }
    @Override
    public void onClick(View v) {
        int id = v.getId();
        // Answer
        if (id == R.id.acceptImageView) {
            CallSession session = gEngineKit.getCurrentSession();
            if (session != null && session.getState() == EnumType.CallState.Incoming) {
                session.joinHome(session.getRoomId());
            } else {
                activity.finish();
            }
        }
        // hang up the phone
        if (id == R.id.incomingHangupImageView || id == R.id.outgoingHangupImageView) {
            CallSession session = gEngineKit.getCurrentSession();
            if (session != null) {
                SkyEngineKit.Instance().endCall();
                activity.finish();
            } else {
                activity.finish();
            }
        }
        // Mute
        if (id == R.id.muteImageView) {
            CallSession session = gEngineKit.getCurrentSession();
            if (session != null && session.getState() != EnumType.CallState.Idle) {
                if (session.toggleMuteAudio(!micEnabled)) {
                    micEnabled = !micEnabled;
                }
                muteImageView.setSelected(micEnabled);
            }
        }
        // speaker
        if (id == R.id.speakerImageView) {
            CallSession session = gEngineKit.getCurrentSession();
            if (session != null && session.getState() != EnumType.CallState.Idle) {
                if (session.toggleSpeaker(!isSpeakerOn)) {
                    isSpeakerOn = !isSpeakerOn;
                }
                speakerImageView.setSelected(isSpeakerOn);
            }

        }
        // Small window
        if (id == R.id.minimizeImageView) {
            activity.showFloatingView();
        }

    }

    private void startRefreshTime() {
        CallSession session = SkyEngineKit.Instance().getCurrentSession();
        if (session == null) {
            return;
        }
        if (durationTextView != null) {
            durationTextView.setVisibility(View.VISIBLE);
            durationTextView.setBase(SystemClock.elapsedRealtime() - (System.currentTimeMillis() - session.getStartTime()));
            durationTextView.start();
        }
    }
}
