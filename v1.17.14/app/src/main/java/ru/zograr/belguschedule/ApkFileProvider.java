package ru.zograr.belguschedule;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.Bundle;
import android.os.Environment;
import android.content.res.AssetFileDescriptor;
import android.provider.OpenableColumns;
import android.database.MatrixCursor;
import android.content.Context;

import java.io.File;
import java.io.FileNotFoundException;

public class ApkFileProvider extends ContentProvider {
    private static final String PREFS = "schedule_cache";
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

        File file = path == null || path.isEmpty() ? null : new File(path);
        if (file != null && file.exists() && file.length() > 0) {
            return file;
        }

        // Fallback на случай, если старая версия не сохранила путь или SharedPreferences были очищены.
        File found = findLatestApk(context);
        if (found != null && found.exists() && found.length() > 0) {
            prefs.edit().putString(KEY_UPDATE_FILE_PATH, found.getAbsolutePath()).apply();
            return found;
        }

        throw new FileNotFoundException("APK file not found");
    }

    private File findLatestApk(Context context) {
        File[] dirs = new File[] {
                new File(context.getFilesDir(), "updates"),
                new File(context.getCacheDir(), "updates"),
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) == null
                        ? null
                        : new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates")
        };

        File best = null;
        for (File dir : dirs) {
            if (dir == null || !dir.exists()) continue;

            File[] files = dir.listFiles();
            if (files == null) continue;

            for (File file : files) {
                String name = file.getName().toLowerCase();
                if (!name.endsWith(".apk") || file.length() <= 0) continue;

                if (best == null || file.lastModified() > best.lastModified()) {
                    best = file;
                }
            }
        }

        return best;
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
        File file = getApkFile();
        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(pfd, 0, file.length());
    }

    @Override
    public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts) throws FileNotFoundException {
        return openAssetFile(uri, "r");
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
