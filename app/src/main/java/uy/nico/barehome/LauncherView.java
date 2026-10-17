package uy.nico.barehome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** A text grid. Drawing reads cached geometry and strings and schedules no further frames. */
final class LauncherView extends View {
    interface Listener {
        void launch(MainActivity.App app);
        void refresh();
        void adjustBrightness(int direction);
    }

    private static final String HINT = "D-PAD move   OK open   MENU refresh";
    private static final int HEADER_NONE = -1;
    private static final int HEADER_MINUS = 0;
    private static final int HEADER_PLUS = 1;
    private static final int HEADER_SETTINGS = 2;
    private final Listener listener;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint();
    private final Paint sun = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final float margin;
    private final float rowHeight;
    private final float gap;
    private final float padding;
    private final float textOffset;
    private final float smallOffset;
    private final float headerHeight;
    private final float gridTop;
    private final float settingsWidth;

    private MainActivity.App[] apps = new MainActivity.App[0];
    private String[] labels = new String[0];
    private String positionLabel = "0 apps";
    private String brightnessLabel = "—";
    private int brightness = -1;
    private int selected;
    private int headerFocused = HEADER_NONE;
    private int firstRow;
    private int visibleRows = 1;
    private int columns = Config.COLUMNS;
    private float cellWidth;
    private float settingsLeft;
    private float minusLeft;
    private float plusLeft;
    private int activationKey = KeyEvent.KEYCODE_UNKNOWN;
    private int touched = Integer.MIN_VALUE;

    LauncherView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        density = getResources().getDisplayMetrics().density;
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        margin = Config.MARGIN_DP * density;
        rowHeight = Config.ROW_HEIGHT_DP * density;
        gap = Config.COLUMN_GAP_DP * density;
        padding = 16 * density;
        headerHeight = 44 * density;
        gridTop = margin + headerHeight + 24 * density;
        settingsWidth = 154 * density;
        text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        text.setTextSize(Config.TEXT_SIZE_SP * scaledDensity);
        small.setTypeface(Typeface.MONOSPACE);
        small.setTextSize(12 * scaledDensity);
        small.setColor(Config.MUTED);
        sun.setColor(Config.MUTED);
        sun.setStyle(Paint.Style.STROKE);
        sun.setStrokeWidth(1.5f * density);
        Paint.FontMetrics metrics = text.getFontMetrics();
        textOffset = -(metrics.ascent + metrics.descent) / 2;
        Paint.FontMetrics smallMetrics = small.getFontMetrics();
        smallOffset = -(smallMetrics.ascent + smallMetrics.descent) / 2;
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setSoundEffectsEnabled(false);
        setHapticFeedbackEnabled(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void setApps(MainActivity.App[] updated, String component) {
        apps = updated;
        selected = Math.min(selected, Math.max(0, apps.length - 1));
        for (int i = 0; i < apps.length; i++) {
            if (apps[i].key().equals(component)) {
                selected = i;
                break;
            }
        }
        if (apps.length == 0 && headerFocused == HEADER_NONE) headerFocused = HEADER_SETTINGS;
        layoutLabels();
        ensureVisible();
        describeSelection();
        invalidate();
    }

    String selectedComponent() {
        return apps.length == 0 ? "" : apps[selected].key();
    }

    void setBrightness(int percent) {
        percent = percent < 0 ? -1 : Math.min(100, percent);
        if (brightness == percent) return;
        brightness = percent;
        brightnessLabel = percent < 0 ? "—" : percent + "%";
        if (headerFocused == HEADER_MINUS || headerFocused == HEADER_PLUS) describeSelection();
        invalidate();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        columns = Math.max(1, Math.min(Config.COLUMNS,
                (int) ((width - 2 * margin + gap) / (220 * density + gap))));
        cellWidth = (width - 2 * margin - (columns - 1) * gap) / columns;
        visibleRows = Math.max(1, (int) ((height - margin - 36 * density - gridTop) / rowHeight));
        settingsLeft = width - margin - settingsWidth;
        plusLeft = settingsLeft - 20 * density - headerHeight;
        minusLeft = plusLeft - 52 * density - headerHeight;
        layoutLabels();
        ensureVisible();
    }

    private void layoutLabels() {
        labels = new String[apps.length];
        float available = Math.max(0, cellWidth - 2 * padding);
        float ellipsisWidth = text.measureText("…");
        for (int i = 0; i < apps.length; i++) {
            String label = apps[i].label;
            if (text.measureText(label) <= available) {
                labels[i] = label;
            } else {
                int end = text.breakText(label, true, Math.max(0, available - ellipsisWidth), null);
                if (end > 0 && Character.isHighSurrogate(label.charAt(end - 1))) end--;
                labels[i] = label.substring(0, end) + "…";
            }
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Config.BACKGROUND);
        canvas.drawText("barehome", margin, margin + headerHeight / 2 + smallOffset, small);
        float centerY = margin + headerHeight / 2;
        float sunX = minusLeft - 20 * density;
        float ray = 9 * density;
        float inner = 6 * density;
        float diagonal = 6.5f * density;
        float diagonalInner = 4.5f * density;
        canvas.drawCircle(sunX, centerY, 3.5f * density, sun);
        canvas.drawLine(sunX - ray, centerY, sunX - inner, centerY, sun);
        canvas.drawLine(sunX + inner, centerY, sunX + ray, centerY, sun);
        canvas.drawLine(sunX, centerY - ray, sunX, centerY - inner, sun);
        canvas.drawLine(sunX, centerY + inner, sunX, centerY + ray, sun);
        canvas.drawLine(sunX - diagonal, centerY - diagonal,
                sunX - diagonalInner, centerY - diagonalInner, sun);
        canvas.drawLine(sunX + diagonalInner, centerY - diagonalInner,
                sunX + diagonal, centerY - diagonal, sun);
        canvas.drawLine(sunX - diagonal, centerY + diagonal,
                sunX - diagonalInner, centerY + diagonalInner, sun);
        canvas.drawLine(sunX + diagonalInner, centerY + diagonalInner,
                sunX + diagonal, centerY + diagonal, sun);
        drawBrightnessButton(canvas, minusLeft, HEADER_MINUS, "−");
        drawBrightnessButton(canvas, plusLeft, HEADER_PLUS, "+");
        small.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(brightnessLabel, minusLeft + headerHeight + 26 * density,
                centerY + smallOffset, small);
        small.setTextAlign(Paint.Align.LEFT);
        if (headerFocused == HEADER_SETTINGS) {
            fill.setColor(Config.FOREGROUND);
            canvas.drawRect(settingsLeft, margin, getWidth() - margin, margin + headerHeight, fill);
        }
        text.setColor(headerFocused == HEADER_SETTINGS ? Config.BACKGROUND : Config.FOREGROUND);
        canvas.drawText("Settings", settingsLeft + padding,
                margin + headerHeight / 2 + textOffset, text);

        int first = firstRow * columns;
        int last = Math.min(apps.length, first + visibleRows * columns);
        for (int i = first; i < last; i++) {
            float left = margin + (i % columns) * (cellWidth + gap);
            float top = gridTop + (i / columns - firstRow) * rowHeight;
            boolean focused = headerFocused == HEADER_NONE && i == selected;
            if (focused) {
                fill.setColor(Config.FOREGROUND);
                canvas.drawRect(left, top + 4 * density,
                        left + cellWidth, top + rowHeight - 4 * density, fill);
            }
            text.setColor(focused ? Config.BACKGROUND : Config.FOREGROUND);
            canvas.drawText(labels[i], left + padding, top + rowHeight / 2 + textOffset, text);
        }
        if (apps.length == 0) {
            text.setColor(Config.FOREGROUND);
            canvas.drawText("No apps", margin + padding, gridTop + rowHeight / 2 + textOffset, text);
        }
        float footer = getHeight() - margin + smallOffset;
        canvas.drawText(HINT, margin, footer, small);
        small.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(positionLabel, getWidth() - margin, footer, small);
        small.setTextAlign(Paint.Align.LEFT);
    }

    private void drawBrightnessButton(Canvas canvas, float left, int button, String label) {
        boolean focused = headerFocused == button;
        if (focused) {
            fill.setColor(Config.FOREGROUND);
            canvas.drawRect(left, margin, left + headerHeight, margin + headerHeight, fill);
        }
        text.setColor(focused ? Config.BACKGROUND : Config.FOREGROUND);
        text.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(label, left + headerHeight / 2, margin + headerHeight / 2 + textOffset, text);
        text.setTextAlign(Paint.Align.LEFT);
    }

    private void ensureVisible() {
        int row = selected / columns;
        if (row < firstRow) firstRow = row;
        if (row >= firstRow + visibleRows) firstRow = row - visibleRows + 1;
        int rowCount = (apps.length + columns - 1) / columns;
        firstRow = Math.max(0, Math.min(firstRow, rowCount - visibleRows));
    }

    private boolean move(int key) {
        int previous = selected;
        int previousHeader = headerFocused;
        if (headerFocused != HEADER_NONE) {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN && apps.length > 0) headerFocused = HEADER_NONE;
            else if (key == KeyEvent.KEYCODE_DPAD_LEFT) headerFocused = Math.max(HEADER_MINUS, headerFocused - 1);
            else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) headerFocused = Math.min(HEADER_SETTINGS, headerFocused + 1);
        } else if (apps.length > 0) {
            switch (key) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (selected < columns) headerFocused = HEADER_SETTINGS;
                    else selected -= columns;
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (selected / columns < (apps.length - 1) / columns) {
                        selected = Math.min(apps.length - 1, selected + columns);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (selected % columns != 0) selected--;
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (selected % columns < columns - 1 && selected + 1 < apps.length) selected++;
                    break;
                default: return false;
            }
        }
        if (selected != previous || headerFocused != previousHeader) {
            ensureVisible();
            describeSelection();
            invalidate();
        }
        return true;
    }

    private void describeSelection() {
        String description;
        if (headerFocused == HEADER_MINUS || headerFocused == HEADER_PLUS) {
            description = headerFocused == HEADER_MINUS ? "Decrease backlight" : "Increase backlight";
            if (brightness >= 0) description += ", " + brightness + " percent";
        } else {
            description = headerFocused == HEADER_SETTINGS || apps.length == 0 ? "Settings" : apps[selected].label;
        }
        positionLabel = apps.length == 0 ? "0 apps" : (selected + 1) + " / " + apps.length;
        setContentDescription(description);
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                activationKey = KeyEvent.KEYCODE_UNKNOWN;
                return move(keyCode);
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
                if (event.getRepeatCount() == 0) activationKey = keyCode;
                return true;
            case KeyEvent.KEYCODE_BACK:
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
                boolean activate = activationKey == keyCode && !event.isCanceled();
                activationKey = KeyEvent.KEYCODE_UNKNOWN;
                if (activate) {
                    if (keyCode == KeyEvent.KEYCODE_MENU) listener.refresh();
                    else if (keyCode == KeyEvent.KEYCODE_SETTINGS) listener.launch(null);
                    else performClick();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_BACK:
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) activationKey = KeyEvent.KEYCODE_UNKNOWN;
    }

    @Override public boolean performClick() {
        super.performClick();
        if (headerFocused == HEADER_MINUS || headerFocused == HEADER_PLUS) {
            listener.adjustBrightness(headerFocused == HEADER_MINUS ? -1 : 1);
        } else {
            listener.launch(headerFocused == HEADER_SETTINGS || apps.length == 0 ? null : apps[selected]);
        }
        return true;
    }

    private int itemAt(float x, float y) {
        if (y >= margin && y <= margin + headerHeight) {
            if (x >= settingsLeft && x <= getWidth() - margin) return HEADER_SETTINGS - 3;
            if (x >= plusLeft && x <= plusLeft + headerHeight) return HEADER_PLUS - 3;
            if (x >= minusLeft && x <= minusLeft + headerHeight) return HEADER_MINUS - 3;
        }
        if (x < margin || x >= getWidth() - margin || y < gridTop
                || y >= gridTop + visibleRows * rowHeight) return Integer.MIN_VALUE;
        int column = (int) ((x - margin) / (cellWidth + gap));
        if ((x - margin) % (cellWidth + gap) >= cellWidth) return Integer.MIN_VALUE;
        int index = (firstRow + (int) ((y - gridTop) / rowHeight)) * columns + column;
        return column < columns && index < apps.length ? index : Integer.MIN_VALUE;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            touched = itemAt(event.getX(), event.getY());
            if (touched == Integer.MIN_VALUE) return false;
            headerFocused = touched < 0 ? touched + 3 : HEADER_NONE;
            if (headerFocused == HEADER_NONE) selected = touched;
            describeSelection();
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            boolean activate = touched != Integer.MIN_VALUE && touched == itemAt(event.getX(), event.getY());
            touched = Integer.MIN_VALUE;
            if (activate) performClick();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) touched = Integer.MIN_VALUE;
        return true;
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Button");
        info.setScrollable(true);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }

    @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            boolean forward = action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
            if (headerFocused != HEADER_NONE) {
                if (forward && headerFocused < HEADER_SETTINGS) headerFocused++;
                else if (!forward && headerFocused > HEADER_MINUS) headerFocused--;
                else if (forward && apps.length > 0) {
                    headerFocused = HEADER_NONE;
                    selected = 0;
                }
            } else if (!forward && selected == 0) {
                headerFocused = HEADER_SETTINGS;
            } else if (apps.length > 0) {
                selected = Math.max(0, Math.min(apps.length - 1, selected + (forward ? 1 : -1)));
            }
            ensureVisible();
            describeSelection();
            invalidate();
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }
}
