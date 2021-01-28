package com.dds.core.consts;

/**
 * Created by dds on 2020/4/19.
 * ddssingsong@163.com
 */
public class Urls {

    //private final static String IP = "10.128.197.95:5000";
    private final static String IP = "10.128.197.95:3000";
    //public final static String IP = "42.192.40.58:5000";

    private final static String HOST = "http://" + IP + "/";

    // Signaling address
    public final static String WS = "ws://" + IP + "/ws";

    // Get user list
    public static String getUserList() {
        return HOST + "userList";
    }

    // Get room list
    public static String getRoomList() {
        return HOST + "roomList";
    }
}
