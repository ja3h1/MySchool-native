package com.myschool.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.util.Collections;

public class LoginController {
    static final String LOGIN_URL = "https://myschool.carsu.edu.ph/login";
    static final String LOGIN_PATH = "/login";
    static final String TOKEN_KEY = "auth._token.local";
    static final String API_URL = "https://csu-api.carsu.edu.ph";

    static final String SEL_USER = "input[type=text], input[type=email], input:not([type])";
    static final String SEL_PASS = "input[type=password]";

    static final int POLL_MS = 500;
    static final int GIVE_UP_MS = 60000;
    static final int RESULT_MS = 10000;

    private final MainActivity activity;
    private final Handler h = new Handler(Looper.getMainLooper());
    private WebView web;
    private View layoutLogin;
    private TextView status;
    private MaterialButton btn;
    private TextInputEditText etUser, etPass;
    private boolean done, revealed;
    private int elapsed;

    public LoginController(MainActivity activity, MaterialButton btnLogin) {
        this.activity = activity;
        this.btn = btnLogin;
        Context c = activity;

        etUser = activity.findViewById(R.id.etUser);
        etPass = activity.findViewById(R.id.etPass);
        status = activity.findViewById(R.id.tvLoginStatus);

        if (btn != null) {
            btn.setText("Sign in");
            btn.setEnabled(false);
            btn.setOnClickListener(v -> start());
        }

        layoutLogin = activity.findViewById(R.id.layoutLogin);

        // Park WebView at the back of the screen
        web = new WebView(c);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);

        String ua = web.getSettings().getUserAgentString()
                .replace("; wv", "")
                .replaceAll("Version/\\d+\\.\\d+ ", "");
        web.getSettings().setUserAgentString(ua);

        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(
                    web.getSettings(), Collections.emptySet());
        }

        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override public void doUpdateVisitedHistory(WebView v, String url, boolean reload) { check(url); }
            @Override public void onPageStarted(WebView v, String url, android.graphics.Bitmap favicon) { check(url); }
            @Override public void onPageFinished(WebView v, String url) {
                check(url);
                if (url != null && url.contains(LOGIN_PATH) && !done) {
                    if (btn != null) btn.setEnabled(true);
                    if (!revealed && status != null) status.setText("");
                    activity.onLoginReady();
                }
            }
        });

        ViewGroup root = (ViewGroup) activity.findViewById(android.R.id.content);
        if (root != null) {
            root.addView(web, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        web.loadUrl(LOGIN_URL);
    }

    private static String q(String s) { return JSONObject.quote(s); }

    private void sendWebToBack() {
        if (web == null) return;
        ViewGroup p = (ViewGroup) web.getParent();
        if (p != null) {
            p.removeView(web);
            p.addView(web, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    public void restart() {
        done = false;
        revealed = false;
        elapsed = 0;
        h.removeCallbacksAndMessages(null);
        if (btn != null) btn.setEnabled(false);
        if (status != null) status.setText("Checking saved session…");
        if (layoutLogin != null) {
            layoutLogin.animate().cancel();
            layoutLogin.setAlpha(1f);
            layoutLogin.setVisibility(View.VISIBLE);
        }
        if (web != null) {
            sendWebToBack();
            web.setVisibility(View.VISIBLE);
            web.loadUrl(LOGIN_URL);
        }
    }

    private void start() {
        String u = etUser != null && etUser.getText() != null ? String.valueOf(etUser.getText()).trim() : "";
        String p = etPass != null && etPass.getText() != null ? String.valueOf(etPass.getText()) : "";
        if (u.isEmpty() || p.isEmpty()) {
            if (status != null) status.setText("Enter your ID and password.");
            return;
        }
        if (btn != null) btn.setEnabled(false);
        if (status != null) status.setText("Signing in…");
        elapsed = 0;
        String js = "(function(u,p){"
                + "var s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;"
                + "function set(e,v){s.call(e,v);e.dispatchEvent(new Event('input',{bubbles:true}));"
                + "e.dispatchEvent(new Event('change',{bubbles:true}));}"
                + "var a=document.querySelector(" + q(SEL_USER) + "),b=document.querySelector(" + q(SEL_PASS) + ");"
                + "if(!a||!b)return 'nofields';set(a,u);set(b,p);return 'ok';})(" + q(u) + "," + q(p) + ")";
        if (web != null) {
            web.evaluateJavascript(js, r -> {
                if (r == null || !r.contains("ok")) {
                    if (status != null) status.setText("Could not fill login form. Please try again.");
                    if (btn != null) btn.setEnabled(true);
                    return;
                }
                h.postDelayed(poll, POLL_MS);
            });
        }
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (done || web == null) return;
            elapsed += POLL_MS;
            web.evaluateJavascript(
                    "(function(){var t=document.querySelector('input[name=\"cf-turnstile-response\"]');"
                            + "return t?t.value:'__none__';})()", v -> {
                        if (done) return;
                        String t = v == null ? "" : v.replace("\"", "");
                        boolean token = !t.isEmpty() && !t.equals("__none__");
                        boolean noWidget = t.equals("__none__") && elapsed >= 2500;
                        if (token || noWidget) { submit(); return; }
                        if (elapsed >= GIVE_UP_MS) {
                            if (status != null) status.setText("Sign in timed out. Please check your credentials.");
                            if (btn != null) btn.setEnabled(true);
                            return;
                        }
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
        if (web != null) {
            web.evaluateJavascript(js, r -> {
                if (r == null || !r.contains("ok")) {
                    if (status != null) status.setText("Could not submit sign-in form.");
                    if (btn != null) btn.setEnabled(true);
                    return;
                }
                h.postDelayed(() -> {
                    if (!done) {
                        web.evaluateJavascript("(function(){"
                                + "var err = document.querySelector('.alert-danger, .error, [class*=\"error\"], [class*=\"danger\"]');"
                                + "return err ? err.innerText : 'none';"
                                + "})()", errVal -> {
                            if (!done) {
                                String errText = errVal != null ? errVal.replaceAll("[\"\\\\]", "").trim() : "";
                                if (!errText.isEmpty() && !errText.equals("none")) {
                                    if (status != null) status.setText(errText);
                                } else {
                                    if (status != null) status.setText("Sign in failed. Check your ID and password.");
                                }
                                if (btn != null) btn.setEnabled(true);
                            }
                        });
                    }
                }, RESULT_MS);
            });
        }
    }

    private void check(String url) {
        if (done || url == null || url.contains(LOGIN_PATH)) return;
        if (web == null) return;
        web.evaluateJavascript("localStorage.getItem('" + TOKEN_KEY + "')", v -> {
            if (done) return;
            String token = v != null ? v.replaceAll("[\"\\\\]", "") : "";
            if (token.startsWith("Bearer ")) {
                done = true;
                h.removeCallbacksAndMessages(null);
                web.stopLoading();
                web.setVisibility(View.GONE);
                String cookies = CookieManager.getInstance().getCookie(API_URL);
                activity.onLoggedIn(token, cookies);
                return;
            }
            web.evaluateJavascript("(function(){ return document.body ? document.body.innerText : ''; })()", resp -> {
                if (done || resp == null || resp.trim().isEmpty() || resp.equals("null")) return;
                try {
                    String cleanResp = resp.replaceAll("^\"|\"$", "").replace("\\\"", "\"").replace("\\\\", "\\");
                    if (!cleanResp.startsWith("{")) return;
                    JSONObject j = new JSONObject(cleanResp);
                    JSONObject d = j.optJSONObject("data") != null ? j.optJSONObject("data") : j;
                    String access = d.optString("accessToken", d.optString("access_token", d.optString("token", "")));
                    if (access.isEmpty()) {
                        StringBuilder keys = new StringBuilder();
                        java.util.Iterator<String> it = d.keys();
                        while (it.hasNext()) keys.append(it.next()).append(' ');
                        h.post(() -> { if (status != null) status.setText("No access token. Keys: " + keys); });
                        return;
                    }
                    String bearer = "Bearer " + access;
                    String cookies = CookieManager.getInstance().getCookie(API_URL);
                    h.post(() -> {
                        done = true;
                        web.stopLoading();
                        web.setVisibility(View.GONE);
                        activity.onLoggedIn(bearer, cookies);
                    });
                } catch (Exception ignored) {}
            });
        });
    }
}
