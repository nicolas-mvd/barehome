package uy.nico.barehome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Explicit, revocable TV-side consent. Opening this page grants or enables nothing. */
public final class DogModeConsentActivity extends Activity {
    private TextView status;
    private Button allow, revoke;
    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        Intent entry = getIntent();
        if (entry == null || !DogModeSetup.allowed(entry.getAction(), entry.getDataString(),
                entry.getComponent() != null)) {
            finish();
            return;
        }
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(48), dp(32), dp(48), dp(32));
        body.setBackgroundColor(0xff202323);
        TextView title = text("Dog Mode helper", 28);
        title.setTypeface(null, Typeface.BOLD);
        body.addView(title);
        body.addView(text("Select CANAL 10 and keep NS NOW playing", 20));
        body.addView(text("Android accessibility access can read screen content and interact with apps. "
                + "BareHome uses this helper only in NS NOW while Dog Mode is starting or active. "
                + "It selects CANAL 10 and responds to NS NOW’s exact inactivity prompt. "
                + "It does not inspect other apps, save screen text or send periodic button presses.", 18));
        body.addView(text("Starting also saves the TV’s current energy settings, Developer options and USB debugging, "
                + "then turns the supported power-off timers and both debugging values off before opening NS NOW. "
                + "It changes only these two debugging values, without resetting other Developer options or USB authorizations.", 18));
        body.addView(text("Ending turns this helper off, restores the exact saved energy and debugging values, and opens Home. "
                + "Developer options and USB debugging will turn back on if they were on before Start. "
                + "You can end the mode and revoke this permission here at any time.", 18));
        status = text("", 18);
        status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        body.addView(status);
        allow = button("Allow for Dog Mode");
        allow.setOnClickListener(view -> {
            if (DogModeAccessibility.setConsent(this, true)) {
                status.setText("Allowed. Start Dog Mode from BareRemote. The helper stays off until you start.");
                refresh();
            } else status.setText("Permission was not saved. Nothing was enabled.");
        });
        body.addView(allow);
        revoke = button("End Dog Mode & revoke");
        revoke.setOnClickListener(view -> {
            allow.setEnabled(false); revoke.setEnabled(false);
            status.setText("Turning the helper off and restoring TV settings…");
            DogModeCoordinator.revokeConsent(this, restored -> {
                if (isFinishing() || isDestroyed()) return;
                refresh();
                status.setText(restored
                        ? "Permission revoked. The helper is off and saved TV settings are restored."
                        : "The TV could not confirm a complete restore. Use Restore settings & exit in BareRemote. "
                            + "Check this helper is off in the TV’s Accessibility settings.");
            });
        });
        body.addView(revoke);
        Button done = button("Done");
        done.setOnClickListener(view -> finish());
        body.addView(done);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        setContentView(scroll);
        refresh();
        status.setText(DogModeAccessibility.hasConsent(this)
                ? "Allowed for Dog Mode. You can end the mode and revoke access below."
                : "Not allowed. Choose Allow to use this helper only during Dog Mode.");
        allow.requestFocus();
    }
    private void refresh() {
        boolean allowed = DogModeAccessibility.hasConsent(this);
        allow.setEnabled(!allowed);
        revoke.setEnabled(allowed || NsNowDogService.isEnabled(this) || new DogModeController(this).generation() != null);
    }
    private TextView text(String value, int size) {
        TextView result = new TextView(this);
        result.setText(value); result.setTextSize(size); result.setTextColor(0xffe2e6e2);
        result.setPadding(0, 0, 0, dp(18));
        return result;
    }
    private Button button(String value) {
        Button result = new Button(this);
        result.setText(value); result.setAllCaps(false); result.setTextSize(18);
        result.setGravity(Gravity.CENTER); result.setMinHeight(dp(52));
        result.setContentDescription(value);
        return result;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
