package com.dds.core;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.dds.LauncherActivity;
import com.dds.core.base.BaseActivity;
import com.dds.core.socket.IUserState;
import com.dds.core.socket.SocketManager;
import com.dds.webrtc.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Main interface
 */
public class MainActivity extends BaseActivity implements IUserState {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BottomNavigationView navView = findViewById(R.id.nav_view);
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_user, R.id.navigation_room, R.id.navigation_setting)
                .build();
        // Set ActionBar to follow linkage
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        // Set Nav follow linkage
        NavigationUI.setupWithNavController(navView, navController);
        // Set login status callback
        SocketManager.getInstance().addUserStateCallback(this);

    }

    @Override
    public void userLogin() {

    }

    @Override
    public void userLogout() {
        if (!this.isFinishing()) {
            Intent intent = new Intent(this, LauncherActivity.class);
            startActivity(intent);
            this.finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAfterTransition();
        } else {
            super.onBackPressed();
        }

    }
}
