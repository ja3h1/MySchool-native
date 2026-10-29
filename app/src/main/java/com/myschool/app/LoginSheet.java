package com.myschool.app;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

public class LoginSheet extends BottomSheetDialogFragment {
    static final String LOGIN_URL = "https://myschool.carsu.edu.ph/login";
    static final String LOGIN_PATH = "/login";
    static final String TOKEN_KEY = "auth._token.local";
    static final String API_URL = "https://csu-api.carsu.edu.ph";

    // shortcut: generic selectors, replace with the real ids if the form has several inputs
    static final String SEL_USER = "input[type=text], input[type=email], input:not([type])";
    static final String SEL_PASS = "input[type=password]";

    static final int POLL_MS = 500;
    static final int REVEAL_AFTER_MS = 6000;   // show the WebView if Turnstile hasn't passed by then
    static final int GIVE_UP_MS = 60000;       // stop waiting for a human to solve it
    static final int RESULT_MS = 10000;        // still on /login this long after submit = failed

    private final Handler h = new Handler(Looper.getMainLooper());
    private WebView web;
    private View overlay;
    private TextView status;
    private MaterialButton btn;
    private TextInputEditText etUser, etPass;
    private boolean done, revealed;
    private int elapsed;

    private int dp(Context c, int n) {
        return (int) (n * c.getResources().getDisplayMetrics().density);
    }

    private static String q(String s) { return JSONObject.quote(s); }

    @Override
    public View onCreateView(@NonNull LayoutInflater inf, ViewGroup parent, Bundle state) {
        Context c = requireContext();

        // WebView sits at full size under an opaque form. Not GONE / alpha 0: Turnstile needs a real, attached view.
        web = new WebView(c);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public void doUpdateVisitedHistory(WebView v, String url, boolean reload) { check(url); }
            @Override public void onPageFinished(WebView v, String url) {
                if (url != null && url.contains(LOGIN_PATH) && !done) {
                    btn.setEnabled(true);
                    if (!revealed) status.setText("");
                }
            }
        });
        web.loadUrl(LOGIN_URL);

        View handle = new View(c);
        handle.setBackgroundColor(0xFF888888);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(c, 40), dp(c, 4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.setMargins(0, dp(c, 12), 0, dp(c, 12));

        etUser = new TextInputEditText(c);
        etUser.setSingleLine(true);
        etPass = new TextInputEditText(c);
        etPass.setSingleLine(true);
        etPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        TextInputLayout tilUser = field(c, "Student ID / username", etUser, false);
        TextInputLayout tilPass = field(c, "Password", etPass, true);

        btn = new MaterialButton(c);
        btn.setText("Sign in");
        btn.setEnabled(false);
        btn.setOnClickListener(v -> start());

        status = new TextView(c);
        status.setText("Checking saved session…");
        status.setGravity(Gravity.CENTER);

        LinearLayout form = new LinearLayout(c);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(c, 24), 0, dp(c, 24), dp(c, 24));
        form.addView(tilUser);
        form.addView(tilPass);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(c, 16);
        form.addView(btn, bp);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(c, 12);
        form.addView(status, sp);

        overlay = new LinearLayout(c);
        ((LinearLayout) overlay).setOrientation(LinearLayout.VERTICAL);
        overlay.setBackgroundColor(MaterialColors.getColor(c, com.google.android.material.R.attr.colorSurface, 0xFFFFFFFF));
        overlay.setClickable(true); // swallow touches so they never reach the WebView underneath
        ((LinearLayout) overlay).addView(form);

        FrameLayout stack = new FrameLayout(c);
        stack.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        stack.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (c.getResources().getDisplayMetrics().heightPixels * 0.9)));
        box.addView(handle, hp);
        box.addView(stack, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return box;
    }

    private TextInputLayout field(Context c, String hint, TextInputEditText et, boolean toggle) {
        TextInputLayout til = new TextInputLayout(c, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        til.setHint(hint);
        if (toggle) til.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        til.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 12);
        til.setLayoutParams(lp);
        return til;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog d = getDialog();
        if (d instanceof BottomSheetDialog) {
            View sheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            BottomSheetBehavior<View> b = BottomSheetBehavior.from(sheet);
            b.setSkipCollapsed(true);
            b.setState(BottomSheetBehavior.STATE_EXPANDED);
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    // ---- sign-in flow ----

    private void start() {
        String u = String.valueOf(etUser.getText()).trim(), p = String.valueOf(etPass.getText());
        if (u.isEmpty() || p.isEmpty()) { status.setText("Enter your ID and password."); return; }
        btn.setEnabled(false);
        status.setText("Signing in…");
        elapsed = 0;
        String js = "(function(u,p){"
                + "var s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;"
                + "function set(e,v){s.call(e,v);e.dispatchEvent(new Event('input',{bubbles:true}));"
                + "e.dispatchEvent(new Event('change',{bubbles:true}));}"
                + "var a=document.querySelector(" + q(SEL_USER) + "),b=document.querySelector(" + q(SEL_PASS) + ");"
                + "if(!a||!b)return 'nofields';set(a,u);set(b,p);return 'ok';})(" + q(u) + "," + q(p) + ")";
        web.evaluateJavascript(js, r -> {
            if (r == null || !r.contains("ok")) {
                reveal("Couldn't find the login form. Sign in here instead.");
                return;
            }
            h.postDelayed(poll, POLL_MS);
        });
    }

    // Wait for the Turnstile token, then submit. A human gets the WebView if it takes too long.
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (done || !isAdded() || web == null) return;
            elapsed += POLL_MS;
            web.evaluateJavascript(
                    "(function(){var t=document.querySelector('input[name=\"cf-turnstile-response\"]');"
                            + "return t?t.value:'__none__';})()", v -> {
                        if (done || !isAdded()) return;
                        String t = v == null ? "" : v.replace("\"", "");
                        boolean token = !t.isEmpty() && !t.equals("__none__");
                        boolean noWidget = t.equals("__none__") && elapsed >= 2500;
                        if (token || noWidget) { submit(); return; }
                        if (elapsed >= REVEAL_AFTER_MS && !revealed)
                            reveal("Verification needs you. Tap the checkbox below.");
                        if (elapsed >= GIVE_UP_MS) { reveal("Timed out. Close this and try again."); return; }
                        h.postDelayed(poll, POLL_MS);
                    });
        }
    };

    private void submit() {
        String js = "(function(){var b=document.querySelector('button[type=submit]');"
                + "if(!b){var l=document.querySelectorAll('button');for(var i=0;i<l.length;i++){"
                + "if(/log\\s?in|sign\\s?in/i.test(l[i].innerText)){b=l[i];break;}}}"
                + "if(b){b.click();return 'ok';}"
                + "var p=document.querySelector(" + q(SEL_PASS) + "),f=p&&p.closest('form');"
                + "if(f&&f.requestSubmit){f.requestSubmit();return 'ok';}return 'nobtn';})()";
        web.evaluateJavascript(js, r -> {
            if (r == null || !r.contains("ok")) { reveal("Couldn't press Sign in. Do it here."); return; }
            // success is caught by check() via the URL change; this only handles "still on /login"
            h.postDelayed(() -> {
                if (!done && isAdded()) reveal("Login didn't finish. Check your details or finish it here.");
            }, RESULT_MS);
        });
    }

    private void reveal(String msg) {
        if (!isAdded()) return;
        revealed = true;
        status.setText(msg);
        btn.setEnabled(true);
        overlay.animate().alpha(0f).setDuration(200).withEndAction(() -> overlay.setVisibility(View.GONE));
    }

    private void check(String url) {
        if (done || !isAdded() || url == null || url.contains(LOGIN_PATH)) return;
        web.evaluateJavascript("localStorage.getItem('" + TOKEN_KEY + "')", v -> {
            if (done || !isAdded() || v == null) return;
            String token = v.replaceAll("[\"\\\\]", "");
            if (!token.startsWith("Bearer ")) return;
            done = true;
            h.removeCallbacksAndMessages(null);
            String cookies = CookieManager.getInstance().getCookie(API_URL);
            ((MainActivity) requireActivity()).onLoggedIn(token, cookies);
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        h.removeCallbacksAndMessages(null);
        if (web != null) web.destroy();
        web = null;
        super.onDestroyView();
    }
}