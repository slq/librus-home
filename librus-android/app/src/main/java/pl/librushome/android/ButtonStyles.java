package pl.librushome.android;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.widget.Button;

/** Flat controls with a real ripple, readable disabled state and 48 dp touch target. */
public final class ButtonStyles {
    static final int TEAL = 0xff087e8b;
    static int dp(Context c, int n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    public static Button make(Context c, String label, Runnable action, boolean accent) {
        Button b = new Button(c); b.setText(label); b.setAllCaps(false); b.setTextSize(13);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(dp(c,48)); b.setMinimumHeight(dp(c,48));
        b.setPadding(dp(c,16), dp(c,10), dp(c,16), dp(c,10)); b.setGravity(android.view.Gravity.CENTER);
        b.setStateListAnimator(null); b.setElevation(0); b.setBackgroundTintList(null);
        style(b, accent); b.setOnClickListener(v -> action.run()); return b;
    }
    public static void style(Button b, boolean accent) {
        Context c = b.getContext();
        int[][] states = {{-android.R.attr.state_enabled}, {}};
        b.setTextColor(new ColorStateList(states, new int[]{0xff8795a4, accent ? Color.WHITE : TEAL}));
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(accent ? TEAL : Color.WHITE); normal.setCornerRadius(dp(c,10));
        if (!accent) normal.setStroke(dp(c,1), 0xffd4e1e7);
        GradientDrawable disabled = new GradientDrawable(); disabled.setColor(0xffe7edf1); disabled.setCornerRadius(dp(c,10));
        StateListDrawable colors = new StateListDrawable(); colors.addState(states[0], disabled); colors.addState(states[1], normal);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(accent ? 0x33ffffff : 0x22087e8b), colors, null));
    }
}
