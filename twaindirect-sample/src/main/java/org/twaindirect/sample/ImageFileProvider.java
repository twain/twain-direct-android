package org.twaindirect.sample;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Image file provider to support the image list. When the user selects an image, we can't
 * just use the path to the image to launch an intent to view it because Android won't
 * let us; we have to supply it through a ContentProvider.
 */
public class ImageFileProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] strings, @Nullable String s, @Nullable String[] strings1, @Nullable String s1) {
        return null;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File path = new File(Environment.getExternalStorageDirectory(), getContext().getString(R.string.image_folder_name));
        File pdf = new File(path, uri.getLastPathSegment());
        return ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "application/pdf";
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues contentValues) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String s, @Nullable String[] strings) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues contentValues, @Nullable String s, @Nullable String[] strings) {
        return 0;
    }
}
