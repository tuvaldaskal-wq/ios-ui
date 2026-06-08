package com.aiassistant;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Sign in / sign up screen backed by Supabase Auth. */
public class AuthActivity extends Activity {

    private EditText email;
    private EditText password;
    private TextView error;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.app_bg);
        root.setGravity(Gravity.CENTER);
        root.setFitsSystemWindows(true);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));

        View orb = new View(this);
        orb.setBackgroundResource(R.drawable.orb);
        LinearLayout.LayoutParams orbLp = new LinearLayout.LayoutParams(dp(72), dp(72));
        orbLp.bottomMargin = dp(18);
        orb.setLayoutParams(orbLp);
        root.addView(orb);

        TextView title = new TextView(this);
        title.setText("Welcome to Aria");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26f);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Sign in so your plan is safe even if you reinstall.");
        subtitle.setTextColor(Color.parseColor("#99FFFFFF"));
        subtitle.setTextSize(14f);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(6), 0, dp(22));
        root.addView(subtitle);

        email = field("Email", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        root.addView(email, fieldParams());
        password = field("Password", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(password, fieldParams());

        error = new TextView(this);
        error.setTextColor(Color.parseColor("#FFB4B4"));
        error.setTextSize(13f);
        error.setGravity(Gravity.CENTER);
        error.setPadding(0, dp(8), 0, dp(8));
        root.addView(error);

        TextView login = button("Log In", true);
        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit(false);
            }
        });
        root.addView(login, fieldParams());

        TextView signup = button("Create Account", false);
        signup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit(true);
            }
        });
        root.addView(signup, fieldParams());

        TextView consent = new TextView(this);
        consent.setText("By continuing you agree that your messages may be processed and "
                + "stored to provide and improve the service.");
        consent.setTextColor(Color.parseColor("#66FFFFFF"));
        consent.setTextSize(11f);
        consent.setGravity(Gravity.CENTER);
        consent.setPadding(0, dp(16), 0, 0);
        root.addView(consent);

        return root;
    }

    private EditText field(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.parseColor("#80FFFFFF"));
        e.setTextColor(Color.WHITE);
        e.setTextSize(16f);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setBackgroundResource(R.drawable.input_bg);
        e.setPadding(dp(16), dp(13), dp(16), dp(13));
        return e;
    }

    private TextView button(String text, boolean primary) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(16f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        t.setBackgroundResource(primary ? R.drawable.send_button : R.drawable.chip_bg);
        return t;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        return lp;
    }

    private void submit(final boolean isSignUp) {
        final String e = email.getText().toString().trim();
        final String p = password.getText().toString();
        if (e.isEmpty() || p.isEmpty()) {
            error.setText("Enter your email and password.");
            return;
        }
        error.setText(isSignUp ? "Creating account…" : "Signing in…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String result = isSignUp
                        ? SupabaseAuth.signUp(AuthActivity.this, e, p)
                        : SupabaseAuth.signIn(AuthActivity.this, e, p);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result == null) {
                            startActivity(new Intent(AuthActivity.this, VoiceActivity.class));
                            finish();
                        } else {
                            error.setText(result);
                        }
                    }
                });
            }
        }).start();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
