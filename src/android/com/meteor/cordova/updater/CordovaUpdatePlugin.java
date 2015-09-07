package com.meteor.cordova.updater;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;

import com.meteor.cordova.updater.UriRemapper.Remapped;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.util.Log;

public class CordovaUpdatePlugin extends CordovaPlugin {
    private static final String TAG = "meteor.cordova.updater";

    private static final String DEFAULT_HOST = "meteor.local";
    private static final String DEFAULT_PAGE = "index.html";

    final Set<String> hosts = new HashSet<String>();
    final Set<String> schemes = new HashSet<String>();
    private List<UriRemapper> remappers = new ArrayList<UriRemapper>();

    private String wwwRoot;
    private String cordovajsRoot;
    private String additionalDataRoot;
    private static String additionalDataUrlPrefix = "/data";

    Asset assetRoot;

    public CordovaUpdatePlugin() {
        this.hosts.add(DEFAULT_HOST);
        this.schemes.add("http");
        this.schemes.add("https");

    }

    /**
     * Just a getter for additionalDataUrlPrefix;
     *
     * @return String
     */
    public static String getAdditionalDataUrlPrefix() {
        return additionalDataUrlPrefix;
    }

    /**
     * Just a setter for additionalDataUrlPrefix;
     *
     * @return String
     */
    public static void setAdditionalDataUrlPrefix(String prefix) {
        additionalDataUrlPrefix = prefix;
    }

    /**
     * Overrides uri resolution.
     * 
     * Implements remapping, including adding default files (index.html) for directories
     *
     * @param uri
     * @return
     */
    @Override
    public Uri remapUri(Uri uri) {
        Log.v(TAG, "remapUri " + uri);

        String scheme = uri.getScheme();
        if (scheme == null || !schemes.contains(scheme)) {
            Log.d(TAG, "Scheme is not intercepted: " + scheme);
            return uri;
        }
        String host = uri.getHost();
        if (host == null || !hosts.contains(host)) {
            Log.d(TAG, "Host is not intercepted: " + host);
            return uri;
        }

        Remapped remapped = remap(uri);

        if (remapped == null) {
            // If e.g. /lists/doesnotexist is not found, we will try to serve /index.html
            // XXX: This needs a double-check, to make sure it works the same as ./packages/webapp/webapp_server.js
            // (if uri was /index.html, we will recheck it, but not a big deal)
            Uri defaultPage = uri.buildUpon().path(DEFAULT_PAGE).build();
            Log.d(TAG, "Unable to find " + uri + ", will try " + defaultPage);
            remapped = remap(defaultPage);
        }

        if (remapped != null) {
            if (remapped.isDirectory) {
                Log.d(TAG, "Found asset, but was directory: " + remapped.uri);
            } else {
                Log.d(TAG, "Remapping to " + remapped.uri);
                return remapped.uri;
            }
        }

        // Serve defaultPage if directory
        if (remapped != null && remapped.isDirectory) {
            Uri defaultPage = Uri.withAppendedPath(uri, DEFAULT_PAGE);

            remapped = remap(defaultPage);
            if (remapped != null) {
                if (remapped.isDirectory) {
                    Log.d(TAG, "Found asset, but was directory: " + remapped.uri);
                } else {
                    Log.d(TAG, "Remapping to " + remapped.uri);
                    return remapped.uri;
                }
            }
        }

        // No remapping; return unaltered
        Log.d(TAG, "No remapping for " + uri);
        return uri;
    }

    /**
     * Helper function that tries all the remappers, to find the first that can remap a Uri
     * 
     * @param uri
     * @return
     */
    private Remapped remap(Uri uri) {
        List<UriRemapper> remappers;
        synchronized (this) {
            remappers = this.remappers;
        }

        for (UriRemapper remapper : remappers) {
            Remapped remapped = remapper.remapUri(uri);
            if (remapped != null) {
                return remapped;
            }
        }
        return null;
    }

    private static final String ACTION_START_SERVER = "startServer";
    private static final String ACTION_SET_LOCAL_PATH = "setLocalPath";
    private static final String ACTION_GET_CORDOVAJSROOT = "getCordovajsRoot";
    private static final String ACTION_SET_ADDITIONAL_DATA_PATH = "setAdditionalDataPath";
    private static final String ACTION_SET_ADDITIONAL_DATA_URL_PREFIX = "setAdditionalDataUrlPrefix";
    private static final String ACTION_REGISTER_MIME_TYPE = "registerMimeType";

    /**
     * Entry-point for JS calls from Cordova
     */
    @Override
    public boolean execute(String action, JSONArray inputs, CallbackContext callbackContext) throws JSONException {
        try {
            if (ACTION_START_SERVER.equals(action)) {
                String wwwRoot = inputs.getString(0);
                String cordovaRoot = inputs.getString(1);

                String result = startServer(wwwRoot, cordovaRoot);

                callbackContext.success(result);

                return true;
            } else if (ACTION_SET_LOCAL_PATH.equals(action)) {
                String wwwRoot = inputs.getString(0);

                setLocalPath(wwwRoot);

                callbackContext.success();
                return true;
            } else if (ACTION_SET_ADDITIONAL_DATA_PATH.equals(action)) {
                String additionalDataRoot = inputs.getString(0);

                setAdditionalDataPath(additionalDataRoot, callbackContext);
                return true;
            } else if (ACTION_SET_ADDITIONAL_DATA_URL_PREFIX.equals(action)) {
                String additionalDataUrlPrefix = inputs.getString(0);

                setAdditionalDataUrlPrefix(additionalDataUrlPrefix, callbackContext);
                return true;
            } else if (ACTION_REGISTER_MIME_TYPE.equals(action)) {
                // not supported on Android so we will just return success
                callbackContext.success();
                return true;
            } else if (ACTION_GET_CORDOVAJSROOT.equals(action)) {
                String result = getCordovajsRoot();

                callbackContext.success(result);

                return true;
            } else {
                Log.w(TAG, "Invalid action passed: " + action);
                PluginResult result = new PluginResult(Status.INVALID_ACTION);
                callbackContext.sendPluginResult(result);
            }
        } catch (Exception e) {
            Log.w(TAG, "Caught exception during execution: " + e);
            String message = e.toString();
            callbackContext.error(message);
        }

        return true;
    }

    /**
     * JS-called function, called after a hot-code-push
     * 
     * @param wwwRoot
     */
    private void setLocalPath(String wwwRoot) {
        Log.w(TAG, "setLocalPath(" + wwwRoot + ")");

        this.updateLocations(wwwRoot, this.cordovajsRoot, this.additionalDataRoot);
    }

    /**
     * JS-called function, used for setting additional path for serving files stored by the app
     *
     * @param additionalDataRoot
     * @param callbackContext
     */
    private void setAdditionalDataPath(String additionalDataRoot, CallbackContext callbackContext) {
        Log.w(TAG, "setAdditionalDataRoot(" + additionalDataRoot + ")");

        File file = new File(additionalDataRoot);
        if(file.exists() && file.isDirectory()) {
            this.updateLocations(this.wwwRoot, this.cordovajsRoot, additionalDataRoot);
            callbackContext.success();
        } else {
            callbackContext.error("Provided path does not exists.");
        }
    }

    /**
     * JS-called function, sets up a url prefix that will be used for accessing files stored by the app
     *
     * @param additionalDataUrlPrefix
     * @param callbackContext
     */
    private void setAdditionalDataUrlPrefix(String additionalDataUrlPrefix, CallbackContext callbackContext) {
        Log.w(TAG, "setAdditionalDataUrlPrefix(" + additionalDataUrlPrefix + ")");

        Pattern alphanumeric = Pattern.compile("[^a-zA-Z0-9]");
        if (alphanumeric.matcher(additionalDataUrlPrefix).find() || additionalDataUrlPrefix.equals("plugins")) {
            callbackContext.error("Prefix must be alphanumerical and different from 'plugins'.");
        } else {
            CordovaUpdatePlugin.setAdditionalDataUrlPrefix("/" + additionalDataUrlPrefix);
            callbackContext.success();
        }
    }

    /**
     * Helper function that sets up the resolver ordering
     * 
     * @param wwwRoot
     * @param cordovajsRoot
     * @param additionalDataRoot
     */
    private void updateLocations(String wwwRoot, String cordovajsRoot, String additionalDataRoot) {
        synchronized (this) {
            UriRemapper appRemapper = null;

            File fsRoot, fsAdditionalDataRoot;
            // XXX: This is very iOS specific
            if (wwwRoot.startsWith("../../Documents/meteor")) {
                Context ctx = cordova.getActivity().getApplicationContext();
                fsRoot = new File(ctx.getApplicationInfo().dataDir, wwwRoot.substring(6));
            } else {
                fsRoot = new File(wwwRoot);
            }
            if (fsRoot.exists()) {
                appRemapper = new FilesystemUriRemapper(fsRoot);
            } else {
                Log.w(TAG, "Filesystem root not found; falling back to assets: " + wwwRoot);

                Asset wwwAsset = getAssetRoot().find(Utils.stripPrefix(wwwRoot, "/android_asset/www/"));
                if (wwwAsset == null) {
                    Log.w(TAG, "Could not find asset: " + wwwRoot + ", default to asset root");
                    wwwAsset = getAssetRoot();
                }
                appRemapper = new AssetUriRemapper(wwwAsset, true);
            }

            // XXX HACKHACK serve cordova.js from the containing folder
            Asset cordovaAssetBase = getAssetRoot().find(Utils.stripPrefix(cordovajsRoot, "/android_asset/www/"));
            if (cordovaAssetBase == null) {
                Log.w(TAG, "Could not find asset: " + cordovajsRoot + ", default to www root");
                cordovaAssetBase = getAssetRoot();
            }
            final AssetUriRemapper cordovaRemapper = new AssetUriRemapper(cordovaAssetBase, true);

            UriRemapper cordovaUriRemapper = new UriRemapper() {
                @Override
                public Remapped remapUri(Uri uri) {
                    String path = uri.getPath();

                    // if ([path isEqualToString:@"/cordova.js"] || [path isEqualToString:@"/cordova_plugins.js"] ||
                    // [path hasPrefix:@"/plugins/"])
                    // return [[METEORCordovajsRoot stringByAppendingPathComponent:path] stringByStandardizingPath];
                    if (path.equals("/cordova.js") || path.equals("/cordova_plugins.js")
                            || path.startsWith("/plugins/")) {
                        Log.v(TAG, "Detected cordova URI: " + uri);
                        Remapped remapped = cordovaRemapper.remapUri(uri);
                        if (remapped == null) {
                            Log.w(TAG, "Detected cordova URI, but resource remap failed: " + uri);
                        }
                        return remapped;
                    }

                    return null;
                }
            };

            List<UriRemapper> remappers = new ArrayList<UriRemapper>();
            remappers.add(cordovaUriRemapper);
            if (additionalDataRoot != null) {
                fsAdditionalDataRoot = new File(additionalDataRoot);
                if (fsAdditionalDataRoot.exists()) {
                    Log.w(TAG, "Added remapper for additional data root: " + fsAdditionalDataRoot.getAbsolutePath());
                    remappers.add(new AdditionalDataPathUriRemapper(fsAdditionalDataRoot));
                }
            }
            remappers.add(appRemapper);

            this.wwwRoot = wwwRoot;
            this.cordovajsRoot = cordovajsRoot;
            this.additionalDataRoot = additionalDataRoot;
            this.remappers = remappers;
        }
    }

    private Asset getAssetRoot() {
        if (this.assetRoot == null) {
            Context ctx = cordova.getActivity().getApplicationContext();
            AssetManager assetManager = ctx.getResources().getAssets();

            this.assetRoot = new Asset(assetManager, "www");

            // For debug
            // this.assetRoot.dump();
        }
        return this.assetRoot;
    }

    /**
     * JS-called function, that returns cordovajsRoot as set previously
     * 
     * @return
     */
    private String getCordovajsRoot() {
        Log.w(TAG, "getCordovajsRoot");

        return this.cordovajsRoot;
    }

    /**
     * JS-called function, that starts the url interception
     *
     * @param wwwRoot
     * @param cordovaRoot
     * @return
     * @throws JSONException
     */
    private String startServer(String wwwRoot, String cordovaRoot)
            throws JSONException {
        Log.w(TAG, "startServer(" + wwwRoot + "," + cordovaRoot + ")");

        this.updateLocations(wwwRoot, cordovaRoot, null);

        return "http://" + DEFAULT_HOST;
    }

}
