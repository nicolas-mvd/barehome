package uy.nico.barehome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** A text grid. Drawing reads cached geometry and strings and schedules no further frames. */
final class LauncherView extends View {
    interface Listener {
        void launch(MainActivity.App app);
        void refresh();
        void toggleMode();
        void editMode();
        boolean saveMode(boolean night, int backlight, int brightness);
        void openUpdates();
    }

    private static final String HINT = "D-PAD move   OK open   MENU refresh";
    private static final String MODE_HINT = "OK switch Day / Night   HOLD OK edit mode";
    private static final String SETTINGS_HINT = "OK open settings   HOLD OK check updates";
    private static final String UPDATE_HINT = "OK update BareHome";
    private static final String EDIT_HINT = "UP / DOWN select   LEFT / RIGHT adjust   OK confirm   BACK cancel";
    private static final int HEADER_NONE = -1;
    private static final int HEADER_UPDATE = 0;
    private static final int HEADER_MODE = 1;
    private static final int HEADER_SETTINGS = 2;
    private static final int HEADER_COUNT = 3;
    private static final int NO_TOUCH = Integer.MIN_VALUE;
    private static final int EDIT_BACKLIGHT = 0;
    private static final int EDIT_BRIGHTNESS = 1;
    private static final int EDIT_SAVE = 2;
    private static final int EDIT_CANCEL = 3;
    private static final int ACTION_DECREASE = 0x00ba0001;
    private static final int ACTION_INCREASE = 0x00ba0002;
    private final Listener listener;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint();
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
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
    private final float modeWidth;
    private final float touchSlop;
    private final int holdTimeout = ViewConfiguration.getLongPressTimeout();
    private final Runnable selectionHold = new Runnable() {
        @Override public void run() {
            if ((activationKey != KeyEvent.KEYCODE_UNKNOWN || touched != NO_TOUCH)
                    && isLongPressTarget() && hasWindowFocus()) performLongClick();
        }
    };

    private MainActivity.App[] apps = new MainActivity.App[0];
    private String[] labels = new String[0];
    private String positionLabel = "0 apps";
    private boolean night;
    private boolean updateAvailable;
    private int selected;
    private int headerFocused = HEADER_NONE;
    private int firstRow;
    private int visibleRows = 1;
    private int columns = Config.COLUMNS;
    private float cellWidth;
    private float settingsLeft;
    private float modeLeft;
    private float updateLeft;
    private int activationKey = KeyEvent.KEYCODE_UNKNOWN;
    private long activationDownTime;
    private int touched = NO_TOUCH;
    private float touchX;
    private float touchY;
    private boolean editorOpen;
    private boolean editorNight;
    private int editorRow;
    private int editorBacklight;
    private int editorBrightness;
    private String editorBacklightLabel = "";
    private String editorBrightnessLabel = "";
    private float editorLeft;
    private float editorRight;
    private float editorTop;
    private float editorRowsTop;

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
        modeWidth = Math.max(154 * density, text.measureText("Night") + 56 * density);
        small.setTypeface(Typeface.MONOSPACE);
        small.setTextSize(12 * scaledDensity);
        small.setColor(Config.MUTED);
        icon.setStyle(Paint.Style.STROKE);
        icon.setStrokeWidth(1.5f * density);
        Paint.FontMetrics metrics = text.getFontMetrics();
        textOffset = -(metrics.ascent + metrics.descent) / 2;
        Paint.FontMetrics smallMetrics = small.getFontMetrics();
        smallOffset = -(smallMetrics.ascent + smallMetrics.descent) / 2;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
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

    void setMode(boolean night) {
        if (this.night == night) return;
        this.night = night;
        if (isModeSelected()) describeSelection();
        invalidate();
    }

    void setUpdateAvailable(boolean available) {
        if (updateAvailable == available) return;
        updateAvailable = available;
        if (!available && headerFocused == HEADER_UPDATE) {
            cancelInput();
            headerFocused = HEADER_MODE;
            describeSelection();
        }
        invalidate();
    }

    void openModeEditor(boolean night, int backlight, int brightness) {
        cancelInput();
        editorNight = night;
        editorBacklight = Math.max(5, Math.min(100, backlight));
        editorBrightness = Math.max(0, Math.min(100, brightness));
        updateEditorLabels();
        editorRow = EDIT_BACKLIGHT;
        editorOpen = true;
        describeSelection();
        invalidate();
    }

    void closeModeEditor() {
        cancelInput();
        if (!editorOpen) return;
        editorOpen = false;
        headerFocused = HEADER_MODE;
        describeSelection();
        invalidate();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        columns = Math.max(1, Math.min(Config.COLUMNS,
                (int) ((width - 2 * margin + gap) / (220 * density + gap))));
        cellWidth = (width - 2 * margin - (columns - 1) * gap) / columns;
        visibleRows = Math.max(1, (int) ((height - margin - 36 * density - gridTop) / rowHeight));
        settingsLeft = width - margin - settingsWidth;
        modeLeft = settingsLeft - 20 * density - modeWidth;
        updateLeft = modeLeft - 20 * density - headerHeight;
        float editorWidth = Math.min(width - 2 * margin, 680 * density);
        editorLeft = (width - editorWidth) / 2;
        editorRight = editorLeft + editorWidth;
        editorTop = Math.max(margin, (height - headerHeight - 24 * density - 4 * rowHeight) / 2);
        editorRowsTop = editorTop + headerHeight + 24 * density;
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
        if (editorOpen) {
            drawEditor(canvas);
            return;
        }
        float centerY = margin + headerHeight / 2;
        if (margin + small.measureText("barehome") + 24 * density <= (updateAvailable ? updateLeft : modeLeft)) {
            canvas.drawText("barehome", margin, centerY + smallOffset, small);
        }
        if (updateAvailable) drawUpdateButton(canvas, centerY);
        boolean modeFocused = headerFocused == HEADER_MODE;
        int modeBackground = modeFocused ? Config.FOREGROUND : Config.BACKGROUND;
        int modeForeground = modeFocused ? Config.BACKGROUND : Config.FOREGROUND;
        if (modeFocused) {
            fill.setColor(modeBackground);
            canvas.drawRect(modeLeft, margin, modeLeft + modeWidth, margin + headerHeight, fill);
        }
        drawModeIcon(canvas, modeLeft + 19 * density, centerY, night, modeForeground, modeBackground);
        text.setColor(modeForeground);
        canvas.drawText(night ? "Night" : "Day", modeLeft + 40 * density, centerY + textOffset, text);
        if (headerFocused == HEADER_SETTINGS) {
            fill.setColor(Config.FOREGROUND);
            canvas.drawRect(settingsLeft, margin, getWidth() - margin, margin + headerHeight, fill);
        }
        text.setColor(headerFocused == HEADER_SETTINGS ? Config.BACKGROUND : Config.FOREGROUND);
        canvas.drawText("Settings", settingsLeft + padding, centerY + textOffset, text);

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
        String hint = isModeSelected() ? MODE_HINT : headerFocused == HEADER_SETTINGS
                ? SETTINGS_HINT : headerFocused == HEADER_UPDATE ? UPDATE_HINT : HINT;
        canvas.drawText(hint, margin, footer, small);
        small.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(positionLabel, getWidth() - margin, footer, small);
        small.setTextAlign(Paint.Align.LEFT);
    }

    private void drawUpdateButton(Canvas canvas, float centerY) {
        boolean focused = headerFocused == HEADER_UPDATE;
        if (focused) {
            fill.setColor(Config.FOREGROUND);
            canvas.drawRect(updateLeft, margin, updateLeft + headerHeight, margin + headerHeight, fill);
        }
        icon.setColor(focused ? Config.BACKGROUND : Config.FOREGROUND);
        float x = updateLeft + headerHeight / 2;
        canvas.drawLine(x, centerY - 10 * density, x, centerY + 5 * density, icon);
        canvas.drawLine(x - 6 * density, centerY - density, x, centerY + 5 * density, icon);
        canvas.drawLine(x + 6 * density, centerY - density, x, centerY + 5 * density, icon);
        canvas.drawLine(x - 9 * density, centerY + 10 * density, x + 9 * density, centerY + 10 * density, icon);
        canvas.drawLine(x - 9 * density, centerY + 6 * density, x - 9 * density, centerY + 10 * density, icon);
        canvas.drawLine(x + 9 * density, centerY + 6 * density, x + 9 * density, centerY + 10 * density, icon);
    }

    private void drawModeIcon(Canvas canvas, float x, float y, boolean night, int foreground, int background) {
        if (night) {
            fill.setColor(foreground);
            canvas.drawCircle(x, y, 8 * density, fill);
            fill.setColor(background);
            canvas.drawCircle(x + 4 * density, y - 3 * density, 7 * density, fill);
            return;
        }
        icon.setColor(foreground);
        float ray = 9 * density;
        float inner = 6 * density;
        float diagonal = 6.5f * density;
        float diagonalInner = 4.5f * density;
        canvas.drawCircle(x, y, 3.5f * density, icon);
        canvas.drawLine(x - ray, y, x - inner, y, icon);
        canvas.drawLine(x + inner, y, x + ray, y, icon);
        canvas.drawLine(x, y - ray, x, y - inner, icon);
        canvas.drawLine(x, y + inner, x, y + ray, icon);
        canvas.drawLine(x - diagonal, y - diagonal, x - diagonalInner, y - diagonalInner, icon);
        canvas.drawLine(x + diagonalInner, y - diagonalInner, x + diagonal, y - diagonal, icon);
        canvas.drawLine(x - diagonal, y + diagonal, x - diagonalInner, y + diagonalInner, icon);
        canvas.drawLine(x + diagonalInner, y + diagonalInner, x + diagonal, y + diagonal, icon);
    }

    private void drawEditor(Canvas canvas) {
        float titleY = editorTop + headerHeight / 2;
        drawModeIcon(canvas, editorLeft + 19 * density, titleY, editorNight,
                Config.FOREGROUND, Config.BACKGROUND);
        text.setColor(Config.FOREGROUND);
        canvas.drawText(editorNight ? "Night" : "Day", editorLeft + 40 * density, titleY + textOffset, text);
        for (int row = EDIT_BACKLIGHT; row <= EDIT_CANCEL; row++) {
            float top = editorRowsTop + row * rowHeight;
            boolean focused = editorRow == row;
            if (focused) {
                fill.setColor(Config.FOREGROUND);
                canvas.drawRect(editorLeft, top + 4 * density, editorRight, top + rowHeight - 4 * density, fill);
            }
            text.setColor(focused ? Config.BACKGROUND : Config.FOREGROUND);
            float baseline = top + rowHeight / 2 + textOffset;
            String label = row == EDIT_BACKLIGHT ? "Backlight" : row == EDIT_BRIGHTNESS
                    ? "Picture brightness" : row == EDIT_SAVE ? "Save" : "Cancel";
            canvas.drawText(label, editorLeft + padding, baseline, text);
            if (row <= EDIT_BRIGHTNESS) {
                text.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("−", editorRight - 160 * density, baseline, text);
                canvas.drawText(row == EDIT_BACKLIGHT ? editorBacklightLabel : editorBrightnessLabel,
                        editorRight - 96 * density, baseline, text);
                canvas.drawText("+", editorRight - 32 * density, baseline, text);
                text.setTextAlign(Paint.Align.LEFT);
            }
        }
        canvas.drawText(EDIT_HINT, margin, getHeight() - margin + smallOffset, small);
    }

    private void ensureVisible() {
        int row = selected / columns;
        if (row < firstRow) firstRow = row;
        if (row >= firstRow + visibleRows) firstRow = row - visibleRows + 1;
        int rowCount = (apps.length + columns - 1) / columns;
        firstRow = Math.max(0, Math.min(firstRow, rowCount - visibleRows));
    }

    private boolean move(int key) {
        if (editorOpen) {
            if (key == KeyEvent.KEYCODE_DPAD_UP) editorRow = Math.max(EDIT_BACKLIGHT, editorRow - 1);
            else if (key == KeyEvent.KEYCODE_DPAD_DOWN) editorRow = Math.min(EDIT_CANCEL, editorRow + 1);
            else if (key == KeyEvent.KEYCODE_DPAD_LEFT) adjustEditor(-1);
            else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) adjustEditor(1);
            describeSelection();
            invalidate();
            return true;
        }
        int previous = selected;
        int previousHeader = headerFocused;
        if (headerFocused != HEADER_NONE) {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN && apps.length > 0) headerFocused = HEADER_NONE;
            else if (key == KeyEvent.KEYCODE_DPAD_LEFT) headerFocused = Math.max(firstHeader(), headerFocused - 1);
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

    private void adjustEditor(int direction) {
        if (editorRow == EDIT_BACKLIGHT) editorBacklight = Math.max(5, Math.min(100, editorBacklight + direction));
        else if (editorRow == EDIT_BRIGHTNESS) editorBrightness = Math.max(0, Math.min(100, editorBrightness + direction));
        updateEditorLabels();
    }

    private void updateEditorLabels() {
        editorBacklightLabel = editorBacklight + "%";
        editorBrightnessLabel = Integer.toString(editorBrightness);
    }

    private boolean isModeSelected() {
        return !editorOpen && headerFocused == HEADER_MODE;
    }

    private boolean isLongPressTarget() {
        return !editorOpen && (headerFocused == HEADER_MODE || headerFocused == HEADER_SETTINGS);
    }

    private int firstHeader() { return updateAvailable ? HEADER_UPDATE : HEADER_MODE; }

    private void describeSelection() {
        String description;
        if (editorOpen) {
            String mode = editorNight ? "Night" : "Day";
            if (editorRow == EDIT_BACKLIGHT) description = mode + " backlight, " + editorBacklight
                    + " percent, left and right to adjust, 5 to 100";
            else if (editorRow == EDIT_BRIGHTNESS) description = mode + " picture brightness, " + editorBrightness
                    + ", left and right to adjust, 0 to 100";
            else description = editorRow == EDIT_SAVE ? "Save and apply " + mode + " levels" : "Cancel edits";
        } else if (headerFocused == HEADER_MODE) {
            description = (night ? "Night" : "Day") + " mode selected. Click to switch to "
                    + (night ? "Day" : "Night") + ". Hold OK to edit selected mode.";
        } else if (headerFocused == HEADER_UPDATE) {
            description = "BareHome update available. Click to review and install.";
        } else if (headerFocused == HEADER_SETTINGS) {
            description = "Settings. Click to open TV settings. Hold OK to check BareHome updates.";
        } else {
            description = headerFocused == HEADER_SETTINGS || apps.length == 0 ? "Settings" : apps[selected].label;
        }
        positionLabel = apps.length == 0 ? "0 apps" : (selected + 1) + " / " + apps.length;
        setContentDescription(description);
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    }

    private static boolean isSelectKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private void cancelInput() {
        removeCallbacks(selectionHold);
        activationKey = KeyEvent.KEYCODE_UNKNOWN;
        touched = NO_TOUCH;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                cancelInput();
                return move(keyCode);
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
                if (event.getRepeatCount() == 0) {
                    cancelInput();
                    activationKey = keyCode;
                    activationDownTime = event.getDownTime();
                    if (isSelectKey(keyCode) && isLongPressTarget()) postDelayed(selectionHold, holdTimeout);
                } else if (activationKey == keyCode && isSelectKey(keyCode) && isLongPressTarget()
                        && (event.isLongPress() || event.getEventTime() - activationDownTime >= holdTimeout)) {
                    performLongClick();
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                cancelInput();
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
                boolean heldSelection = activate && isSelectKey(keyCode) && isLongPressTarget()
                        && event.getEventTime() - activationDownTime >= holdTimeout;
                cancelInput();
                if (heldSelection) performLongClick();
                else if (activate) {
                    if (keyCode == KeyEvent.KEYCODE_MENU) {
                        if (!editorOpen) listener.refresh();
                    } else if (keyCode == KeyEvent.KEYCODE_SETTINGS) {
                        if (!editorOpen) listener.launch(null);
                    } else performClick();
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (!event.isCanceled() && editorOpen) closeModeEditor();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) cancelInput();
    }

    @Override protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        if (!focused) cancelInput();
    }

    @Override protected void onDetachedFromWindow() {
        cancelInput();
        super.onDetachedFromWindow();
    }

    @Override public boolean performClick() {
        super.performClick();
        if (editorOpen) {
            if (editorRow == EDIT_SAVE && listener.saveMode(editorNight, editorBacklight, editorBrightness)) {
                closeModeEditor();
            } else if (editorRow == EDIT_CANCEL) closeModeEditor();
        } else if (headerFocused == HEADER_MODE) {
            listener.toggleMode();
        } else if (headerFocused == HEADER_UPDATE && updateAvailable) {
            listener.openUpdates();
        } else {
            listener.launch(headerFocused == HEADER_SETTINGS || apps.length == 0 ? null : apps[selected]);
        }
        return true;
    }

    @Override public boolean performLongClick() {
        if (!isLongPressTarget()) return false;
        boolean edit = isModeSelected();
        cancelInput();
        if (edit) listener.editMode();
        else listener.openUpdates();
        return true;
    }

    private int itemAt(float x, float y) {
        if (editorOpen) {
            if (x < editorLeft || x > editorRight || y < editorRowsTop
                    || y >= editorRowsTop + 4 * rowHeight) return NO_TOUCH;
            return (int) ((y - editorRowsTop) / rowHeight);
        }
        if (y >= margin && y <= margin + headerHeight) {
            if (x >= settingsLeft && x <= getWidth() - margin) return HEADER_SETTINGS - HEADER_COUNT;
            if (x >= modeLeft && x <= modeLeft + modeWidth) return HEADER_MODE - HEADER_COUNT;
            if (updateAvailable && x >= updateLeft && x <= updateLeft + headerHeight) return HEADER_UPDATE - HEADER_COUNT;
        }
        if (x < margin || x >= getWidth() - margin || y < gridTop
                || y >= gridTop + visibleRows * rowHeight) return NO_TOUCH;
        int column = (int) ((x - margin) / (cellWidth + gap));
        if ((x - margin) % (cellWidth + gap) >= cellWidth) return NO_TOUCH;
        int index = (firstRow + (int) ((y - gridTop) / rowHeight)) * columns + column;
        return column < columns && index < apps.length ? index : NO_TOUCH;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            cancelInput();
            touched = itemAt(event.getX(), event.getY());
            if (touched == NO_TOUCH) return false;
            touchX = event.getX();
            touchY = event.getY();
            if (editorOpen) editorRow = touched;
            else {
                headerFocused = touched < 0 ? touched + HEADER_COUNT : HEADER_NONE;
                if (headerFocused == HEADER_NONE) selected = touched;
                if (isLongPressTarget()) postDelayed(selectionHold, holdTimeout);
            }
            describeSelection();
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (Math.abs(event.getX() - touchX) > touchSlop || Math.abs(event.getY() - touchY) > touchSlop) cancelInput();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            boolean activate = touched != NO_TOUCH && touched == itemAt(event.getX(), event.getY());
            cancelInput();
            if (activate) {
                if (editorOpen && editorRow <= EDIT_BRIGHTNESS) {
                    if (event.getX() >= editorRight - 64 * density) adjustEditor(1);
                    else if (event.getX() >= editorRight - 192 * density
                            && event.getX() <= editorRight - 128 * density) adjustEditor(-1);
                    describeSelection();
                    invalidate();
                } else performClick();
            }
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL
                || event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) cancelInput();
        return true;
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(editorOpen && editorRow <= EDIT_BRIGHTNESS
                ? "android.widget.SeekBar" : "android.widget.Button");
        info.setScrollable(true);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        info.setLongClickable(isLongPressTarget());
        if (isLongPressTarget()) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK);
        if (editorOpen && editorRow <= EDIT_BRIGHTNESS) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(ACTION_DECREASE, "Decrease level"));
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(ACTION_INCREASE, "Increase level"));
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,
                    editorRow == EDIT_BACKLIGHT ? 5 : 0, 100,
                    editorRow == EDIT_BACKLIGHT ? editorBacklight : editorBrightness));
        }
    }

    @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_LONG_CLICK) return performLongClick();
        if (editorOpen && editorRow <= EDIT_BRIGHTNESS) {
            if (action == ACTION_DECREASE || action == ACTION_INCREASE) {
                adjustEditor(action == ACTION_DECREASE ? -1 : 1);
                describeSelection();
                invalidate();
                return true;
            }
            if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId() && arguments != null
                    && arguments.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)) {
                float progress = arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE);
                if (Float.isNaN(progress) || Float.isInfinite(progress)) return false;
                int value = Math.round(progress);
                if (editorRow == EDIT_BACKLIGHT) editorBacklight = Math.max(5, Math.min(100, value));
                else editorBrightness = Math.max(0, Math.min(100, value));
                updateEditorLabels();
                describeSelection();
                invalidate();
                return true;
            }
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            cancelInput();
            boolean forward = action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
            if (editorOpen) {
                editorRow = Math.max(EDIT_BACKLIGHT, Math.min(EDIT_CANCEL, editorRow + (forward ? 1 : -1)));
            } else if (headerFocused != HEADER_NONE) {
                if (forward && headerFocused < HEADER_SETTINGS) headerFocused++;
                else if (!forward && headerFocused > firstHeader()) headerFocused--;
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
