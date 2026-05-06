package ru.zograr.weblauncher;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String siteUrl = getString(R.string.site_url);

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(siteUrl));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть сайт", Toast.LENGTH_LONG).show();
        }

        finish();
    }
}
