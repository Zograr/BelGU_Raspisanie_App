package ru.zograr.belguschedule;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.content.res.AssetFileDescriptor;
import android.provider.OpenableColumns;
import android.database.MatrixCursor;
import android.content.Context;

import java.io.File;
import java.io.FileNotFoundException;

public class ApkFileProvider extends ContentProvider {
    private static final String PREFS = "schedule_prefs";
    private static final String KEY_UPDATE_FILE_PATH = "update_file_path";

    @Override
    public boolean onCreate() {
        return true;
    }

    private File getApkFile() throws FileNotFoundException {
        Context context = getContext();
        if (context == null) throw new FileNotFoundException("Context is null");

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String path = prefs.getString(KEY_UPDATE_FILE_PATH, "");
        if (path == null || path.isEmpty()) {
            throw new FileNotFoundException("APK path is empty");
        }

        File file = new File(path);
        if (!file.exists() || file.length() <= 0) {
            throw new FileNotFoundException("APK file not found");
        }

        return file;
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = getApkFile();
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        ParcelFileDescriptor pfd = openFile(uri, mode);
        return new AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            File file = getApkFile();
            MatrixCursor cursor = new MatrixCursor(new String[] {
                    OpenableColumns.DISPLAY_NAME,
                    OpenableColumns.SIZE
            });
            cursor.addRow(new Object[] { file.getName(), file.length() });
            return cursor;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
