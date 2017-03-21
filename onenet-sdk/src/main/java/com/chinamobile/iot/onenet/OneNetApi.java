package com.chinamobile.iot.onenet;

import android.app.Application;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.chinamobile.iot.onenet.http.HttpExecutor;
import com.chinamobile.iot.onenet.http.Urls;
import com.chinamobile.iot.onenet.module.ApiKey;
import com.chinamobile.iot.onenet.module.Binary;
import com.chinamobile.iot.onenet.module.Command;
import com.chinamobile.iot.onenet.module.DataPoint;
import com.chinamobile.iot.onenet.module.DataStream;
import com.chinamobile.iot.onenet.module.Device;
import com.chinamobile.iot.onenet.module.Mqtt;
import com.chinamobile.iot.onenet.module.Trigger;
import com.chinamobile.iot.onenet.util.Assertions;
import com.chinamobile.iot.onenet.util.Meta;
import com.chinamobile.iot.onenet.util.OneNetLogger;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

public class OneNetApi {

    public static final String LOG_TAG = "OneNetApi";

    private static String sAppKey;
    static boolean sDebug;
    private static HttpExecutor sHttpExecutor;

    public static void init(Application application, boolean debug) {
        try {
            sAppKey = Meta.readAppKey(application);
            Urls.sHost = Meta.readHost(application);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        sDebug = debug;
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder();
        if (sDebug) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(new OneNetLogger());
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            okHttpClientBuilder.addNetworkInterceptor(loggingInterceptor);
        }
        okHttpClientBuilder.addInterceptor(sApiKeyInterceptor);
        sHttpExecutor = new HttpExecutor(okHttpClientBuilder.build());
    }

    private static Interceptor sApiKeyInterceptor = new Interceptor() {

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request.Builder builder = chain.request().newBuilder();
            builder.addHeader("api-key", sAppKey);
            if (TextUtils.isEmpty(sAppKey)) {
                Log.e(LOG_TAG, "app-key is messing, please config in the meta-data or call setAppKey()");
            }
            return chain.proceed(builder.build());
        }
    };

    public static void setAppKey(String apiKey) {
        sAppKey = apiKey;
    }

    public static String getAppKey() {
        return sAppKey;
    }

    private static boolean isInitialized() {
        return sHttpExecutor != null;
    }

    private static boolean isOnUIThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    private static void assertInitialized() {
        Assertions.assertCondition(isInitialized(), "You should call OneNetApi.init() in your Application!");
    }

    private static void assertUIThread() {
        Assertions.assertCondition(isOnUIThread(), "Expected to run on UI thread!");
    }

    private static void get(String url, OneNetApiCallback callback) {
        assertInitialized();
        assertUIThread();
        sHttpExecutor.get(url, new OneNetApiCallbackAdapter(callback));
    }

    private static void post(String url, String requestBodyString, OneNetApiCallback callback) {
        assertInitialized();
        assertUIThread();
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), requestBodyString);
        sHttpExecutor.post(url, requestBody, new OneNetApiCallbackAdapter(callback));
    }

    private static void post(String url, File file, OneNetApiCallback callback) {
        assertInitialized();
        assertUIThread();
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/octet-stream"), file);
        sHttpExecutor.post(url, requestBody, new OneNetApiCallbackAdapter(callback));
    }

    private static void post(String url, byte[] content, OneNetApiCallback callback) {
        assertInitialized();
        assertUIThread();
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/octet-stream"), content);
        sHttpExecutor.post(url, requestBody, new OneNetApiCallbackAdapter(callback));
    }

    private static void put(String url, String requestBodyString, OneNetApiCallback callback) {
        assertInitialized();
        assertUIThread();
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), requestBodyString);
        sHttpExecutor.put(url, requestBody, new OneNetApiCallbackAdapter(callback));
    }

    private static void delete(String url, OneNetApiCallback callback) {
        assertInitialized();
        assertUIThread();
        sHttpExecutor.delete(url, new OneNetApiCallbackAdapter(callback));
    }

    /******************** 设备相关api ********************/

    /**
     * 注册设备（该方法用于让设备终端自己在平台创建设备，不适用于APP）
     *
     * @param registerCode      设备注册码
     * @param requestBodyString HTTP内容 详见<a href="http://www.heclouds.com/doc/art262.html#68">
     *                          http://www.heclouds.com/doc/art262.html#68</a>
     * @param callback          回调函数
     */
    public static void registerDevice(String registerCode, String requestBodyString, OneNetApiCallback callback) {
        post(Device.urlForRegistering(registerCode), requestBodyString, callback);
    }

    /**
     * 新增设备
     *
     * @param requestBodyString HTTP内容 详见<a href="http://www.heclouds.com/doc/art262.html#68">
     *                          http://www.heclouds.com/doc/art262.html#68</a>
     * @param callback          回调函数
     */
    public static void addDevice(String requestBodyString, OneNetApiCallback callback) {
        post(Device.urlForAdding(), requestBodyString, callback);
    }

    /**
     * 更新设备
     *
     * @param deviceId          设备ID
     * @param requestBodyString HTTP内容 详见<a href="http://www.heclouds.com/doc/art262.html#68">
     *                          http://www.heclouds.com/doc/art262.html#68</a>
     * @param callback          回调函数
     */
    public static void updateDevice(String deviceId, String requestBodyString, OneNetApiCallback callback) {
        put(Device.urlForUpdating(deviceId), requestBodyString, callback);
    }

    /**
     * 精确查询单个设备
     *
     * @param deviceId 设备ID
     * @param callback 回调函数
     */
    public static void querySingleDevice(String deviceId, OneNetApiCallback callback) {
        get(Device.urlForQueryingSingle(deviceId), callback);
    }

    /**
     * 模糊查询设备
     *
     * @param params   URL参数 详见<a href="http://www.heclouds.com/doc/art262.html#68">
     *                 http://www.heclouds.com/doc/art262.html#68</a>
     * @param callback 回调函数
     */
    public static void fuzzyQueryDevices(Map<String, String> params, OneNetApiCallback callback) {
        get(Device.urlForfuzzyQuerying(params), callback);
    }

    /**
     * 删除设备
     *
     * @param deviceId 设备ID
     * @param callback 回调函数
     */
    public static void deleteDevice(String deviceId, OneNetApiCallback callback) {
        delete(Device.urlForDeleting(deviceId), callback);
    }

    /******************** END ********************/

    /******************** 数据流相关api ********************/

    /**
     * 新增数据流
     *
     * @param deviceId          设备ID
     * @param requestBodyString HTTP内容 详见 <a href="http://www.heclouds.com/doc/art261.html#68">
     *                          http://www.heclouds.com/doc/art261.html#68</a>
     * @param callback          回调函数
     */
    public static void addDataStream(String deviceId, String requestBodyString, OneNetApiCallback callback) {
        post(DataStream.urlForAdding(deviceId), requestBodyString, callback);
    }

    /**
     * 更新数据流
     *
     * @param deviceId          设备ID
     * @param dataStreamId      数据流ID
     * @param requestBodyString HTTP内容 详见 <a href="http://www.heclouds.com/doc/art261.html#68">
     *                          http://www.heclouds.com/doc/art261.html#68</a>
     * @param callback          回调函数
     */
    public static void updateDataStream(String deviceId, String dataStreamId, String requestBodyString, OneNetApiCallback callback) {
        put(DataStream.urlForUpdating(deviceId, dataStreamId), requestBodyString, callback);
    }

    /**
     * 查询单个数据流
     *
     * @param deviceId     设备ID
     * @param dataStreamId 数据流ID
     * @param callback     回调函数
     */
    public static void querySingleDataStream(String deviceId, String dataStreamId, OneNetApiCallback callback) {
        get(DataStream.urlForQueryingSingle(deviceId, dataStreamId), callback);
    }

    /**
     * 查询多个数据流
     *
     * @param deviceId 设备ID
     * @param callback 回调函数
     */
    public static void queryMultiDataStreams(String deviceId, OneNetApiCallback callback) {
        get(DataStream.urlForQueryingMulti(deviceId), callback);
    }

    /**
     * 删除数据流
     *
     * @param deviceId     设备ID
     * @param dataStreamId 数据流ID
     * @param callback     回调函数
     */
    public static void deleteDatastream(String deviceId, String dataStreamId, OneNetApiCallback callback) {
        delete(DataStream.urlForDeleting(deviceId, dataStreamId), callback);
    }

    /******************** END ********************/

    /******************** 数据点相关api ********************/

    /**
     * 新增数据点(数据点类型为3)
     *
     * @param deviceId          设备ID
     * @param requestBodyString HTTP内容 详见<a href="http://www.heclouds.com/doc/art260.html#68">
     *                          http://www.heclouds.com/doc/art260.html#68</a>
     * @param callback          回调函数
     */
    public static void addDataPoints(String deviceId, String requestBodyString, OneNetApiCallback callback) {
        post(DataPoint.urlForAdding(deviceId, null), requestBodyString, callback);
    }

    /**
     * 新增数据点
     *
     * @param deviceId          设备ID
     * @param type              数据点类型
     * @param requestBodyString HTTP内容 详见<a href="http://www.heclouds.com/doc/art260.html#68">
     *                          http://www.heclouds.com/doc/art260.html#68</a>
     * @param callback          回调函数
     */
    public static void addDataPoints(String deviceId, String type, String requestBodyString, OneNetApiCallback callback) {
        post(DataPoint.urlForAdding(deviceId, type), requestBodyString, callback);
    }

    /**
     * 查询数据点
     *
     * @param deviceId 设备ID
     * @param params   URL参数 详见<a href="http://www.heclouds.com/doc/art260.html#68">
     *                 http://www.heclouds.com/doc/art260.html#68</a>
     * @param callback 回调函数
     */
    public static void queryDataPoints(String deviceId, Map<String, String> params, OneNetApiCallback callback) {
        get(DataPoint.urlForQuerying(deviceId, params), callback);
    }

    /******************** END ********************/

    /******************** 触发器相关api ********************/

    /**
     * 新增触发器
     *
     * @param requestBodyString HTTP内容 详见<a href="http://www.heclouds.com/doc/art259.html#68">
     *                          http://www.heclouds.com/doc/art259.html#68</a>
     * @param callback          回调函数
     */
    public static void addTrigger(String requestBodyString, OneNetApiCallback callback) {
        post(Trigger.urlForAdding(), requestBodyString, callback);
    }

    /**
     * 更新触发器
     *
     * @param triggerId         触发器ID
     * @param requestBodyString HTTP内容 详见<a href="http://www.heclouds.com/doc/art259.html#68">
     *                          http://www.heclouds.com/doc/art259.html#68</a>
     * @param callback          回调函数
     */
    public static void updateTrigger(String triggerId, String requestBodyString, OneNetApiCallback callback) {
        put(Trigger.urlForUpdating(triggerId), requestBodyString, callback);
    }

    /**
     * 查询单个触发器
     *
     * @param triggerId 触发器ID
     * @param callback  回调函数
     */
    public static void querySingleTrigger(String triggerId, OneNetApiCallback callback) {
        get(Trigger.urlForQueryingSingle(triggerId), callback);
    }

    /**
     * 模糊查询触发器
     *
     * @param name     指定触发器名称
     * @param page     指定页码
     * @param perPager 指定每页输出个数，最多100
     * @param callback 回调函数
     */
    public static void fuzzyQueryTriggers(String name, int page, int perPager, OneNetApiCallback callback) {
        get(Trigger.urlForfuzzyQuerying(name, page, perPager), callback);
    }

    /**
     * 删除触发器
     *
     * @param triggerId 触发器ID
     * @param callback  回调函数
     */
    public static void deleteTrigger(String triggerId, OneNetApiCallback callback) {
        delete(Trigger.urlForDeleting(triggerId), callback);
    }

    /******************** END ********************/

    /******************** 二进制相关api ********************/

    /**
     * 新增二进制数据
     *
     * @param deviceId         设备ID
     * @param dataStreamId     数据流ID
     * @param requestBodyBytes HTTP内容，普通二进制数据、文件、图像等（最大限制为800k）
     * @param callback         回调函数
     */
    public static void addBinaryData(String deviceId, String dataStreamId, byte[] requestBodyBytes, OneNetApiCallback callback) {
        post(Binary.urlForAdding(deviceId, dataStreamId), requestBodyBytes, callback);
    }

    /**
     * 新增二进制数据
     *
     * @param deviceId        设备ID
     * @param dataStreamId    数据流ID
     * @param requestBodyFile HTTP内容，文件、图像等（最大限制为800k）
     * @param callback        回调函数
     */
    public static void addBinaryData(String deviceId, String dataStreamId, File requestBodyFile, OneNetApiCallback callback) {
        post(Binary.urlForAdding(deviceId, dataStreamId), requestBodyFile, callback);
    }

    /**
     * 新增二进制数据
     *
     * @param deviceId          设备ID
     * @param dataStreamId      数据流ID
     * @param requestBodyString HTTP内容，普通二进制数据、文件、图像等（最大限制为800k）
     * @param callback          回调函数
     */
    public static void addBinaryData(String deviceId, String dataStreamId, String requestBodyString, OneNetApiCallback callback) {
        post(Binary.urlForAdding(deviceId, dataStreamId), requestBodyString, callback);
    }

    /**
     * 查询二进制数据
     *
     * @param index    二进制数据索引号
     * @param callback 回调函数
     */
    public static void queryBinaryData(String index, OneNetApiCallback callback) {
        get(Binary.urlForQuerying(index), callback);
    }

    /******************** END ********************/

    /******************** 命令相关api ********************/

    public static void sendCmdToDevice(String deviceId, String requestBodyString, OneNetApiCallback callback) {
        post(Command.urlForSending(deviceId), requestBodyString, callback);
    }

    public static void sendCmdToDevice(String deviceId, boolean needResponse, int timeout,
                                       Command.CommandType type, String requestBodyString,
                                       OneNetApiCallback callback) {
        post(Command.urlForSending(deviceId, needResponse, timeout, type), requestBodyString, callback);
    }

    public static void queryCmdStatus(String cmdUuid, OneNetApiCallback callback) {
        get(Command.urlForQueryingStatus(cmdUuid), callback);
    }

    public static void queryCmdResponse(String cmdUuid, OneNetApiCallback callback) {
        get(Command.urlForQueryingResponse(cmdUuid), callback);
    }

    /******************** END ********************/

    /******************** MQTT相关api ********************/

    public static void sendCmdByTopic(String topic, String requestBodyString, OneNetApiCallback callback) {
        post(Mqtt.urlForSendingCmdByTopic(topic), requestBodyString, callback);
    }

    public static void queryDevicesByTopic(String topic, int page, int perPage, OneNetApiCallback callback) {
        get(Mqtt.urlForQueryingDevicesByTopic(topic, page, perPage), callback);
    }

    public static void queryDeviceTopics(String deviceId, OneNetApiCallback callback) {
        get(Mqtt.urlForQueryingDeviceTopics(deviceId), callback);
    }

    public static void addTopic(String requestBodyString, OneNetApiCallback callback) {
        post(Mqtt.urlForAddingTopic(), requestBodyString, callback);
    }

    public static void deleteTopic(String topic, OneNetApiCallback callback) {
        delete(Mqtt.urlForDeletingTopic(topic), callback);
    }

    public static void queryTopics(OneNetApiCallback callback) {
        get(Mqtt.urlForQueryingTopics(), callback);
    }

    /******************** END ********************/

    /******************** ApiKey相关api ********************/

    public static void addApiKey(String requestBodyString, OneNetApiCallback callback) {
        post(ApiKey.urlForAdding(), requestBodyString, callback);
    }

    public static void updateApiKey(String key, String requestBodyString, OneNetApiCallback callback) {
        put(ApiKey.urlForUpdating(key), requestBodyString, callback);
    }

    public static void queryApiKey(String key, OneNetApiCallback callback) {
        get(ApiKey.urlForQuerying(key, 0, 0, null), callback);
    }

    public static void queryApiKey(String key, int page, int perPage, String deviceId, OneNetApiCallback callback) {
        get(ApiKey.urlForQuerying(key, page, perPage, deviceId), callback);
    }

    public static void deleteApiKey(String key, OneNetApiCallback callback) {
        delete(ApiKey.urlForDeleting(key), callback);
    }

    /******************** END ********************/
}
