package com.meteor.cordova.updater;

import android.net.Uri;
import java.io.File;

/**
 * UriRemapper that checks if the url starts with the additional data url prefix and then tries to
 * find the find in relative to additional data root.
 * 
 * @author meteor
 * 
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
