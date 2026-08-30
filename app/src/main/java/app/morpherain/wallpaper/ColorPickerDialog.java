package app.morpherain.wallpaper;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

final class ColorPickerDialog {
    interface OnColorPicked { void onPicked(int color); }

    private ColorPickerDialog() {}

    static void show(Context context, String titleText, int initialColor, OnColorPicked listener) {
        Dialog dialog = new Dialog(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 20), dp(context, 18), dp(context, 20), dp(context, 18));
        root.setBackgroundColor(Color.rgb(22, 22, 22));

        TextView title = new TextView(context);
        title.setText(titleText);
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View preview = new View(context);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 42));
        previewLp.setMargins(0, dp(context, 12), 0, dp(context, 12));
        root.addView(preview, previewLp);

        final int[] selected = {initialColor};
        setPreview(preview, initialColor);

        PickerView picker = new PickerView(context);
        picker.setColor(initialColor);
        LinearLayout.LayoutParams pickerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 310));
        root.addView(picker, pickerLp);

        LinearLayout hexRow = new LinearLayout(context);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hexRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hexRowLp.setMargins(0, dp(context, 10), 0, 0);
        root.addView(hexRow, hexRowLp);

        EditText hex = new EditText(context);
        hex.setSingleLine(true);
        hex.setTextColor(Color.WHITE);
        hex.setHintTextColor(Color.GRAY);
        hex.setHint("#RRGGBB");
        hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
        hex.setText(toHex(initialColor));
        LinearLayout.LayoutParams hexLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hexRow.addView(hex, hexLp);

        Button applyHex = new Button(context);
        applyHex.setText("Use hex");
        applyHex.setAllCaps(false);
        hexRow.addView(applyHex, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        picker.setListener(color -> {
            selected[0] = color;
            setPreview(preview, color);
            hex.setText(toHex(color));
            hex.setSelection(hex.length());
        });

        applyHex.setOnClickListener(v -> {
            Integer parsed = parseHex(hex.getText().toString());
            if (parsed == null) {
                Toast.makeText(context, "Enter a colour as #RRGGBB", Toast.LENGTH_SHORT).show();
                return;
            }
            selected[0] = parsed;
            picker.setColor(parsed);
            setPreview(preview, parsed);
            hex.setText(toHex(parsed));
            hex.setSelection(hex.length());
        });

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsLp.setMargins(0, dp(context, 12), 0, 0);
        root.addView(buttons, buttonsLp);

        Button cancel = new Button(context);
        cancel.setText("Cancel");
        cancel.setAllCaps(false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        buttons.addView(cancel);

        Button ok = new Button(context);
        ok.setText("Use colour");
        ok.setAllCaps(false);
        ok.setOnClickListener(v -> {
            listener.onPicked(selected[0]);
            dialog.dismiss();
        });
        buttons.addView(ok);

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.setOnShowListener(ignored -> {
            Window w = dialog.getWindow();
            if (w != null) w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        });
        dialog.show();
    }

    static String toHex(int color) {
        return String.format(Locale.ROOT, "#%02X%02X%02X",
                Color.red(color), Color.green(color), Color.blue(color));
    }

    private static Integer parseHex(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (!value.startsWith("#")) value = "#" + value;
        if (value.length() != 7) return null;
        try {
            return Color.parseColor(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void setPreview(View view, int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(view.getContext(), 10));
        bg.setStroke(dp(view.getContext(), 1), Color.argb(120, 255, 255, 255));
        view.setBackground(bg);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class PickerView extends View {
        interface Listener { void onColorChanged(int color); }

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint marker = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF svRect = new RectF();
        private final RectF hueRect = new RectF();
        private final float[] hsv = {180f, 1f, 1f};
        private Listener listener;
        private int activeArea;

        PickerView(Context context) {
            super(context);
            marker.setStyle(Paint.Style.STROKE);
            marker.setStrokeWidth(dp(context, 2));
            marker.setColor(Color.WHITE);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setListener(Listener listener) { this.listener = listener; }

        void setColor(int color) {
            Color.colorToHSV(color, hsv);
            invalidate();
        }

        private int color() { return Color.HSVToColor(hsv); }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int pad = dp(getContext(), 4);
            int gap = dp(getContext(), 18);
            int hueHeight = dp(getContext(), 32);
            svRect.set(pad, pad, getWidth() - pad, getHeight() - pad - gap - hueHeight);
            hueRect.set(pad, svRect.bottom + gap, getWidth() - pad, getHeight() - pad);

            int hueColor = Color.HSVToColor(new float[]{hsv[0], 1f, 1f});
            paint.setShader(new LinearGradient(svRect.left, 0, svRect.right, 0,
                    Color.WHITE, hueColor, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(svRect, dp(getContext(), 6), dp(getContext(), 6), paint);
            paint.setShader(new LinearGradient(0, svRect.top, 0, svRect.bottom,
                    Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(svRect, dp(getContext(), 6), dp(getContext(), 6), paint);

            int[] hueColors = {
                    Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
                    Color.BLUE, Color.MAGENTA, Color.RED
            };
            paint.setShader(new LinearGradient(hueRect.left, 0, hueRect.right, 0,
                    hueColors, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(hueRect, dp(getContext(), 6), dp(getContext(), 6), paint);
            paint.setShader(null);

            float sx = svRect.left + hsv[1] * svRect.width();
            float sy = svRect.top + (1f - hsv[2]) * svRect.height();
            marker.setColor(hsv[2] < 0.55f ? Color.WHITE : Color.BLACK);
            canvas.drawCircle(sx, sy, dp(getContext(), 8), marker);
            marker.setColor(Color.WHITE);
            float hx = hueRect.left + (hsv[0] / 360f) * hueRect.width();
            canvas.drawCircle(hx, hueRect.centerY(), dp(getContext(), 8), marker);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (svRect.contains(event.getX(), event.getY())) activeArea = 1;
                else if (hueRect.contains(event.getX(), event.getY())) activeArea = 2;
                else activeArea = 0;
            }
            if (activeArea == 0) return true;
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                if (activeArea == 1) {
                    hsv[1] = clamp((event.getX() - svRect.left) / svRect.width());
                    hsv[2] = 1f - clamp((event.getY() - svRect.top) / svRect.height());
                } else {
                    hsv[0] = clamp((event.getX() - hueRect.left) / hueRect.width()) * 359.999f;
                }
                invalidate();
                if (listener != null) listener.onColorChanged(color());
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                activeArea = 0;
                return true;
            }
            return true;
        }

        private float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
    }
}
