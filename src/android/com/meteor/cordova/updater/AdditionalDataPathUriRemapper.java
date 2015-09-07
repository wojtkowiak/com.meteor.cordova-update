package com.meteor.cordova.updater;

import android.net.Uri;
import java.io.File;

/**
 * UriRemapper that checks if the url starts with the additional data url prefix and then tries to
 * find the file in the additional data root that is set by the meteor app.
 */
public class AdditionalDataPathUriRemapper extends FilesystemUriRemapper implements UriRemapper {
    private static final String TAG = "meteor.cordova.updater";

    public AdditionalDataPathUriRemapper(File base) {
        super(base);
    }

    @Override
    public Remapped remapUri(Uri uri) {
        String path = uri.getPath();
        String pathPrefix = CordovaUpdatePlugin.getAdditionalDataUrlPrefix();

        if (path.substring(0, pathPrefix.length()).equals(pathPrefix)) {
            return super.remapUri(new Uri.Builder().path(path.substring(pathPrefix.length())).build());
        }
        return null;
    }
}
