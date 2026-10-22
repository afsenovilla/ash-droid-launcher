package io.github.afsenovilla.ashdroid.launcher;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Checks GitHub for a newer stable release and installs it in place
 * (same signing key, so saves and settings are kept).
 * Dev builds count as older than the release with the same number
 * (0.2.1-dev.7 &lt; 0.2.1), so they are offered the stable version too.
 */
final class Updater
{
    private static final String API = "https://api.github.com/repos/afsenovilla/ash-droid-launcher/releases/latest";
    static final String ACTION_STATUS = "io.github.afsenovilla.ashdroid.UPDATE_STATUS";

    static final class Info
    {
        final String version;
        final String apkUrl;
        final long size;

        Info(String version, String apkUrl, long size)
        {
            this.version = version;
            this.apkUrl = apkUrl;
            this.size = size;
        }
    }

    interface Progress
    {
        void onProgress(long done, long total);
    }

    private Updater() {}

    /** Blocking; returns the newer release or null (also on any network error). */
    static Info check(String currentVersion)
    {
        try {
            HttpURLConnection c = open(API);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            if (c.getResponseCode() != 200) return null;
            String body;
            try (InputStream in = c.getInputStream()) {
                body = readAll(in);
            }
            JSONObject o = new JSONObject(body);
            if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null;
            String tag = o.optString("tag_name", "").replaceFirst("^v", "");
            if (tag.isEmpty() || compare(tag, currentVersion) <= 0) return null;
            JSONArray assets = o.optJSONArray("assets");
            if (assets == null) return null;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (a.optString("name").endsWith(".apk")) {
                    return new Info(tag, a.optString("browser_download_url"), a.optLong("size", -1));
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Downloads the APK straight into a PackageInstaller session and commits it (system asks to confirm). */
    static void install(Context ctx, Info info, Progress progress) throws IOException
    {
        PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(ctx.getPackageName());
        int id = pi.createSession(params);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            HttpURLConnection c = open(info.apkUrl);
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            long total = c.getContentLengthLong() > 0 ? c.getContentLengthLong() : info.size;
            try (InputStream in = c.getInputStream();
                 OutputStream out = s.openWrite("update.apk", 0, total > 0 ? total : -1)) {
                byte[] buf = new byte[1 << 16];
                long done = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    progress.onProgress(done, total);
                }
                s.fsync(out);
            }
            Intent i = new Intent(ctx, StatusReceiver.class).setAction(ACTION_STATUS);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT
                    | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent pend = PendingIntent.getBroadcast(ctx, id, i, flags);
            s.commit(pend.getIntentSender());
        } catch (IOException | RuntimeException e) {
            try {
                pi.abandonSession(id);
            } catch (RuntimeException ignored) {
            }
            throw e;
        }
    }

    /** Receives the installer's answer: shows the system confirmation, or the error. */
    public static final class StatusReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context ctx, Intent intent)
        {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(confirm);
                }
            } else if (status != PackageInstaller.STATUS_SUCCESS) {
                String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Toast.makeText(ctx, ctx.getString(io.github.afsenovilla.ashdroid.R.string.update_failed)
                        + (msg != null ? "\n" + msg : ""), Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Semantic-ish compare: 0.2.1 &gt; 0.2.1-dev.7 &gt; 0.2.0. */
    static int compare(String a, String b)
    {
        String[] pa = a.split("-", 2), pb = b.split("-", 2);
        String[] na = pa[0].split("\\."), nb = pb[0].split("\\.");
        for (int i = 0; i < Math.max(na.length, nb.length); i++) {
            int x = i < na.length ? num(na[i]) : 0, y = i < nb.length ? num(nb[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        boolean preA = pa.length > 1, preB = pb.length > 1;
        if (preA != preB) return preA ? -1 : 1;
        return 0;
    }

    private static int num(String s)
    {
        try {
            return Integer.parseInt(s.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static HttpURLConnection open(String url) throws IOException
    {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "ash-droid-launcher");
        return c;
    }

    private static String readAll(InputStream in) throws IOException
    {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toString("UTF-8");
    }
}
