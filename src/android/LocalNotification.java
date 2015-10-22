/*
 * Copyright (c) 2013-2015 by appPlant UG. All rights reserved.
 *
 * @APPPLANT_LICENSE_HEADER_START@
 *
 * This file contains Original Code and/or Modifications of Original Code
 * as defined in and that are subject to the Apache License
 * Version 2.0 (the 'License'). You may not use this file except in
 * compliance with the License. Please obtain a copy of the License at
 * http://opensource.org/licenses/Apache-2.0/ and read it before using this
 * file.
 *
 * The Original Code and all software distributed under the License are
 * distributed on an 'AS IS' basis, WITHOUT WARRANTY OF ANY KIND, EITHER
 * EXPRESS OR IMPLIED, AND APPLE HEREBY DISCLAIMS ALL SUCH WARRANTIES,
 * INCLUDING WITHOUT LIMITATION, ANY WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE, QUIET ENJOYMENT OR NON-INFRINGEMENT.
 * Please see the License for the specific language governing rights and
 * limitations under the License.
 *
 * @APPPLANT_LICENSE_HEADER_END@
 */

package de.appplant.cordova.plugin.localnotification;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.view.ViewGroup;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import de.appplant.cordova.plugin.notification.Manager;
import de.appplant.cordova.plugin.notification.Notification;

/**
 * This plugin utilizes the Android AlarmManager in combination with local
 * notifications. When a local notification is scheduled the alarm manager takes
 * care of firing the event. When the event is processed, a notification is put
 * in the Android notification center and status bar.
 */
public class LocalNotification extends CordovaPlugin {

    // Reference to the web view for static access
    private static CordovaWebView webView = null;

    private static CordovaInterface cordova = null;

    private static List<WebViewReference> mWebViewReferences = new ArrayList<WebViewReference>();

    // Indicates if the device is ready (to receive events)
    private static Boolean deviceready = false;

    // To inform the user about the state of the app in callbacks
    protected static Boolean isInBackground = true;

    // Queues all events before deviceready
    private static ArrayList<String> eventQueue = new ArrayList<String>();

    /**
     * Called after plugin construction and fields have been initialized.
     * Prefer to use pluginInitialize instead since there is no value in
     * having parameters on the initialize() function.
     *
     * pluginInitialize is not available for cordova 3.0-3.5 !
     */
    @Override
    public void initialize (CordovaInterface cordova, CordovaWebView webView) {
        //LocalNotification.webView = super.webView;
        //LocalNotification.cordova = cordova;
        /*if(webView != null){
            String webUrl = webView.getUrl();
            Log.d("localNotification","initializing - "+ webUrl);
        }*/
        if(this.webView == null){
            Log.d("localNotification","initialize - referencing instance webView");
            this.webView = webView;
        }
        if(this.cordova == null){
            Log.d("localNotification","initialize - referencing instance cordova");
            this.cordova = cordova;
        }
        /*if(LocalNotification.webView == null){
            Log.d("localNotification","initialize - referencing static webView");
            LocalNotification.webView = webView;
        }
        if(LocalNotification.cordova == null){
            Log.d("localNotification","initialize - referencing static cordova");
            LocalNotification.cordova = cordova;
        }*/
        //super.initialize(cordova, webView);
        registerWebView(webView);

    }

    /**
     * Called when the system is about to start resuming a previous activity.
     *
     * @param multitasking
     *      Flag indicating if multitasking is turned on for app
     */
    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);
        isInBackground = true;
    }

    /**
     * Called when the activity will start interacting with the user.
     *
     * @param multitasking
     *      Flag indicating if multitasking is turned on for app
     */
    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        isInBackground = false;
        deviceready();
    }

    /**
     * The final call you receive before your activity is destroyed.
     */
    @Override
    public void onDestroy() {
        deviceready = false;
        isInBackground = true;
    }

    /**
     * Executes the request.
     *
     * This method is called from the WebView thread. To do a non-trivial
     * amount of work, use:
     *      cordova.getThreadPool().execute(runnable);
     *
     * To run on the UI thread, use:
     *     cordova.getActivity().runOnUiThread(runnable);
     *
     * @param action
     *      The action to execute.
     * @param args
     *      The exec() arguments in JSON form.
     * @param command
     *      The callback context used when calling back into JavaScript.
     * @return
     *      Whether the action was valid.
     */
    @Override
    public boolean execute (final String action, final JSONArray args,
                            final CallbackContext command) throws JSONException {
        Log.d("localNotification","execute - @Override method");
        //LocalNotification.webView = super.webView;
        //LocalNotification.cordova = super.cordova;
        registerWebView(webView);
        Log.e("localNotification","execute - mWebViewReferences size: "+ mWebViewReferences.size());
        Notification.setDefaultTriggerReceiver(TriggerReceiver.class);
        if (cordova == null) {
            Log.e("localNotification","execute - instance cordova is null");
            //throw new Error("execute => cordova is null");
        }
        ExecutorService tp = cordova.getThreadPool();
        if (tp == null) {
            Log.e("localNotification","execute - cordova.getThreadPool() is null");
            //throw new Error("execute => cordova.getThreadPool() returned null");
        }
        if (command == null) {
            Log.e("localNotification","execute - CallbackContext is null");
            //throw new Error("execute => command is null");
        }
        tp.execute(new Runnable() {
            public void run() {
                if (action.equals("schedule")) {
                    schedule(args);
                    command.success();
                }
                else if (action.equals("update")) {
                    update(args);
                    command.success();
                }
                else if (action.equals("cancel")) {
                    cancel(args);
                    command.success();
                }
                else if (action.equals("cancelAll")) {
                    cancelAll();
                    command.success();
                }
                else if (action.equals("clear")) {
                    clear(args);
                    command.success();
                }
                else if (action.equals("clearAll")) {
                    clearAll();
                    command.success();
                }
                else if (action.equals("isPresent")) {
                    isPresent(args.optInt(0), command);
                }
                else if (action.equals("isScheduled")) {
                    isScheduled(args.optInt(0), command);
                }
                else if (action.equals("isTriggered")) {
                    isTriggered(args.optInt(0), command);
                }
                else if (action.equals("getAllIds")) {
                    getAllIds(command);
                }
                else if (action.equals("getScheduledIds")) {
                    getScheduledIds(command);
                }
                else if (action.equals("getTriggeredIds")) {
                    getTriggeredIds(command);
                }
                else if (action.equals("getSingle")) {
                    getSingle(args, command);
                }
                else if (action.equals("getSingleScheduled")) {
                    getSingleScheduled(args, command);
                }
                else if (action.equals("getSingleTriggered")) {
                    getSingleTriggered(args, command);
                }
                else if (action.equals("getAll")) {
                    getAll(args, command);
                }
                else if (action.equals("getScheduled")) {
                    getScheduled(args, command);
                }
                else if (action.equals("getTriggered")) {
                    getTriggered(args, command);
                }
                else if (action.equals("deviceready")) {
                    deviceready();
                }
                else if (action.equals("_init")) {
                    Log.d("localNotification","_init execute");
                    if(webView != null){
                        Log.d("localNotification","initialize - referencing static webView");
                        LocalNotification.webView = webView;
                    }
                    if(cordova != null){
                        Log.d("localNotification","initialize - referencing static cordova");
                        LocalNotification.cordova = cordova;
                    }
                    //JSONObject jb = new JSONObject("{\"ok\": true }");
                    command.sendPluginResult(new PluginResult(PluginResult.Status.OK, "OK"));
                }
                
            }
        });

        return true;
    }

    /**
     * Schedule multiple local notifications.
     *
     * @param notifications
     *      Properties for each local notification
     */
    private void schedule (JSONArray notifications) {
        Log.d("localNotification","schedule - scheduling notifications " + notifications);
        for (int i = 0; i < notifications.length(); i++) {
            JSONObject options = notifications.optJSONObject(i);

            Notification notification =
                    getNotificationMgr().schedule(options, TriggerReceiver.class);

            fireEvent("schedule", notification);
        }
    }

    /**
     * Update multiple local notifications.
     *
     * @param updates
     *      Notification properties including their IDs
     */
    private void update (JSONArray updates) {
        for (int i = 0; i < updates.length(); i++) {
            JSONObject update = updates.optJSONObject(i);
            int id = update.optInt("id", 0);

            Notification notification =
                    getNotificationMgr().update(id, update, TriggerReceiver.class);

            fireEvent("update", notification);
        }
    }

    /**
     * Cancel multiple local notifications.
     *
     * @param ids
     *      Set of local notification IDs
     */
    private void cancel (JSONArray ids) {
        for (int i = 0; i < ids.length(); i++) {
            int id = ids.optInt(i, 0);

            Notification notification =
                    getNotificationMgr().cancel(id);

            if (notification != null) {
                fireEvent("cancel", notification);
            }
        }
    }

    /**
     * Cancel all scheduled notifications.
     */
    private void cancelAll() {
        getNotificationMgr().cancelAll();
        fireEvent("cancelall");
    }

    /**
     * Clear multiple local notifications without canceling them.
     *
     * @param ids
     *      Set of local notification IDs
     */
    private void clear(JSONArray ids){
        for (int i = 0; i < ids.length(); i++) {
            int id = ids.optInt(i, 0);

            Notification notification =
                    getNotificationMgr().clear(id);

            if (notification != null) {
                fireEvent("clear", notification);
            }
        }
    }

    /**
     * Clear all triggered notifications without canceling them.
     */
    private void clearAll() {
        getNotificationMgr().clearAll();
        fireEvent("clearall");
    }

    /**
     * If a notification with an ID is present.
     *
     * @param id
     *      Notification ID
     * @param command
     *      The callback context used when calling back into JavaScript.
     */
    private void isPresent (int id, CallbackContext command) {
        boolean exist = getNotificationMgr().exist(id);

        PluginResult result = new PluginResult(
                PluginResult.Status.OK, exist);

        command.sendPluginResult(result);
    }

    /**
     * If a notification with an ID is scheduled.
     *
     * @param id
     *      Notification ID
     * @param command
     *      The callback context used when calling back into JavaScript.
     */
    private void isScheduled (int id, CallbackContext command) {
        boolean exist = getNotificationMgr().exist(
                id, Notification.Type.SCHEDULED);

        PluginResult result = new PluginResult(
                PluginResult.Status.OK, exist);

        command.sendPluginResult(result);
    }

    /**
     * If a notification with an ID is triggered.
     *
     * @param id
     *      Notification ID
     * @param command
     *      The callback context used when calling back into JavaScript.
     */
    private void isTriggered (int id, CallbackContext command) {
        boolean exist = getNotificationMgr().exist(
                id, Notification.Type.TRIGGERED);

        PluginResult result = new PluginResult(
                PluginResult.Status.OK, exist);

        command.sendPluginResult(result);
    }

    /**
     * Set of IDs from all existent notifications.
     *
     * @param command
     *      The callback context used when calling back into JavaScript.
     */
    private void getAllIds (CallbackContext command) {
        List<Integer> ids = getNotificationMgr().getIds();

        command.success(new JSONArray(ids));
    }

    /**
     * Set of IDs from all scheduled notifications.
     *
     * @param command
     *      The callback context used when calling back into JavaScript.
     */
    private void getScheduledIds (CallbackContext command) {
        List<Integer> ids = getNotificationMgr().getIdsByType(
                Notification.Type.SCHEDULED);

        command.success(new JSONArray(ids));
    }

    /**
     * Set of IDs from all triggered notifications.
     *
     * @param command
     *      The callback context used when calling back into JavaScript.
     */
    private void getTriggeredIds (CallbackContext command) {
        List<Integer> ids = getNotificationMgr().getIdsByType(
                Notification.Type.TRIGGERED);

        command.success(new JSONArray(ids));
    }

    /**
     * Options from local notification.
     *
     * @param ids
     *      Set of local notification IDs
     * @param command
     *      The callback context used when calling back into JavaScript.
     */
    private void getSingle (JSONArray ids, CallbackContext command) {
        getOptions(ids.optString(0), Notification.Type.ALL, command);
    }

    /**
     * Options from scheduled notification.
     *
     * @param ids
     *      Set of local notification IDs
     * @param command
     *      The callback context used when calling back into JavaScript.
     */
    private void getSingleScheduled (JSONArray ids, CallbackContext command) {
        getOptions(ids.optString(0), Notification.Type.SCHEDULED, command);
    }

    /**
     * Options from triggered notification.
     *
     * @param ids
     *      Set of local notification IDs
     * @param command
     *      The callback context used when calling back into JavaScript.
     */
    private void getSingleTriggered (JSONArray ids, CallbackContext command) {
        getOptions(ids.optString(0), Notification.Type.TRIGGERED, command);
    }

    /**
     * Set of options from local notification.
     *
     * @param ids
     *      Set of local notification IDs
     * @param command
     *      The callback context used when calling back into JavaScript.
     */
    private void getAll (JSONArray ids, CallbackContext command) {
        getOptions(ids, Notification.Type.ALL, command);
    }

    /**
     * Set of options from scheduled notifications.
     *
     * @param ids
     *      Set of local notification IDs
     * @param command
     *      The callback context used when calling back into JavaScript.
     */
    private void getScheduled (JSONArray ids, CallbackContext command) {
        getOptions(ids, Notification.Type.SCHEDULED, command);
    }

    /**
     * Set of options from triggered notifications.
     *
     * @param ids
     *      Set of local notification IDs
     * @param command
     *      The callback context used when calling back into JavaScript.
     */
    private void getTriggered (JSONArray ids, CallbackContext command) {
        getOptions(ids, Notification.Type.TRIGGERED, command);
    }

    /**
     * Options from local notification.
     *
     * @param id
     *      Set of local notification IDs
     * @param type
     *      The local notification life cycle type
     * @param command
     *      The callback context used when calling back into JavaScript.
     */
    private void getOptions (String id, Notification.Type type,
                             CallbackContext command) {

        JSONArray ids = new JSONArray().put(id);

        JSONObject options =
                getNotificationMgr().getOptionsBy(type, toList(ids)).get(0);

        command.success(options);
    }

    /**
     * Set of options from local notifications.
     *
     * @param ids
     *      Set of local notification IDs
     * @param type
     *      The local notification life cycle type
     * @param command
     *      The callback context used when calling back into JavaScript.
     */
    private void getOptions (JSONArray ids, Notification.Type type,
                             CallbackContext command) {

        List<JSONObject> options;

        if (ids.length() == 0) {
            options = getNotificationMgr().getOptionsByType(type);
        } else {
            options = getNotificationMgr().getOptionsBy(type, toList(ids));
        }

        command.success(new JSONArray(options));
    }

    /**
     * Call all pending callbacks after the deviceready event has been fired.
     */
    private static synchronized void deviceready () {
        isInBackground = false;
        deviceready = true;

        for (String js : eventQueue) {
            sendJavascript(js);
        }

        eventQueue.clear();
    }

    /**
     * Fire given event on JS side. Does inform all event listeners.
     *
     * @param event
     *      The event name
     */
    private void fireEvent (String event) {
        Log.d("localNotification","fireEvent - instance method");
        fireEvent(event, null);
    }

    /**
     * Fire given event on JS side. Does inform all event listeners.
     *
     * @param event
     *      The event name
     * @param notification
     *      Optional local notification to pass the id and properties.
     */
    

    static void fireEvent (String event, Notification notification) {
        Log.d("localNotification","fireEvent - static regular method");
        String state = getApplicationState();
        String params = "\"" + state + "\"";

        if (notification != null) {
            params = notification.toString() + "," + params;
        }

        String js = "cordova.plugins.notification.local.core.fireEvent(" +
                "\"" + event + "\"," + params + ")";

        //sendJavascript(js);
        sendJavascriptToAllWebViews(js);
    }

    private static void registerWebView(CordovaWebView webView) {
        getWebViewReference(webView);
    }
    private static WebViewReference getWebViewReference(CordovaWebView webView) {
        WebViewReference webViewReference = findWebViewReference(webView);
        if (webViewReference == null) {
            webViewReference = createWebViewReference(webView);
        }
        return webViewReference;
    }
    private static WebViewReference findWebViewReference(CordovaWebView webView) {
        WebViewReference webViewReference = null;
        for (WebViewReference item : mWebViewReferences) {
            if (item.getWebView() == webView) {
                webViewReference = item;
                break;
            }
            /*if (item.toString() == webView.toString()) {
                webViewReference = item;
                break;
            }*/
        }
        return webViewReference;
    }
    private static WebViewReference createWebViewReference(CordovaWebView webView) {
        String url = webView.getUrl();
        Log.d("localNotification","createWebViewReference - creating view reference - "+ url);
        Log.d("localNotification","createWebViewReference - webView.toString() - "+ webView.toString());
        WebViewReference webViewReference = new WebViewReference(webView);
        mWebViewReferences.add(webViewReference);
        return webViewReference;
    }

    private static void sendJavascriptToAllWebViews(final String js) {
        Log.d("localNotification","sendJavascriptToAllWebViews - WebView list size - "+ mWebViewReferences.size());
        for (WebViewReference webViewReference : mWebViewReferences) {
            Log.d("localNotification","sendJavascriptToAllWebViews - Running sendJavascript on webview - "+ webViewReference.toString());
            sendJavascript(js, webViewReference.getWebView());
        }
    }

    /**
     * Use this instead of deprecated sendJavascript
     *
     * @param js
     *       JS code snippet as string
     */
    private static synchronized void sendJavascript(final String js, final CordovaWebView wv) {
        Log.d("localNotification","sendJavascript - regular method");
        if (!deviceready) {
            Log.d("localNotification","sendJavascript - device not ready");
            eventQueue.add(js);
            return;
        }
        if(wv != null){
            Runnable jsLoader = new Runnable() {
                public void run() {
                    wv.loadUrl("javascript:" + js);
                }
            };
            ///String webUrl = webView.getUrl();
            
            try {
                //Method post = webView.getClass().getMethod("post",Runnable.class);
                Method post = ((View)wv).getClass().getMethod("post",Runnable.class);
                Log.d("localNotification","sendJavascript(ori) - post available ");
                Log.d("localNotification","sendJavascript(ori) - js "+ js);
                post.invoke(wv,jsLoader);
                Log.d("localNotification","sendJavascript(ori) - post invoked ");

                //Activity wvContext =  (Activity) webView.getContext();
            } catch(Exception e) {
                //throw e;
                Log.e("localNotification","sendJavascript(ori) - post not available, thrown exception "+ e);
                //((Activity)(webView.getContext())).runOnUiThread(jsLoader);
                Activity cActivity =  (Activity) cordova.getActivity();
                if(cActivity != null){
                    Log.d("localNotification","sendJavascript(ori) - cActivity => is not null ");
                    cActivity.runOnUiThread(jsLoader);
                }
               
                ///LocalNotification.cordova.getActivity().runOnUiThread(jsLoader);
            }            
        }else{
            Log.e("localNotification","sendJavascript(ori) - static webView is null");
        }

    }

    private static synchronized void sendJavascript(final String js) {
        Log.d("localNotification","sendJavascript - regular method");
        if (!deviceready) {
            Log.d("localNotification","sendJavascript - device not ready");
            eventQueue.add(js);
            return;
        }
        if(webView != null){
            Runnable jsLoader = new Runnable() {
                public void run() {
                    webView.loadUrl("javascript:" + js);
                }
            };
            ///String webUrl = webView.getUrl();
            try{
                Activity cActivity =  (Activity) cordova.getActivity();
                if(cActivity == null){
                    Log.d("localNotification","sendJavascript(ori) - cActivity => is null ");
                }else{
                    Log.d("localNotification","sendJavascript(ori) - cActivity => is not null ");
                    WebView currFocus = (WebView)cActivity.getCurrentFocus();
                    String wvUrl =  currFocus.getUrl();

                    ViewGroup viewgroup = (ViewGroup)((View)currFocus);
                    if(viewgroup != null){
                        Log.d("localNotification","sendJavascript(ori) - viewgroup => viewgroup.getChildCount() " + viewgroup.getChildCount());
                    }

                    ArrayList<View> views = ((View)currFocus).getFocusables(1);
                    if(views != null){
                        Log.d("localNotification","sendJavascript(ori) - ((View)currFocus).getFocusables(1) views.size() : " + views.size());
                    }

                    Log.d("localNotification","sendJavascript(ori) - currFocus url "+ wvUrl);
                    cActivity.runOnUiThread(jsLoader);
                }
                /*ViewGroup viewgroup = (ViewGroup)((View)webView);
                if(viewgroup == null){
                    Log.d("localNotification","sendJavascript(ori) - viewgroup => is null ");
                }
                Log.d("localNotification","sendJavascript(ori) - viewgroup => viewgroup.getChildCount() " + viewgroup.getChildCount());*/

                /*if(cordova == null){
                    Log.e("localNotification","sendJavascript(ori) - cordova is null ");
                }
                Activity cActivity =  (Activity) cordova.getActivity();

                Log.d("localNotification","sendJavascript(ori) - got activity");
                WebView wv = (WebView)cActivity.findViewById(1);
                Log.d("localNotification","sendJavascript(ori) - findViewById");
                if(wv != null){
                    Log.d("localNotification","sendJavascript(ori) - wv is not null");
                    String wvUrl =  wv.getUrl();
                    Log.d("localNotification","initializing - "+ wvUrl);
                }else{
                    Log.d("localNotification","sendJavascript(ori) - wv is null");
                }*/

                /*if(cActivity != null){
                    Log.d("localNotification","sendJavascript(ori) - cActivity is not null");
                    cActivity.runOnUiThread(jsLoader);
                }else{
                    Log.d("localNotification","sendJavascript(ori) - cActivity is null");
                }*/
            }catch(Exception e){
                Log.e("localNotification","sendJavascript(ori) - try getview, thrown exception ");
            }

            try {
                //Method post = webView.getClass().getMethod("post",Runnable.class);
                Method post = ((View)webView).getClass().getMethod("post",Runnable.class);
                Log.d("localNotification","sendJavascript(ori) - post available ");
                Log.d("localNotification","sendJavascript(ori) - js "+ js);
                post.invoke(webView,jsLoader);
                Log.d("localNotification","sendJavascript(ori) - post invoked ");

                //Activity wvContext =  (Activity) webView.getContext();
            } catch(Exception e) {
                //throw e;
                Log.e("localNotification","sendJavascript(ori) - post not available, thrown exception "+ e);
                //((Activity)(webView.getContext())).runOnUiThread(jsLoader);
                Activity cActivity =  (Activity) cordova.getActivity();
                if(cActivity != null){
                    Log.d("localNotification","sendJavascript(ori) - cActivity => is not null ");
                    cActivity.runOnUiThread(jsLoader);
                }
                /*Activity wvContext =  (Activity) webView.getContext();
                if(wvContext != null){
                    Log.d("localNotification","sendJavascript(ori) - webView.getContext() is not null");
                    wvContext.runOnUiThread(jsLoader);
                }else{
                    Log.d("localNotification","sendJavascript(ori) - webView.getContext() is null");
                }*/
                ///LocalNotification.cordova.getActivity().runOnUiThread(jsLoader);
            }            
        }else{
            Log.e("localNotification","sendJavascript(ori) - static webView is null");
        }

    }

    /**
     * Convert JSON array of integers to List.
     *
     * @param ary
     *      Array of integers
     */
    private List<Integer> toList (JSONArray ary) {
        ArrayList<Integer> list = new ArrayList<Integer>();

        for (int i = 0; i < ary.length(); i++) {
            list.add(ary.optInt(i));
        }

        return list;
    }

    /**
     * Current application state.
     *
     * @return
     *      "background" or "foreground"
     */
    static String getApplicationState () {
        return isInBackground ? "background" : "foreground";
    }

    /**
     * Notification manager instance.
     */
    private Manager getNotificationMgr() {
        return Manager.getInstance(cordova.getActivity());
    }

    
    public static class WebViewReference {

        private CordovaWebView mWebView;


        public WebViewReference(CordovaWebView webView) {
            mWebView = webView;
        }

        public void destroy() {
            mWebView = null;
        }

        public CordovaWebView getWebView() {
            return mWebView;
        }

        public String getUrl(){
            String url = "";
            if (getWebView() != null) {
                url = getWebView().getUrl();
            }
            return url;
        }

        @Override
        public String toString() {
            String webViewStr = "empty";
            if (getWebView() != null) {
                webViewStr = getWebView().toString();
            }
            return webViewStr;
        }

    }
}


