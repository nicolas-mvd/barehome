package uy.nico.barehome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Windowless dispatch only; the bounded private receiver owns the operation and reply. */
public final class DogModeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            Intent intent = getIntent();
            if (state == null && intent != null && Intent.ACTION_VIEW.equals(intent.getAction())
                    && DogModeWire.parse(intent.getDataString()) != null) {
                sendBroadcast(new Intent(this, DogModeReceiver.class).setData(intent.getData()));
            }
        } finally {
            finish();
        }
    }
}
