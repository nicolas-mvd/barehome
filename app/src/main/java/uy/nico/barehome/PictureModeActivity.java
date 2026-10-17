package uy.nico.barehome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.widget.Toast;

/** A short, windowless entry point; never starts or brings Home forward. */
public final class PictureModeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            Intent intent = getIntent();
            // Recreation must not repeat a command. Do not accept extras or partial URIs.
            if (state == null && intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
                String uri = intent.getDataString();
                Boolean night = PictureCommand.selectedNight(uri);
                if (night != null) new PictureController(this).select(night.booleanValue());
                else if (PictureCommand.opensInputs(uri)) openInputs();
            }
        } finally {
            // Theme.NoDisplay activities must finish before onResume completes.
            finish();
        }
    }

    private void openInputs() {
        Intent inputs = new Intent("com.android.tv.action.VIEW_INPUTS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ResolveInfo resolved = getPackageManager().resolveActivity(
                    inputs, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved == null || resolved.activityInfo == null
                    || !resolved.activityInfo.exported || !resolved.activityInfo.enabled
                    || !resolved.activityInfo.applicationInfo.enabled) {
                inputMessage("Source menu is unavailable on this TV");
                return;
            }
            startActivity(inputs);
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            inputMessage("This TV could not open its source menu");
        }
    }

    private void inputMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
