package com.dds.core.voip;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.dds.App;
import com.dds.core.base.BaseActivity;
import com.dds.permission.Permissions;
import com.dds.skywebrtc.CallSession;
import com.dds.skywebrtc.EnumType;
import com.dds.skywebrtc.SkyEngineKit;
import com.dds.skywebrtc.except.NotInitializedException;
import com.dds.webrtc.R;

import java.util.UUID;


/**
 * Created by dds on 2018/7/26.
 * Single call interface
 */
public class CallSingleActivity extends BaseActivity implements CallSession.CallSessionCallback {

    public static final String EXTRA_TARGET = "targetId";
    public static final String EXTRA_MO = "isOutGoing";
    public static final String EXTRA_AUDIO_ONLY = "audioOnly";
    public static final String EXTRA_FROM_FLOATING_VIEW = "fromFloatingView";
    private static final String TAG = "CallSingleActivity";

    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isOutgoing;
    private String targetId;
    boolean isAudioOnly;
    private boolean isFromFloatingView;

    private SkyEngineKit gEngineKit;

    private CallSession.CallSessionCallback currentFragment;
    private String room;


    public static void openActivity(Context context, String targetId, boolean isOutgoing,
                                    boolean isAudioOnly) {
        Intent voip = new Intent(context, CallSingleActivity.class);
        voip.putExtra(CallSingleActivity.EXTRA_MO, isOutgoing);
        voip.putExtra(CallSingleActivity.EXTRA_TARGET, targetId);
        voip.putExtra(CallSingleActivity.EXTRA_AUDIO_ONLY, isAudioOnly);
        voip.putExtra(CallSingleActivity.EXTRA_FROM_FLOATING_VIEW, false);
        if (context instanceof Activity) {
            context.startActivity(voip);
        } else {
            voip.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(voip);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStatusBarOrScreenStatus(this);
        setContentView(R.layout.activity_single_call);

        try {
            gEngineKit = SkyEngineKit.Instance();
        } catch (NotInitializedException e) {
            finish();
        }
        final Intent intent = getIntent();
        targetId = intent.getStringExtra(EXTRA_TARGET);
        isFromFloatingView = intent.getBooleanExtra(EXTRA_FROM_FLOATING_VIEW, false);
        isOutgoing = intent.getBooleanExtra(EXTRA_MO, false);
        isAudioOnly = intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false);

        if (isFromFloatingView) {
            Intent serviceIntent = new Intent(this, FloatingVoipService.class);
            stopService(serviceIntent);
            init(targetId, false, isAudioOnly, false);
        } else {
            // Permission check
            String[] per;
            if (isAudioOnly) {
                per = new String[]{Manifest.permission.RECORD_AUDIO};
            } else {
                per = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA};
            }
            Permissions.request(this, per, integer -> {
                Log.d(TAG, "Permissions.request integer = " + integer);
                if (integer == 0) {
                    // Permission consent
                    init(targetId, isOutgoing, isAudioOnly, false);
                } else {
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
                    // Permission denied
                    CallSingleActivity.this.finish();
                }
            });
        }


    }

    @Override
    public void onBackPressed() {
        //You can’t press the return key during a call,
        // it’s the same phenomenon as WeChat, you can only hang up or answer
//        super.onBackPressed();
//        if (currentFragment != null) {
//            if (currentFragment instanceof FragmentAudio) {
//                ((FragmentAudio) currentFragment).onBackPressed();
//            } else if (currentFragment instanceof FragmentVideo) {
//                ((FragmentVideo) currentFragment).onBackPressed();
//            }
//        }

    }

    private void init(String targetId, boolean outgoing, boolean audioOnly, boolean isReplace) {
        Fragment fragment;
        if (audioOnly) {
            fragment = new FragmentAudio();
        } else {
            fragment = new FragmentVideo();
        }
        FragmentManager fragmentManager = getSupportFragmentManager();
        currentFragment = (CallSession.CallSessionCallback) fragment;
        if (isReplace) {
            fragmentManager.beginTransaction()
                    .replace(android.R.id.content, fragment)
                    .commit();
        } else {
            fragmentManager.beginTransaction()
                    .add(android.R.id.content, fragment)
                    .commit();
        }
        if (outgoing) {
            // Create session
            room = UUID.randomUUID().toString() + System.currentTimeMillis();
            boolean b = gEngineKit.startOutCall(getApplicationContext(), room, targetId, audioOnly);
            if (!b) {
                finish();
                return;
            }
            App.getInstance().setRoomId(room);
            App.getInstance().setOtherUserId(targetId);
            CallSession session = gEngineKit.getCurrentSession();
            if (session == null) {
                finish();
            } else {
                session.setSessionCallback(this);
            }
        } else {
            CallSession session = gEngineKit.getCurrentSession();
            if (session == null) {
                finish();
            } else {
                session.setSessionCallback(this);
            }
        }

    }

    public SkyEngineKit getEngineKit() {
        return gEngineKit;
    }

    public boolean isOutgoing() {
        return isOutgoing;
    }


    public boolean isFromFloatingView() {
        return isFromFloatingView;
    }

    // Show small window
    public void showFloatingView() {
        if (!checkOverlayPermission()) {
            return;
        }
        Intent intent = new Intent(this, FloatingVoipService.class);
        intent.putExtra(EXTRA_TARGET, targetId);
        intent.putExtra(EXTRA_AUDIO_ONLY, isAudioOnly);
        intent.putExtra(EXTRA_MO, isOutgoing);
        startService(intent);
        finish();
    }

    // Switch to voice call
    public void switchAudio() {
        init(targetId, isOutgoing, true, true);
    }

    public String getRoomId() {
        return room;
    }

    // ======================================Interface callback================================
    @Override
    public void didCallEndWithReason(EnumType.CallEndReason reason) {
        //Hand it over to fragment to finish
//        finish();
        handler.post(() -> currentFragment.didCallEndWithReason(reason));
    }

    @Override
    public void didChangeState(EnumType.CallState callState) {
        if (callState == EnumType.CallState.Connected) {
            isOutgoing = false;
        }
        handler.post(() -> currentFragment.didChangeState(callState));
    }

    @Override
    public void didChangeMode(boolean var1) {
        handler.post(() -> currentFragment.didChangeMode(var1));
    }

    @Override
    public void didCreateLocalVideoTrack() {
        handler.post(() -> currentFragment.didCreateLocalVideoTrack());
    }

    @Override
    public void didReceiveRemoteVideoTrack(String userId) {
        handler.post(() -> currentFragment.didReceiveRemoteVideoTrack(userId));
    }

    @Override
    public void didUserLeave(String userId) {

    }

    @Override
    public void didError(String var1) {
        finish();
    }


    // ========================================================================================

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            SettingsCompat.setDrawOverlays(this, true);
            if (!SettingsCompat.canDrawOverlays(this)) {
                Toast.makeText(this, "Need permission for floating window", Toast.LENGTH_LONG).show();
                SettingsCompat.manageDrawOverlays(this);
                return false;
            }
        }
        return true;
    }


    @TargetApi(19)
    private static int getSystemUiVisibility() {
        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        return flags;
    }

    /**
     * Set the status bar to be transparent
     */
    @TargetApi(19)
    public void setStatusBarOrScreenStatus(Activity activity) {
        Window window = activity.getWindow();
        //Full screen + lock screen + always bright display
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        window.getDecorView().setSystemUiVisibility(getSystemUiVisibility());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
            layoutParams.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(layoutParams);
        }
        // Above 5.0 system status bar is transparent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //Clear the transparent status bar
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            //Set the status bar color must be added
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);//Set transparency
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) { //19
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
