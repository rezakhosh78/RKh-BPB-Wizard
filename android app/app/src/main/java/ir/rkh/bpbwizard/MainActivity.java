package ir.rkh.bpbwizard;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity {
    private static final String PREF = "rkh_bpb_android_store_v1";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_DEPLOYS = "deployments";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SecureRandom rng = new SecureRandom();

    private ArrayList<JSONObject> profiles = new ArrayList<>();
    private ArrayList<JSONObject> deployments = new ArrayList<>();
    private ArrayList<JSONObject> accounts = new ArrayList<>();

    private LinearLayout root;
    private Spinner profileSpinner, authSpinner, accountSpinner;
    private EditText profileNameInput, emailInput, secretInput, proxyInput, workerInput, kvInput, subdomainInput, manualAccountInput, loginEmailInput, loginGlobalKeyInput, loginProxyInput;
    private TextView statusText;
    private LinearLayout historyList;
    private String currentProfileId = "";
    private int wizardStep = 0;
    private int homeMode = 0; // 0 = main, 1 = login, 2 = create

    private final int BG = Color.rgb(255, 241, 244);
    private final int CARD = Color.argb(155, 255, 255, 255);
    private final int CARD_2 = Color.argb(185, 255, 250, 252);
    private final int ORANGE = Color.rgb(224, 36, 72);
    private final int ORANGE_2 = Color.rgb(255, 94, 126);
    private final int TEXT = Color.rgb(48, 18, 28);
    private final int MUTED = Color.rgb(124, 72, 86);
    private final int GREEN = Color.rgb(36, 168, 116);
    private final int RED = Color.rgb(220, 38, 38);

    private static final String[] FIRST_WORDS = {"nova", "orbit", "ember", "solar", "lumen", "atlas", "flare", "turbo", "pixel", "cosmo", "matrix", "velvet", "shadow", "spark", "neon", "quantum", "aurora", "comet", "vector", "cipher"};
    private static final String[] SECOND_WORDS = {"panel", "vault", "bridge", "gate", "hub", "node", "core", "stack", "portal", "shelf", "store", "dash", "route", "shell", "zone", "pulse", "dock", "lane", "mesh", "nest"};
    private static final Set<String> BANNED = new HashSet<>(Arrays.asList("bpb", "rkh", "worker"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(255, 218, 226));
        getWindow().setNavigationBarColor(Color.rgb(255, 230, 235));
        if (Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(true);
        loadStore();
        renderHome();
    }

    private void renderHome() {
        profileSpinner = null; authSpinner = null; accountSpinner = null;
        profileNameInput = null; emailInput = null; secretInput = null; proxyInput = null; workerInput = null; kvInput = null; subdomainInput = null; manualAccountInput = null;
        loginEmailInput = null; loginGlobalKeyInput = null; loginProxyInput = null;
        statusText = null; historyList = null;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), getStatusBarHeight() + dp(10), dp(12), dp(12));
        root.setBackground(glassBg(Color.rgb(255, 241, 244), Color.rgb(255, 220, 230), dp(0), Color.TRANSPARENT, 0));

        root.addView(headerCard(), new LinearLayout.LayoutParams(-1, dp(98)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(10), 0, dp(10));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        if (homeMode == 0) {
            body.addView(homeChoiceCard());
        } else if (homeMode == 1) {
            body.addView(loginCard());
        } else {
            body.addView(stepIndicator());
            if (wizardStep <= 0) body.addView(profileCard());
            else if (wizardStep == 1) body.addView(deployCard());
            else body.addView(historyCard());
            body.addView(wizardNav());

            String selectedProfileBeforeRender = currentProfileId == null ? "" : currentProfileId;
            if (profileSpinner != null) {
                refreshProfileSpinner();
                if (!selectedProfileBeforeRender.isEmpty()) profileSpinner.post(() -> selectProfileById(selectedProfileBeforeRender));
            }
            suggestNames();
            renderHistory();
        }
        body.addView(bottomCredits());
    }

    private View headerCard() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(12), dp(10), dp(12), dp(10));
        h.setBackground(glassBg(Color.argb(170, 255, 255, 255), Color.argb(150, 255, 205, 218), dp(28), Color.argb(145, 255, 80, 110), 1));
        h.setElevation(dp(18));

        TextView menu = headerIcon("☰");
        menu.setOnClickListener(v -> showProfilesMenu());
        h.addView(menu, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        TextView title = text("RKh BPB Wizard", 23, true, TEXT);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        center.addView(title, lp(-1, -2));
        animatePulse(title);
        TextView sub = text("Create BPB Worker", 13, true, ORANGE);
        sub.setGravity(Gravity.CENTER);
        center.addView(sub, lp(-1, -2));
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(0, -1, 1);
        centerLp.setMargins(dp(8), 0, dp(8), 0);
        h.addView(center, centerLp);

        TextView plus = headerIcon("＋");
        plus.setOnClickListener(v -> newProfileMode());
        h.addView(plus, new LinearLayout.LayoutParams(dp(58), dp(58)));
        return h;
    }

    private TextView headerIcon(String s) {
        TextView t = text(s, 30, true, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setBackground(glassBg(Color.argb(238, 224, 36, 72), Color.argb(238, 255, 118, 142), dp(20), Color.argb(150, 255, 255, 255), 1));
        t.setElevation(dp(10));
        t.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.92f).scaleY(0.92f).alpha(0.78f).setDuration(85).start();
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(130).start();
            return false;
        });
        return t;
    }

    private View homeChoiceCard() {
        LinearLayout c = card();
        TextView title = text("Welcome", 24, true, TEXT);
        title.setGravity(Gravity.CENTER);
        c.addView(title, lp(-1, -2));
        TextView sub = text("Choose Import BPB Panel to import an existing panel from Cloudflare, or Create BPB Panel to build a new one.", 13, false, MUTED);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(4), 0, dp(12));
        c.addView(sub, lp(-1, -2));
        c.addView(button("📥 Import BPB Panel", ORANGE, v -> { homeMode = 1; renderHome(); }), lp(-1, dp(62)));
        c.addView(button("✨ Create BPB Panel", Color.rgb(164, 63, 84), v -> { homeMode = 2; wizardStep = 0; renderHome(); }), lp(-1, dp(62)));
        return c;
    }

    private View loginCard() {
        LinearLayout c = card();
        c.addView(sectionTitle("📥 Import BPB Panel"));
        TextView info = text("Enter your Cloudflare Email and Global API Key. The app will scan your Cloudflare accounts, find existing BPB panels, and add them as a new profile.", 13, false, MUTED);
        info.setPadding(0, 0, 0, dp(10));
        c.addView(info, lp(-1, -2));

        loginEmailInput = input("Cloudflare Email", false);
        c.addView(label("Email"));
        c.addView(loginEmailInput, lp(-1, dp(52)));

        loginGlobalKeyInput = input("Cloudflare Global API Key", true);
        c.addView(label("Global API Key"));
        c.addView(loginGlobalKeyInput, lp(-1, dp(52)));

        loginProxyInput = input("Optional HTTP/HTTPS proxy", false);
        c.addView(label("Proxy URL (optional)"));
        c.addView(loginProxyInput, lp(-1, dp(52)));

        c.addView(button("🔎 Find BPB Panel", ORANGE, v -> loginFindBpbPanels()), lp(-1, dp(60)));
        c.addView(button("← Back", Color.rgb(150, 66, 86), v -> { homeMode = 0; renderHome(); }), lp(-1, dp(54)));
        statusText = text("Ready to scan Cloudflare.", 13, false, MUTED);
        statusText.setPadding(0, dp(8), 0, 0);
        c.addView(statusText, lp(-1, -2));
        return c;
    }

    private View stepIndicator() {
        LinearLayout c = card();
        String[] titles = {"1 / 3  Profile", "2 / 3  Deploy", "3 / 3  Deploy History"};
        c.addView(text("✨ " + titles[Math.max(0, Math.min(wizardStep, 2))], 18, true, TEXT), lp(-1, -2));
        c.addView(text("Cards move step by step with Next and Back.", 12, false, MUTED), lp(-1, -2));
        return c;
    }

    private View wizardNav() {
        LinearLayout r = row();
        r.setGravity(Gravity.CENTER);
        if (wizardStep == 0) {
            r.addView(button("← Main", Color.rgb(150, 66, 86), v -> goMain()), new LinearLayout.LayoutParams(0, dp(54), 1));
            r.addView(space(8, 1));
            r.addView(button("Next →", ORANGE, v -> nextStep()), new LinearLayout.LayoutParams(0, dp(54), 1));
        } else if (wizardStep == 1) {
            r.addView(button("← Back", Color.rgb(150, 66, 86), v -> previousStep()), new LinearLayout.LayoutParams(0, dp(54), 1));
            r.addView(space(8, 1));
            r.addView(button("Next →", ORANGE, v -> nextStep()), new LinearLayout.LayoutParams(0, dp(54), 1));
        } else {
            r.addView(button("← Back", Color.rgb(150, 66, 86), v -> previousStep()), new LinearLayout.LayoutParams(0, dp(54), 1));
            r.addView(space(8, 1));
            r.addView(button("Main", ORANGE, v -> goMain()), new LinearLayout.LayoutParams(0, dp(54), 1));
        }
        return r;
    }

    private void goMain() {
        homeMode = 0;
        wizardStep = 0;
        renderHome();
    }

    private void nextStep() {
        if (wizardStep == 0) {
            JSONObject p = profileFromFields();
            if (!validateProfile(p)) return;
            saveProfile();
            wizardStep = 1;
        } else if (wizardStep == 1) {
            wizardStep = 2;
        }
        renderHome();
    }

    private void previousStep() {
        if (wizardStep > 0) wizardStep--;
        renderHome();
    }

    private View bottomCredits() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(10), dp(16), dp(10), dp(18));
        TextView tg = text("Telegram: @pingplas_channel", 14, true, ORANGE);
        tg.setGravity(Gravity.CENTER);
        tg.setOnClickListener(v -> openUrl("https://t.me/pingplas_channel"));
        c.addView(tg, lp(-1, -2));
        TextView made = text("Made By RKh!", 13, true, MUTED);
        made.setGravity(Gravity.CENTER);
        c.addView(made, lp(-1, -2));
        return c;
    }

    private View profileCard() {
        LinearLayout c = card();
        c.addView(sectionTitle("👤 Cloudflare Profile"));

        profileSpinner = new Spinner(this);
        styleSpinner(profileSpinner);
        c.addView(label("Saved profiles"));
        c.addView(profileSpinner, lp(-1, dp(48)));
        profileSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                if (pos <= 0) {
                    currentProfileId = "";
                    clearProfileFields();
                } else {
                    JSONObject p = profiles.get(pos - 1);
                    currentProfileId = p.optString("id", "");
                    fillProfileFields(p);
                }
                renderHistory();
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        profileNameInput = input("Profile name", false);
        c.addView(label("Profile name"));
        c.addView(profileNameInput, lp(-1, dp(52)));

        authSpinner = new Spinner(this);
        styleSpinner(authSpinner);
        authSpinner.setAdapter(spinnerAdapter(Arrays.asList("API Token", "Global API Key + Email")));
        authSpinner.setSelection(1);
        c.addView(label("Auth method"));
        c.addView(authSpinner, lp(-1, dp(48)));

        emailInput = input("Email for Global API Key", false);
        c.addView(label("Email (only for Global Key)"));
        c.addView(emailInput, lp(-1, dp(52)));

        secretInput = input("API Token or Global Key", true);
        c.addView(label("Secret"));
        c.addView(secretInput, lp(-1, dp(52)));

        proxyInput = input("Optional HTTP/HTTPS proxy, e.g. http://127.0.0.1:7890", false);
        c.addView(label("Proxy URL (optional)"));
        c.addView(proxyInput, lp(-1, dp(52)));

        TextView auto = text("Profile information is saved automatically when you tap Next.", 12, false, MUTED);
        auto.setPadding(0, dp(10), 0, 0);
        c.addView(auto, lp(-1, -2));
        return c;
    }

    private View deployCard() {
        LinearLayout c = card();
        c.addView(sectionTitle("☁️ Deploy to Cloudflare"));

        c.addView(button("🔎 Load Cloudflare accounts", ORANGE, v -> loadAccounts()), lp(-1, dp(56)));
        c.addView(button("🎲 Suggest new random names", Color.rgb(77, 45, 25), v -> suggestNames()), lp(-1, dp(56)));

        accountSpinner = new Spinner(this);
        styleSpinner(accountSpinner);
        accountSpinner.setAdapter(spinnerAdapter(Collections.singletonList("No account loaded")));
        c.addView(label("Cloudflare account"));
        c.addView(accountSpinner, lp(-1, dp(48)));

        manualAccountInput = input("Optional manual Account ID for scoped API Tokens", false);
        c.addView(label("Manual Account ID (useful when API Token cannot list accounts)"));
        c.addView(manualAccountInput, lp(-1, dp(52)));

        workerInput = input("random-name", false);
        c.addView(label("Worker name"));
        c.addView(workerInput, lp(-1, dp(52)));

        kvInput = input("random-kv-name", false);
        c.addView(label("KV namespace name"));
        c.addView(kvInput, lp(-1, dp(52)));

        subdomainInput = input("Optional workers.dev account subdomain", false);
        c.addView(label("workers.dev subdomain (optional)"));
        c.addView(subdomainInput, lp(-1, dp(52)));

        c.addView(button("🚀 Deploy Worker + KV", ORANGE, v -> deployNow()), lp(-1, dp(60)));
        statusText = text("Ready.", 13, false, MUTED);
        statusText.setPadding(0, dp(10), 0, 0);
        c.addView(statusText, lp(-1, -2));
        return c;
    }


    private View footerBar() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.HORIZONTAL);
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.setPadding(dp(10), dp(8), dp(10), dp(8));
        f.setBackground(glassBg(Color.argb(232, 12, 7, 18), Color.argb(232, 28, 14, 18), dp(24), Color.argb(95, 255, 132, 28), 1));
        f.setElevation(dp(18));

        TextView plus = footerIcon("＋");
        plus.setOnClickListener(v -> newProfileMode());
        f.addView(plus, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setGravity(Gravity.CENTER);
        TextView m1 = text("RKh BPB Wizard", 13, true, TEXT);
        m1.setGravity(Gravity.CENTER);
        TextView m2 = text("Liquid Glass • Cloudflare", 11, false, MUTED);
        m2.setGravity(Gravity.CENTER);
        middle.addView(m1, lp(-1, -2));
        middle.addView(m2, lp(-1, -2));
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(0, -1, 1);
        midLp.setMargins(dp(10), 0, dp(10), 0);
        f.addView(middle, midLp);

        TextView menu = footerIcon("☰");
        menu.setTextSize(30);
        menu.setOnClickListener(v -> showProfilesMenu());
        f.addView(menu, new LinearLayout.LayoutParams(dp(58), dp(58)));
        return f;
    }

    private TextView footerIcon(String s) {
        TextView t = text(s, 32, true, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setBackground(glassBg(Color.argb(235, 255, 122, 24), Color.argb(235, 255, 184, 74), dp(20), Color.argb(115, 255, 255, 255), 1));
        t.setElevation(dp(10));
        t.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.92f).scaleY(0.92f).alpha(0.78f).setDuration(95).start();
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(145).start();
            return false;
        });
        return t;
    }

    private void newProfileMode() {
        homeMode = 2;
        wizardStep = 0;
        currentProfileId = "";
        if (profileSpinner != null) profileSpinner.setSelection(0);
        clearProfileFields();
        renderHome();
        toast("New profile mode");
    }

    private void showProfilesMenu() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(glassBg(Color.argb(245, 255, 255, 255), Color.argb(230, 255, 218, 228), dp(24), Color.argb(120, 255, 80, 110), 1));
        box.addView(text("☰ Profiles", 22, true, TEXT), lp(-1, -2));
        box.addView(text("Only profile names are shown here. Tap one to open details.", 12, false, MUTED), lp(-1, -2));

        if (profiles.isEmpty()) box.addView(text("No saved profiles yet. Tap ＋ above to create one.", 13, false, MUTED), lp(-1, -2));
        final AlertDialog[] ref = new AlertDialog[1];
        for (JSONObject p : profiles) {
            Button b = button("👤 " + p.optString("name", "Profile"), ORANGE, v -> {
                if (ref[0] != null) ref[0].dismiss();
                showProfileDetails(p);
            });
            box.addView(b, lp(-1, dp(54)));
        }

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(sv).create();
        ref[0] = dialog;
        dialog.setOnShowListener(d -> { if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent); });
        dialog.show();
    }

    private void showProfileDetails(JSONObject p) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(glassBg(Color.argb(246, 255, 255, 255), Color.argb(232, 255, 220, 230), dp(24), Color.argb(130, 255, 80, 110), 1));
        box.addView(text("👤 " + p.optString("name", "Profile"), 22, true, TEXT), lp(-1, -2));
        TextView emailPlain = text("Email: " + p.optString("email", "—"), 13, false, MUTED);
        emailPlain.setTextIsSelectable(true);
        box.addView(emailPlain, lp(-1, -2));

        TextView localTitle = text("📊 Deploy History", 15, true, ORANGE);
        localTitle.setPadding(0, dp(10), 0, dp(4));
        box.addView(localTitle, lp(-1, -2));
        final AlertDialog[] ref = new AlertDialog[1];
        int count = 0;
        for (JSONObject d : deployments) {
            if (!p.optString("id").equals(d.optString("profile_id"))) continue;
            count++;
            LinearLayout dcard = new LinearLayout(this);
            dcard.setOrientation(LinearLayout.VERTICAL);
            dcard.setPadding(dp(10), dp(10), dp(10), dp(10));
            dcard.setBackground(round(Color.argb(150, 255, 255, 255), dp(18), Color.argb(70, 224, 36, 72), 1));
            addDeployBubbles(dcard, d);
            dcard.addView(button("🌐 Open Panel", ORANGE, v -> { if (ref[0] != null) ref[0].dismiss(); openPanelPage(d); }), lp(-1, dp(50)));
            dcard.addView(button("🧹 Delete Worker + KV", RED, v -> { if (ref[0] != null) ref[0].dismiss(); cleanupDeployment(d); }), lp(-1, dp(50)));
            box.addView(dcard, lp(-1, -2));
        }
        if (count == 0) box.addView(text("No deploy history for this profile yet.", 12, false, MUTED), lp(-1, -2));

        box.addView(button("☁️ Show all Cloudflare Workers + KV", Color.rgb(164, 63, 84), v -> { if (ref[0] != null) ref[0].dismiss(); showRemoteInventory(p); }), lp(-1, dp(54)));

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(sv).create();
        ref[0] = dialog;
        dialog.setOnShowListener(d -> { if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent); });
        dialog.show();
    }

    private void showRemoteInventory(JSONObject profile) {
        setStatus("Loading Cloudflare Workers and KV...");
        toast("Loading Cloudflare inventory...");
        new Thread(() -> {
            try {
                JSONArray data = loadRemoteInventory(profile);
                ui.post(() -> renderRemoteInventoryDialog(profile, data));
            } catch (Exception e) {
                ui.post(() -> { setStatus("Error: " + e.getMessage()); toast(e.getMessage()); });
            }
        }).start();
    }

    private JSONArray loadRemoteInventory(JSONObject profile) throws Exception {
        CloudflareClient cf = new CloudflareClient(profile);
        JSONObject accountsRes = cf.request("GET", "/accounts", null, null, null);
        JSONArray arr = accountsRes.optJSONArray("result");
        JSONArray out = new JSONArray();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject a = arr.getJSONObject(i);
            String accountId = a.optString("id");
            JSONObject item = new JSONObject();
            item.put("account_id", accountId);
            item.put("account_name", a.optString("name", "Account"));
            item.put("workers", listWorkers(cf, accountId));
            item.put("kv", listAllKv(cf, accountId));
            out.put(item);
        }
        return out;
    }

    private JSONArray listWorkers(CloudflareClient cf, String accountId) {
        JSONArray out = new JSONArray();
        try {
            JSONObject res = cf.request("GET", "/accounts/" + enc(accountId) + "/workers/scripts", null, null, null);
            JSONArray arr = res.optJSONArray("result");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.getJSONObject(i);
                    String name = s.optString("id", s.optString("script_name", s.optString("name", "")));
                    if (!name.isEmpty()) out.put(new JSONObject().put("name", name));
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    private JSONArray listAllKv(CloudflareClient cf, String accountId) {
        JSONArray out = new JSONArray();
        try {
            int page = 1;
            while (true) {
                JSONObject res = cf.request("GET", "/accounts/" + enc(accountId) + "/storage/kv/namespaces?per_page=100&page=" + page, null, null, null);
                JSONArray arr = res.optJSONArray("result");
                if (arr != null) for (int i = 0; i < arr.length(); i++) out.put(arr.getJSONObject(i));
                JSONObject info = res.optJSONObject("result_info");
                int total = info == null ? 1 : info.optInt("total_pages", 1);
                if (page >= total) break;
                page++;
            }
        } catch (Exception ignored) {}
        return out;
    }

    private void renderRemoteInventoryDialog(JSONObject profile, JSONArray accountsData) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(glassBg(Color.argb(246, 255, 255, 255), Color.argb(230, 255, 220, 230), dp(24), Color.argb(130, 224, 36, 72), 1));
        box.addView(text("☁️ Cloudflare inventory", 22, true, TEXT), lp(-1, -2));
        box.addView(text("Profile: " + profile.optString("name") + "\nEmail: " + profile.optString("email", "—"), 12, false, MUTED), lp(-1, -2));

        final AlertDialog[] ref = new AlertDialog[1];
        try {
            if (accountsData.length() == 0) box.addView(text("No accounts found or access denied.", 13, false, MUTED), lp(-1, -2));
            for (int i = 0; i < accountsData.length(); i++) {
                JSONObject acc = accountsData.getJSONObject(i);
                String accountId = acc.optString("account_id");
                LinearLayout acard = new LinearLayout(this);
                acard.setOrientation(LinearLayout.VERTICAL);
                acard.setPadding(dp(10), dp(10), dp(10), dp(10));
                acard.setBackground(round(Color.argb(166, 255, 255, 255), dp(18), Color.argb(80, 224, 36, 72), 1));
                acard.addView(text("🏢 " + acc.optString("account_name"), 15, true, ORANGE_2), lp(-1, -2));

                JSONArray workers = acc.optJSONArray("workers");
                acard.addView(text("Workers", 13, true, TEXT), lp(-1, -2));
                if (workers == null || workers.length() == 0) acard.addView(text("No Workers found.", 12, false, MUTED), lp(-1, -2));
                if (workers != null) for (int w = 0; w < workers.length(); w++) {
                    JSONObject wo = workers.getJSONObject(w);
                    String workerName = wo.optString("name");
                    acard.addView(remoteRow("☁️ " + workerName, "Delete Worker", RED, v -> deleteRemoteWorker(profile, accountId, workerName, ref[0])), lp(-1, -2));
                }

                JSONArray kvs = acc.optJSONArray("kv");
                acard.addView(text("KV Namespaces", 13, true, TEXT), lp(-1, -2));
                if (kvs == null || kvs.length() == 0) acard.addView(text("No KV namespaces found.", 12, false, MUTED), lp(-1, -2));
                if (kvs != null) for (int k = 0; k < kvs.length(); k++) {
                    JSONObject kv = kvs.getJSONObject(k);
                    String title = kv.optString("title", kv.optString("id"));
                    String id = kv.optString("id");
                    acard.addView(remoteRow("🗃️ " + title, "Delete KV", RED, v -> deleteRemoteKv(profile, accountId, id, ref[0])), lp(-1, -2));
                }
                box.addView(acard, lp(-1, -2));
            }
        } catch (Exception e) {
            box.addView(text("Error rendering inventory: " + e.getMessage(), 12, false, RED), lp(-1, -2));
        }

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(sv).create();
        ref[0] = dialog;
        dialog.setOnShowListener(d -> { if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent); });
        dialog.show();
    }

    private View remoteRow(String label, String btn, int color, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(8), dp(8), dp(8), dp(8));
        r.setBackground(round(Color.argb(155, 255, 250, 252), dp(14), Color.argb(65, 224, 36, 72), 1));
        TextView t = text(label, 12, false, TEXT);
        t.setTextIsSelectable(true);
        r.addView(t, lp(-1, -2));
        r.addView(button(btn, color, click), lp(-1, dp(46)));
        return r;
    }

    private void deleteRemoteWorker(JSONObject profile, String accountId, String workerName, AlertDialog dialog) {
        setStatus("Deleting Worker...");
        new Thread(() -> {
            try {
                new CloudflareClient(profile).request("DELETE", "/accounts/" + enc(accountId) + "/workers/scripts/" + enc(workerName), null, null, null);
                ui.post(() -> { if (dialog != null) dialog.dismiss(); toast("Worker deleted"); showRemoteInventory(profile); });
            } catch (Exception e) { ui.post(() -> toast(e.getMessage())); }
        }).start();
    }

    private void deleteRemoteKv(JSONObject profile, String accountId, String kvId, AlertDialog dialog) {
        setStatus("Deleting KV...");
        new Thread(() -> {
            try {
                new CloudflareClient(profile).request("DELETE", "/accounts/" + enc(accountId) + "/storage/kv/namespaces/" + enc(kvId), null, null, null);
                ui.post(() -> { if (dialog != null) dialog.dismiss(); toast("KV deleted"); showRemoteInventory(profile); });
            } catch (Exception e) { ui.post(() -> toast(e.getMessage())); }
        }).start();
    }

    private View historyCard() {
        LinearLayout c = card();
        c.addView(sectionTitle("📊 Deploy History"));
        historyList = new LinearLayout(this);
        historyList.setOrientation(LinearLayout.VERTICAL);
        c.addView(historyList, lp(-1, -2));
        return c;
    }

    private void openPanelPage(JSONObject d) {
        String panelUrl = d.optString("panel_url", "");
        if (panelUrl.isEmpty()) { toast("Panel URL not found."); return; }

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(dp(12), getStatusBarHeight() + dp(10), dp(12), dp(12));
        screen.setBackground(glassBg(Color.rgb(255, 241, 244), Color.rgb(255, 220, 230), dp(0), Color.TRANSPARENT, 0));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(12), dp(12), dp(12), dp(10));
        top.setBackground(glassBg(Color.argb(178, 255, 255, 255), Color.argb(150, 255, 205, 218), dp(24), Color.argb(145, 255, 80, 110), 1));
        top.setElevation(dp(14));
        TextView title = text("RKh BPB Wizard • Panel", 20, true, TEXT);
        title.setGravity(Gravity.CENTER);
        top.addView(title, lp(-1, -2));
        top.addView(copyBubble("Panel URL", panelUrl), lp(-1, -2));
        top.addView(copyBubble("Panel Password", d.optString("panel_password")), lp(-1, -2));
        LinearLayout actions = row();
        actions.addView(button("← Back", Color.rgb(150, 66, 86), v -> renderHome()), new LinearLayout.LayoutParams(0, dp(50), 1));
        actions.addView(space(8, 1));
        actions.addView(button("Open in Browser", ORANGE, v -> openUrl(panelUrl)), new LinearLayout.LayoutParams(0, dp(50), 1));
        top.addView(actions, lp(-1, -2));
        screen.addView(top, new LinearLayout.LayoutParams(-1, -2));

        WebView web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        web.setWebViewClient(new WebViewClient());
        web.loadUrl(panelUrl);
        LinearLayout.LayoutParams webLp = new LinearLayout.LayoutParams(-1, 0, 1);
        webLp.setMargins(0, dp(10), 0, 0);
        screen.addView(web, webLp);
        setContentView(screen);
    }

    private void renderHistory() {
        if (historyList == null) return;
        historyList.removeAllViews();

        if (currentProfileId == null || currentProfileId.isEmpty()) {
            TextView empty = text("Select a profile to see its deploy history.", 13, false, MUTED);
            empty.setPadding(0, dp(6), 0, dp(6));
            historyList.addView(empty, lp(-1, -2));
            animateIn(historyList);
            return;
        }

        int count = 0;
        for (JSONObject d : deployments) {
            if (!currentProfileId.equals(d.optString("profile_id"))) continue;
            count++;
            historyList.addView(historyItemCard(d), lp(-1, -2));
        }

        if (count == 0) {
            TextView empty = text("No deployments for this profile yet.", 13, false, MUTED);
            empty.setPadding(0, dp(6), 0, dp(6));
            historyList.addView(empty, lp(-1, -2));
        }
        animateIn(historyList);
    }

    private View historyItemCard(JSONObject d) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(12), dp(12), dp(12), dp(12));
        item.setBackground(glassBg(Color.argb(205, 255, 255, 255), Color.argb(190, 255, 224, 232), dp(20), Color.argb(90, 224, 36, 72), 1));

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text("☁️ " + d.optString("worker_name", "Deployment"), 15, true, TEXT);
        top.addView(name, lp(0, -2, 1));
        TextView badge = text("ACTIVE", 11, true, Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        badge.setBackground(round(GREEN, dp(999), Color.argb(60, 255, 255, 255), 1));
        top.addView(badge, new LinearLayout.LayoutParams(-2, -2));
        item.addView(top, lp(-1, -2));

        addDeployBubbles(item, d);
        item.addView(button("🌐 Open Panel", ORANGE, v -> openPanelPage(d)), lp(-1, dp(54)));
        item.addView(button("🧹 Delete this Worker + KV", RED, v -> cleanupDeployment(d)), lp(-1, dp(54)));
        return item;
    }

    private void addDeployBubbles(LinearLayout parent, JSONObject d) {
        parent.addView(copyBubble("Panel URL", d.optString("panel_url")), lp(-1, -2));
        parent.addView(copyBubble("Panel Password", d.optString("panel_password")), lp(-1, -2));
        parent.addView(copyBubble("Subscription URL", d.optString("subscription_url")), lp(-1, -2));
        parent.addView(copyBubble("Worker URL", d.optString("worker_url")), lp(-1, -2));
        parent.addView(copyBubble("KV ID", kvIdOf(d)), lp(-1, -2));
    }

    private String kvIdOf(JSONObject d) {
        String kvId = "";
        JSONObject kv = d.optJSONObject("kv_namespace");
        if (kv != null) kvId = kv.optString("id");
        if (kvId.isEmpty()) kvId = d.optString("kv_id");
        return kvId;
    }

    private View copyBubble(String label, String value) {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.HORIZONTAL);
        bubble.setGravity(Gravity.CENTER_VERTICAL);
        bubble.setPadding(dp(10), dp(8), dp(8), dp(8));
        bubble.setBackground(round(Color.argb(178, 255, 255, 255), dp(18), Color.argb(72, 224, 36, 72), 1));

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        TextView l = text(label, 11, true, MUTED);
        TextView v = text(value == null || value.isEmpty() ? "—" : value, 12, false, TEXT);
        v.setTextIsSelectable(true);
        txt.addView(l, lp(-1, -2));
        txt.addView(v, lp(-1, -2));
        bubble.addView(txt, new LinearLayout.LayoutParams(0, -2, 1));

        Button copy = button("Copy", ORANGE, x -> copyText(label, value));
        bubble.addView(copy, new LinearLayout.LayoutParams(dp(86), dp(46)));
        return bubble;
    }

    private void copyText(String label, String value) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText(label, value == null ? "" : value));
            toast("Copied: " + label);
        } catch (Exception e) { toast("Copy failed"); }
    }

    private String historyDetails(JSONObject d) {
        return "Panel URL: " + d.optString("panel_url") + "\n" +
                "Panel Password: " + d.optString("panel_password") + "\n" +
                "Subscription URL: " + d.optString("subscription_url") + "\n" +
                "Worker URL: " + d.optString("worker_url") + "\n" +
                "KV ID: " + kvIdOf(d);
    }

    private void renderResult(JSONObject result) {
        currentProfileId = result.optString("profile_id", currentProfileId);
        wizardStep = 2;
        toast("Deploy completed. History opened.");
        renderHome();
    }

    private String resultText(JSONObject r) {
        return "Worker: " + r.optString("worker_name") + "\n" +
                "Worker URL: " + r.optString("worker_url") + "\n" +
                "Panel URL: " + r.optString("panel_url") + "\n" +
                "Panel Password: " + r.optString("panel_password") + "\n" +
                "Subscription URL: " + r.optString("subscription_url") + "\n" +
                "UUID: " + r.optString("uuid") + "\n" +
                "TR_PASS: " + r.optString("tr_pass") + "\n" +
                "SUB_PATH: " + r.optString("sub_path");
    }

    private void loginFindBpbPanels() {
        String email = loginEmailInput == null ? "" : loginEmailInput.getText().toString().trim();
        String key = loginGlobalKeyInput == null ? "" : loginGlobalKeyInput.getText().toString().trim();
        String proxy = loginProxyInput == null ? "" : loginProxyInput.getText().toString().trim();
        if (email.isEmpty()) { toast("Cloudflare Email is required."); return; }
        if (key.isEmpty()) { toast("Global API Key is required."); return; }
        JSONObject p = new JSONObject();
        try {
            p.put("id", randomHex(8));
            p.put("name", "Imported BPB - " + email);
            p.put("auth_type", "global_key");
            p.put("email", email);
            p.put("secret", key);
            p.put("proxy_url", proxy);
        } catch (Exception ignored) {}
        setStatus("Scanning Cloudflare for BPB panels...");
        new Thread(() -> {
            try {
                JSONObject res = importBpbPanelsFromCloudflare(p);
                ui.post(() -> {
                    currentProfileId = p.optString("id");
                    homeMode = 2;
                    wizardStep = 2;
                    toast("Imported " + res.optInt("count") + " BPB panel(s).");
                    renderHome();
                });
            } catch (Exception e) {
                ui.post(() -> { setStatus("Error: " + e.getMessage()); toast(e.getMessage()); });
            }
        }).start();
    }

    private JSONObject importBpbPanelsFromCloudflare(JSONObject profile) throws Exception {
        CloudflareClient cf = new CloudflareClient(profile);
        JSONObject accountsRes = cf.request("GET", "/accounts", null, null, null);
        JSONArray arr = accountsRes.optJSONArray("result");
        if (arr == null || arr.length() == 0) throw new RuntimeException("No Cloudflare account found for this import.");

        ArrayList<JSONObject> imported = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject account = arr.getJSONObject(i);
            String accountId = account.optString("id");
            String subdomain = getAccountSubdomainOnly(cf, accountId);
            if (subdomain.isEmpty()) continue;
            JSONArray workers = listWorkers(cf, accountId);
            for (int w = 0; w < workers.length(); w++) {
                String workerName = workers.getJSONObject(w).optString("name");
                JSONObject d = tryImportBpbWorker(cf, profile, account, subdomain, workerName);
                if (d != null) imported.add(d);
            }
        }
        if (imported.isEmpty()) throw new RuntimeException("No BPB Worker/Panel was found on this Cloudflare import.");

        // Avoid duplicate profile IDs and store imported profile + deployments.
        profiles.add(0, profile);
        for (int i = imported.size() - 1; i >= 0; i--) deployments.add(0, imported.get(i));
        while (deployments.size() > 80) deployments.remove(deployments.size() - 1);
        saveStore();
        return new JSONObject().put("count", imported.size());
    }

    private JSONObject tryImportBpbWorker(CloudflareClient cf, JSONObject profile, JSONObject account, String subdomain, String workerName) {
        try {
            if (workerName == null || workerName.isEmpty()) return null;
            String accountId = account.optString("id");
            String workerUrl = "https://" + workerName + "." + subdomain + ".workers.dev";
            String panelUrl = workerUrl + "/panel";
            String kvId = "";
            String subPath = "";
            String uuid = "";
            String trPass = "";
            boolean likelyBpb = false;

            try {
                JSONObject settings = cf.request("GET", "/accounts/" + enc(accountId) + "/workers/scripts/" + enc(workerName) + "/settings", null, null, null);
                JSONObject result = settings.optJSONObject("result");
                JSONArray bindings = result == null ? settings.optJSONArray("bindings") : result.optJSONArray("bindings");
                if (bindings != null) {
                    for (int b = 0; b < bindings.length(); b++) {
                        JSONObject bind = bindings.getJSONObject(b);
                        String name = bind.optString("name");
                        String type = bind.optString("type");
                        if ("kv".equals(name) || "kv_namespace".equals(type)) {
                            kvId = bind.optString("namespace_id", bind.optString("id", kvId));
                        }
                        if ("SUB_PATH".equals(name)) subPath = bind.optString("text", bind.optString("value", subPath));
                        if ("UUID".equals(name)) uuid = bind.optString("text", bind.optString("value", uuid));
                        if ("TR_PASS".equals(name)) trPass = bind.optString("text", bind.optString("value", trPass));
                    }
                }
                if (!kvId.isEmpty() && (!subPath.isEmpty() || !uuid.isEmpty() || !trPass.isEmpty())) likelyBpb = true;
            } catch (Exception ignored) {}

            if (!likelyBpb && !looksLikeBpbPanel(panelUrl, profile)) return null;

            String panelPassword = "";
            if (!kvId.isEmpty()) {
                try { panelPassword = cf.requestText("GET", "/accounts/" + enc(accountId) + "/storage/kv/namespaces/" + enc(kvId) + "/values/pwd", null, null, null).trim(); } catch (Exception ignored) {}
            }
            JSONObject kv = new JSONObject().put("id", kvId).put("title", kvId.isEmpty() ? "Imported KV" : "Imported KV");
            JSONObject d = new JSONObject();
            d.put("id", randomHex(8));
            d.put("profile_id", profile.optString("id"));
            d.put("profile_name", profile.optString("name"));
            d.put("account_id", accountId);
            d.put("worker_name", workerName);
            d.put("kv_namespace", kv);
            d.put("worker_url", workerUrl);
            d.put("panel_url", panelUrl);
            d.put("subscription_url", subPath.isEmpty() ? workerUrl : workerUrl + "/" + subPath);
            d.put("uuid", uuid);
            d.put("tr_pass", trPass);
            d.put("panel_password", panelPassword);
            d.put("sub_path", subPath);
            d.put("account_subdomain", subdomain);
            d.put("status", "imported");
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    private String getAccountSubdomainOnly(CloudflareClient cf, String accountId) {
        try {
            JSONObject p = cf.request("GET", "/accounts/" + enc(accountId) + "/workers/subdomain", null, null, null);
            JSONObject r = p.optJSONObject("result");
            return r == null ? "" : r.optString("subdomain", r.optString("name", ""));
        } catch (Exception e) { return ""; }
    }

    private boolean looksLikeBpbPanel(String panelUrl, JSONObject profile) {
        try {
            URL url = new URL(panelUrl);
            Proxy proxy = parseProxy(profile.optString("proxy_url"));
            HttpURLConnection c = (HttpURLConnection) (proxy == null ? url.openConnection() : url.openConnection(proxy));
            c.setConnectTimeout(9000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent", "RKh-BPB-Wizard-Android");
            int code = c.getResponseCode();
            if (code < 200 || code >= 500) return false;
            String txt = readAll(c.getInputStream()).toLowerCase(Locale.US);
            return txt.contains("bpb") || (txt.contains("panel") && txt.contains("password"));
        } catch (Exception e) { return false; }
    }

    private void loadAccounts() {
        JSONObject p = profileFromFields();
        if (!validateProfile(p)) return;
        setStatus("Loading Cloudflare accounts...");
        runAsync(() -> {
            CloudflareClient cf = new CloudflareClient(p);
            JSONObject res = cf.request("GET", "/accounts", null, null, null);
            JSONArray arr = res.optJSONArray("result");
            accounts.clear();
            if (arr != null) for (int i = 0; i < arr.length(); i++) accounts.add(arr.getJSONObject(i));
            return res;
        }, result -> {
            ArrayList<String> names = new ArrayList<>();
            for (JSONObject a : accounts) names.add(a.optString("name", "Account") + " — " + a.optString("id"));
            if (names.isEmpty()) names.add("No account found");
            accountSpinner.setAdapter(spinnerAdapter(names));
            setStatus("Accounts loaded: " + accounts.size());
        });
    }

    private void deployNow() {
        JSONObject profile = profileFromFields();
        if (!validateProfile(profile)) return;
        if (currentProfileId.isEmpty()) {
            saveProfile();
            profile = profileFromFields();
        }
        final JSONObject p = profile;
        JSONObject chosenAccount = null;
        String manualAccountId = manualAccountInput == null ? "" : manualAccountInput.getText().toString().trim();
        if (!manualAccountId.isEmpty()) {
            try {
                chosenAccount = new JSONObject().put("id", manualAccountId).put("name", "Manual account");
            } catch (Exception ignored) {}
        } else if (!accounts.isEmpty() && accountSpinner.getSelectedItemPosition() >= 0) {
            chosenAccount = accounts.get(Math.max(0, accountSpinner.getSelectedItemPosition()));
        }
        if (chosenAccount == null) {
            toast("Load accounts or paste Account ID manually. This helps scoped API Tokens.");
            return;
        }
        final JSONObject account = chosenAccount;
        final String workerRaw = workerInput.getText().toString();
        final String kvRaw = kvInput.getText().toString();
        final String subdomainRaw = subdomainInput.getText().toString();
        setStatus("Deploying Worker + KV...");
        runAsync(() -> deployCloudflare(p, account, workerRaw, kvRaw, subdomainRaw), this::renderResult);
    }

    private JSONObject deployCloudflare(JSONObject p, JSONObject account, String workerRaw, String kvRaw, String subdomainRaw) throws Exception {
        CloudflareClient cf = new CloudflareClient(p);
        String accountId = account.optString("id");
        String workerName = userResourceName(workerRaw, 55, randomName());
        String kvName = userResourceName(kvRaw, 60, workerName + "-" + pick(SECOND_WORDS));
        String desiredSub = safeName(subdomainRaw, "nova-" + randomHex(4));

        String accountSubdomain = getOrSetAccountSubdomain(cf, accountId, desiredSub);
        JSONObject kv = getOrCreateKV(cf, accountId, kvName);
        String kvId = kv.optString("id");
        if (kvId.isEmpty()) throw new RuntimeException("Could not get KV namespace ID.");

        String uuid = UUID.randomUUID().toString();
        String trPass = randomSecret("tr_");
        String panelPassword = randomSecret("panel_");
        String subPath = randomSubPath();

        cf.request("PUT", "/accounts/" + enc(accountId) + "/storage/kv/namespaces/" + enc(kvId) + "/values/pwd", null, panelPassword.getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8");

        JSONObject metadata = new JSONObject();
        metadata.put("main_module", "worker.js");
        metadata.put("compatibility_date", "2025-01-01");
        JSONArray bindings = new JSONArray();
        bindings.put(new JSONObject().put("type", "kv_namespace").put("name", "kv").put("namespace_id", kvId));
        bindings.put(new JSONObject().put("type", "plain_text").put("name", "UUID").put("text", uuid));
        bindings.put(new JSONObject().put("type", "plain_text").put("name", "TR_PASS").put("text", trPass));
        bindings.put(new JSONObject().put("type", "plain_text").put("name", "SUB_PATH").put("text", subPath));
        metadata.put("bindings", bindings);

        byte[] worker = readAsset("worker.js");
        Multipart mp = buildMultipart(metadata.toString(), worker);
        cf.request("PUT", "/accounts/" + enc(accountId) + "/workers/scripts/" + enc(workerName), null, mp.body, mp.contentType);
        enableScriptSubdomain(cf, accountId, workerName);

        String workerUrl = "https://" + workerName + "." + accountSubdomain + ".workers.dev";
        JSONObject result = new JSONObject();
        result.put("id", randomHex(8));
        result.put("profile_id", p.optString("id"));
        result.put("profile_name", p.optString("name"));
        result.put("account_id", accountId);
        result.put("worker_name", workerName);
        result.put("kv_namespace", kv);
        result.put("worker_url", workerUrl);
        result.put("panel_url", workerUrl + "/panel");
        result.put("subscription_url", workerUrl + "/" + subPath);
        result.put("uuid", uuid);
        result.put("tr_pass", trPass);
        result.put("panel_password", panelPassword);
        result.put("sub_path", subPath);
        result.put("account_subdomain", accountSubdomain);
        result.put("status", "active");
        deployments.add(0, result);
        while (deployments.size() > 50) deployments.remove(deployments.size() - 1);
        saveStore();
        return result;
    }

    private void cleanupDeployment(JSONObject d) {
        JSONObject profile = findProfile(d.optString("profile_id"));
        if (profile == null) {
            toast("Profile for this deployment was not found.");
            return;
        }
        setStatus("Cleaning up...");
        runAsync(() -> {
            CloudflareClient cf = new CloudflareClient(profile);
            String accountId = d.optString("account_id");
            String workerName = d.optString("worker_name");
            JSONObject kv = d.optJSONObject("kv_namespace");
            String kvId = kv != null ? kv.optString("id") : d.optString("kv_id");
            Exception last = null;
            try { cf.request("DELETE", "/accounts/" + enc(accountId) + "/workers/scripts/" + enc(workerName), null, null, null); } catch (Exception e) { last = e; }
            try { if (!kvId.isEmpty()) cf.request("DELETE", "/accounts/" + enc(accountId) + "/storage/kv/namespaces/" + enc(kvId), null, null, null); } catch (Exception e) { last = e; }
            if (last != null) throw last;
            removeDeployment(d.optString("id"));
            saveStore();
            return new JSONObject().put("message", "Cleanup completed successfully");
        }, result -> {
            toast("Cleanup completed successfully");
            renderHome();
        });
    }

    private JSONObject getOrCreateKV(CloudflareClient cf, String accountId, String title) throws Exception {
        JSONObject existing = findKV(cf, accountId, title);
        if (existing != null) return new JSONObject().put("id", existing.optString("id")).put("title", title).put("reused", true);
        JSONObject res = cf.request("POST", "/accounts/" + enc(accountId) + "/storage/kv/namespaces", new JSONObject().put("title", title), null, null);
        JSONObject r = res.optJSONObject("result");
        if (r == null) throw new RuntimeException("Cloudflare did not return KV result.");
        return new JSONObject().put("id", r.optString("id")).put("title", r.optString("title", title)).put("reused", false);
    }

    private JSONObject findKV(CloudflareClient cf, String accountId, String title) throws Exception {
        int page = 1;
        while (true) {
            JSONObject res = cf.request("GET", "/accounts/" + enc(accountId) + "/storage/kv/namespaces?per_page=100&page=" + page, null, null, null);
            JSONArray arr = res.optJSONArray("result");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject ns = arr.getJSONObject(i);
                    if (title.equals(ns.optString("title"))) return ns;
                }
            }
            JSONObject info = res.optJSONObject("result_info");
            int totalPages = info == null ? 1 : info.optInt("total_pages", 1);
            if (page >= totalPages) return null;
            page++;
        }
    }

    private String getOrSetAccountSubdomain(CloudflareClient cf, String accountId, String desired) throws Exception {
        try {
            JSONObject p = cf.request("GET", "/accounts/" + enc(accountId) + "/workers/subdomain", null, null, null);
            JSONObject r = p.optJSONObject("result");
            String sub = r == null ? "" : (r.optString("subdomain", r.optString("name", "")));
            if (!sub.isEmpty()) return sub;
        } catch (Exception ignored) {}
        Exception last = null;
        JSONObject body = new JSONObject().put("subdomain", desired);
        for (String m : new String[]{"PUT", "POST", "PATCH"}) {
            try {
                JSONObject p = cf.request(m, "/accounts/" + enc(accountId) + "/workers/subdomain", body, null, null);
                JSONObject r = p.optJSONObject("result");
                if (r != null) return r.optString("subdomain", desired);
                return desired;
            } catch (Exception e) { last = e; }
        }
        throw new RuntimeException("Could not create workers.dev account subdomain. Last error: " + (last == null ? "unknown" : last.getMessage()));
    }

    private void enableScriptSubdomain(CloudflareClient cf, String accountId, String workerName) throws Exception {
        Exception last = null;
        JSONObject body = new JSONObject().put("enabled", true);
        for (String m : new String[]{"POST", "PUT", "PATCH"}) {
            try {
                cf.request(m, "/accounts/" + enc(accountId) + "/workers/scripts/" + enc(workerName) + "/subdomain", body, null, null);
                return;
            } catch (Exception e) { last = e; }
        }
        throw new RuntimeException("Worker uploaded, but workers.dev route could not be enabled. Last error: " + (last == null ? "unknown" : last.getMessage()));
    }

    private void saveProfile() {
        try {
            JSONObject p = profileFromFields();
            if (!validateProfile(p)) return;
            String id = p.optString("id");
            if (id.isEmpty()) {
                id = randomHex(8);
                p.put("id", id);
            }
            boolean updated = false;
            for (int i = 0; i < profiles.size(); i++) {
                if (id.equals(profiles.get(i).optString("id"))) {
                    profiles.set(i, p);
                    updated = true;
                    break;
                }
            }
            if (!updated) profiles.add(0, p);
            currentProfileId = id;
            saveStore();
            refreshProfileSpinner();
            selectProfileById(id);
            toast("Profile saved");
        } catch (Exception e) {
            toast(e.getMessage());
        }
    }

    private void deleteProfile() {
        if (currentProfileId.isEmpty()) { toast("No profile selected."); return; }
        for (int i = profiles.size() - 1; i >= 0; i--) if (currentProfileId.equals(profiles.get(i).optString("id"))) profiles.remove(i);
        for (int i = deployments.size() - 1; i >= 0; i--) if (currentProfileId.equals(deployments.get(i).optString("profile_id"))) deployments.remove(i);
        currentProfileId = "";
        saveStore();
        refreshProfileSpinner();
        clearProfileFields();
        renderHistory();
        toast("Profile deleted");
    }

    private void resetLocalData() {
        profiles.clear(); deployments.clear(); accounts.clear(); currentProfileId = "";
        saveStore();
        renderHome();
        toast("Local data reset");
    }

    private JSONObject profileFromFields() {
        JSONObject p = new JSONObject();
        try {
            p.put("id", currentProfileId == null ? "" : currentProfileId);
            p.put("name", profileNameInput == null ? "" : profileNameInput.getText().toString().trim());
            p.put("auth_type", authSpinner != null && authSpinner.getSelectedItemPosition() == 1 ? "global_key" : "api_token");
            p.put("email", emailInput == null ? "" : emailInput.getText().toString().trim());
            p.put("secret", secretInput == null ? "" : secretInput.getText().toString().trim());
            p.put("proxy_url", proxyInput == null ? "" : proxyInput.getText().toString().trim());
        } catch (Exception ignored) {}
        return p;
    }

    private boolean validateProfile(JSONObject p) {
        if (p.optString("name").isEmpty()) { toast("Profile name is required."); return false; }
        if (p.optString("secret").isEmpty()) { toast("API Token or Global Key is required."); return false; }
        if ("global_key".equals(p.optString("auth_type")) && p.optString("email").isEmpty()) { toast("Email is required for Global API Key."); return false; }
        return true;
    }

    private void fillProfileFields(JSONObject p) {
        profileNameInput.setText(p.optString("name"));
        authSpinner.setSelection("global_key".equals(p.optString("auth_type")) ? 1 : 0);
        emailInput.setText(p.optString("email"));
        secretInput.setText(p.optString("secret"));
        proxyInput.setText(p.optString("proxy_url"));
    }

    private void clearProfileFields() {
        if (profileNameInput != null) profileNameInput.setText("");
        if (emailInput != null) emailInput.setText("");
        if (secretInput != null) secretInput.setText("");
        if (proxyInput != null) proxyInput.setText("");
        if (authSpinner != null) authSpinner.setSelection(1);
    }

    private void refreshProfileSpinner() {
        if (profileSpinner == null) return;
        ArrayList<String> names = new ArrayList<>();
        names.add("+ New profile");
        for (JSONObject p : profiles) names.add(p.optString("name", "Profile"));
        profileSpinner.setAdapter(spinnerAdapter(names));
    }

    private void selectProfileById(String id) {
        if (profileSpinner == null) return;
        for (int i = 0; i < profiles.size(); i++) {
            if (id.equals(profiles.get(i).optString("id"))) {
                profileSpinner.setSelection(i + 1);
                return;
            }
        }
    }

    private JSONObject findProfile(String id) {
        for (JSONObject p : profiles) if (id.equals(p.optString("id"))) return p;
        return null;
    }

    private void removeDeployment(String id) {
        for (int i = deployments.size() - 1; i >= 0; i--) if (id.equals(deployments.get(i).optString("id"))) deployments.remove(i);
    }

    private void loadStore() {
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        profiles = readArray(sp.getString(KEY_PROFILES, "[]"));
        deployments = readArray(sp.getString(KEY_DEPLOYS, "[]"));
    }

    private void saveStore() {
        getSharedPreferences(PREF, MODE_PRIVATE).edit()
                .putString(KEY_PROFILES, toJsonArray(profiles).toString())
                .putString(KEY_DEPLOYS, toJsonArray(deployments).toString())
                .apply();
    }

    private ArrayList<JSONObject> readArray(String s) {
        ArrayList<JSONObject> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(s == null ? "[]" : s);
            for (int i = 0; i < arr.length(); i++) out.add(arr.getJSONObject(i));
        } catch (Exception ignored) {}
        return out;
    }

    private JSONArray toJsonArray(ArrayList<JSONObject> list) {
        JSONArray arr = new JSONArray();
        for (JSONObject o : list) arr.put(o);
        return arr;
    }

    private class CloudflareClient {
        private final JSONObject profile;
        CloudflareClient(JSONObject p) { this.profile = p; }

        JSONObject request(String method, String path, JSONObject json, byte[] raw, String contentType) throws Exception {
            URL url = new URL("https://api.cloudflare.com/client/v4" + path);
            HttpURLConnection c;
            Proxy proxy = parseProxy(profile.optString("proxy_url"));
            c = (HttpURLConnection) (proxy == null ? url.openConnection() : url.openConnection(proxy));
            c.setRequestMethod(method);
            c.setConnectTimeout(30000);
            c.setReadTimeout(60000);
            c.setRequestProperty("Accept", "application/json");
            if ("global_key".equals(profile.optString("auth_type"))) {
                c.setRequestProperty("X-Auth-Email", profile.optString("email"));
                c.setRequestProperty("X-Auth-Key", profile.optString("secret"));
            } else {
                c.setRequestProperty("Authorization", "Bearer " + profile.optString("secret"));
            }
            byte[] body = raw;
            if (json != null) {
                body = json.toString().getBytes(StandardCharsets.UTF_8);
                contentType = "application/json; charset=utf-8";
            }
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", contentType == null ? "application/octet-stream" : contentType);
                c.setFixedLengthStreamingMode(body.length);
                OutputStream os = c.getOutputStream();
                os.write(body);
                os.close();
            }
            int code = c.getResponseCode();
            InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            String txt = readAll(is);
            JSONObject res = txt == null || txt.trim().isEmpty() ? new JSONObject().put("success", true) : new JSONObject(txt);
            if (code < 200 || code >= 300 || !res.optBoolean("success", true)) {
                String msg = res.optString("message");
                JSONArray errors = res.optJSONArray("errors");
                if ((msg == null || msg.isEmpty()) && errors != null) msg = errors.toString();
                if (msg == null || msg.isEmpty()) msg = "HTTP " + code + ": " + txt;
                throw new RuntimeException(msg);
            }
            return res;
        }

        String requestText(String method, String path, JSONObject json, byte[] raw, String contentType) throws Exception {
            URL url = new URL("https://api.cloudflare.com/client/v4" + path);
            Proxy proxy = parseProxy(profile.optString("proxy_url"));
            HttpURLConnection c = (HttpURLConnection) (proxy == null ? url.openConnection() : url.openConnection(proxy));
            c.setRequestMethod(method);
            c.setConnectTimeout(30000);
            c.setReadTimeout(60000);
            c.setRequestProperty("Accept", "*/*");
            if ("global_key".equals(profile.optString("auth_type"))) {
                c.setRequestProperty("X-Auth-Email", profile.optString("email"));
                c.setRequestProperty("X-Auth-Key", profile.optString("secret"));
            } else {
                c.setRequestProperty("Authorization", "Bearer " + profile.optString("secret"));
            }
            byte[] body = raw;
            if (json != null) {
                body = json.toString().getBytes(StandardCharsets.UTF_8);
                contentType = "application/json; charset=utf-8";
            }
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", contentType == null ? "application/octet-stream" : contentType);
                c.setFixedLengthStreamingMode(body.length);
                OutputStream os = c.getOutputStream();
                os.write(body);
                os.close();
            }
            int code = c.getResponseCode();
            InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            String txt = readAll(is);
            if (code < 200 || code >= 300) throw new RuntimeException("HTTP " + code + ": " + txt);
            return txt == null ? "" : txt;
        }
    }

    private Proxy parseProxy(String proxyUrl) {
        try {
            if (proxyUrl == null || proxyUrl.trim().isEmpty()) return null;
            URI u = new URI(proxyUrl.trim());
            String host = u.getHost();
            int port = u.getPort();
            if (host == null || port <= 0) return null;
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
        } catch (Exception e) { return null; }
    }

    private Multipart buildMultipart(String metadata, byte[] worker) throws Exception {
        String boundary = "----RKhBPB" + randomHex(12);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        addPart(out, boundary, "metadata", null, "application/json", metadata.getBytes(StandardCharsets.UTF_8));
        addPart(out, boundary, "worker.js", "worker.js", "application/javascript+module", worker);
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return new Multipart(out.toByteArray(), "multipart/form-data; boundary=" + boundary);
    }

    private void addPart(ByteArrayOutputStream out, String boundary, String name, String filename, String type, byte[] data) throws Exception {
        String disp = "Content-Disposition: form-data; name=\"" + name + "\"";
        if (filename != null) disp += "; filename=\"" + filename + "\"";
        String head = "--" + boundary + "\r\n" + disp + "\r\nContent-Type: " + type + "\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static class Multipart {
        byte[] body; String contentType;
        Multipart(byte[] b, String c) { body = b; contentType = c; }
    }

    private byte[] readAsset(String name) throws Exception {
        InputStream is = getAssets().open(name);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
        is.close();
        return out.toByteArray();
    }

    private String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    private void suggestNames() {
        String base = randomName();
        if (workerInput != null) workerInput.setText(base);
        if (kvInput != null) kvInput.setText(base + "-" + pick(SECOND_WORDS));
        if (subdomainInput != null) subdomainInput.setText(safeName(pick(FIRST_WORDS) + "-" + randomHex(3), "nova-" + randomHex(3)));
    }

    private String randomName() {
        return safeName(pick(FIRST_WORDS) + "-" + pick(SECOND_WORDS) + "-" + pick(FIRST_WORDS) + "-" + randomHex(3), "nova-panel-" + randomHex(3));
    }

    private String userResourceName(String raw, int max, String fallback) {
        String s = safeName(raw, fallback);
        for (String b : BANNED) s = s.replace(b, "").replace("--", "-");
        s = s.replaceAll("^-+|-+$", "");
        if (s.isEmpty()) s = fallback;
        if (s.length() > max) s = s.substring(0, max).replaceAll("-+$", "");
        return s;
    }

    private String safeName(String raw, String fallback) {
        String s = raw == null ? "" : raw.toLowerCase(Locale.US).trim();
        s = s.replaceAll("[^a-z0-9-]+", "-").replaceAll("-+", "-").replaceAll("^-+|-+$", "");
        if (s.isEmpty()) s = fallback;
        if (!s.matches("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")) s = fallback;
        return s;
    }

    private String randomSubPath() { return "sub-" + randomHex(12); }
    private String randomSecret(String prefix) { return prefix + randomHex(18); }
    private String randomHex(int bytes) { byte[] b = new byte[bytes]; rng.nextBytes(b); StringBuilder sb = new StringBuilder(); for (byte x : b) sb.append(String.format(Locale.US, "%02x", x)); return sb.toString(); }
    private String pick(String[] arr) { return arr[rng.nextInt(arr.length)]; }
    private String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8").replace("+", "%20"); }

    private void runAsync(Task task, Done done) {
        new Thread(() -> {
            try {
                JSONObject r = task.run();
                ui.post(() -> { setStatus("Done."); done.ok(r); });
            } catch (Exception e) {
                ui.post(() -> { setStatus("Error: " + e.getMessage()); toast(e.getMessage()); });
            }
        }).start();
    }

    interface Task { JSONObject run() throws Exception; }
    interface Done { void ok(JSONObject result); }

    private void setStatus(String s) { if (statusText != null) statusText.setText(s); }
    private void toast(String s) { Toast.makeText(this, s == null ? "" : s, Toast.LENGTH_LONG).show(); }
    private void openUrl(String url) { try { startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {} }

    private TextView sectionTitle(String s) { TextView t = text(s, 20, true, TEXT); t.setPadding(0, 0, 0, dp(10)); return t; }
    private TextView label(String s) { TextView t = text(s, 12, true, MUTED); t.setPadding(0, dp(8), 0, dp(4)); return t; }
    private TextView text(String s, int sp, boolean bold, int color) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL); return t; }

    private EditText input(String hint, boolean secret) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(120, 105, 95));
        e.setTextColor(TEXT);
        e.setSingleLine(true);
        e.setPadding(dp(12), 0, dp(12), 0);
        e.setInputType(secret ? (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD) : InputType.TYPE_CLASS_TEXT);
        e.setBackground(fieldBg());
        return e;
    }

    private Button button(String s, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setSingleLine(false);
        b.setMaxLines(2);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(54));
        b.setMinimumHeight(dp(54));
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(glassBg(color, darken(color), dp(18), Color.argb(92, 255, 255, 255), 1));
        b.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.97f).scaleY(0.97f).alpha(0.82f).setDuration(80).start();
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
            return false;
        });
        b.setOnClickListener(v -> { pulse(v); l.onClick(v); });
        return b;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        c.setBackground(glassBg(CARD, Color.argb(135, 255, 220, 230), dp(26), Color.argb(105, 224, 36, 72), 1));
        c.setElevation(dp(8));
        LinearLayout.LayoutParams p = lp(-1, -2);
        p.setMargins(0, 0, 0, dp(14));
        c.setLayoutParams(p);
        return c;
    }

    private LinearLayout row() { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER); return r; }
    private View space(int w, int h) { Space s = new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(dp(w), dp(h))); return s; }
    private LinearLayout.LayoutParams lp(int w, int h) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h); p.setMargins(0, 0, 0, dp(8)); return p; }
    private LinearLayout.LayoutParams lp(int w, int h, float weight) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, weight); p.setMargins(0, 0, 0, dp(8)); return p; }


    private ArrayAdapter<String> spinnerAdapter(List<String> items) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextColor(TEXT);
                v.setTextSize(13);
                v.setPadding(dp(10), 0, dp(10), 0);
                v.setBackgroundColor(Color.TRANSPARENT);
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                v.setTextColor(TEXT);
                v.setTextSize(13);
                v.setPadding(dp(12), dp(12), dp(12), dp(12));
                v.setBackgroundColor(Color.rgb(255, 238, 242));
                return v;
            }
        };
    }

    private void styleSpinner(Spinner s) { s.setBackground(fieldBg()); s.setPadding(dp(8), 0, dp(8), 0); }
    private GradientDrawable fieldBg() { return glassBg(CARD_2, Color.argb(165, 255, 226, 234), dp(18), Color.argb(92, 224, 36, 72), 1); }
    private GradientDrawable round(int color, int radius, int strokeColor, int strokeWidth) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); g.setStroke(strokeWidth, strokeColor); return g; }
    private GradientDrawable glassBg(int start, int end, int radius, int strokeColor, int strokeWidth) { GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end}); g.setCornerRadius(radius); g.setStroke(strokeWidth, strokeColor); return g; }
    private int darken(int color) { return Color.rgb(Math.max(0, (int)(Color.red(color) * 0.62f)), Math.max(0, (int)(Color.green(color) * 0.62f)), Math.max(0, (int)(Color.blue(color) * 0.62f))); }
    private void pulse(View v) { v.animate().scaleX(1.035f).scaleY(1.035f).setDuration(90).withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()).start(); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private int getStatusBarHeight() { int id = getResources().getIdentifier("status_bar_height", "dimen", "android"); return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24); }

    private void animatePulse(View v) {
        ObjectAnimator a = ObjectAnimator.ofFloat(v, "alpha", 0.75f, 1f);
        a.setDuration(1200);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.start();
    }

    private void animateIn(View v) {
        v.setAlpha(0f);
        v.setTranslationY(dp(18));
        v.animate().alpha(1f).translationY(0).setDuration(420).setInterpolator(new DecelerateInterpolator()).start();
    }
}
