package com.dds.net;

import java.io.InputStream;
import java.util.Map;

/**
 * Created by dds on 2018/4/23.
 */

public interface HttpRequest {

    /**
     * GET request
     *
     * @param url      url
     * @param params   params
     * @param callback callback
     */
    void get(String url, Map<String, Object> params, ICallback callback);

    /**
     * POST request
     *
     * @param url      url
     * @param params   params
     * @param callback callback
     */
    void post(String url, Map<String, Object> params, ICallback callback);

    /**
     * Set up a two-way certificate
     *
     * @param certificate certificate
     * @param pwd         pwd
     */
    void setCertificate(InputStream certificate, String pwd);
}
