package com.dds.core.ui.room;

import android.util.Log;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.dds.core.consts.Urls;
import com.dds.net.HttpRequestPresenter;
import com.dds.net.ICallback;

import java.util.List;

public class RoomViewModel extends ViewModel {

    private MutableLiveData<List<RoomInfo>> mList;
    private Thread thread;

    public RoomViewModel() {
    }

    public MutableLiveData<List<RoomInfo>> getRoomList() {
        if (mList == null) {
            mList = new MutableLiveData<>();
            loadRooms();
        }
        return mList;
    }

    public void loadRooms() {
        thread = new Thread(() -> {
            String url = Urls.getRoomList();
            HttpRequestPresenter.getInstance().get(url, null, new ICallback() {
                @Override
                public void onSuccess(String result) {
                    Log.d("loadRooms - onSuccess()", result);
                    List<RoomInfo> roomInfos = JSON.parseArray(result, RoomInfo.class);
                    Log.d("loadRooms - roomInfo", roomInfos.toString());
                    mList.postValue(roomInfos);
                }

                @Override
                public void onFailure(int code, Throwable t) {
                    Log.d("loadRooms - onFailure()", "code:" + code + ",msg:" + t.toString());
                }
            });
        });
        thread.start();
    }
}