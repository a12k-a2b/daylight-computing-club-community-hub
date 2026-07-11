package club.daylight.shell;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;

/**
 * Thin shell: serves the bundled web app in assets/ from a private https://
 * origin and wires up everything a bare WebView silently drops — back
 * button, rotation state, file pickers, downloads, external links.
 */
public class MainActivity extends Activity {

    /** The app's private origin. Requests to it are answered from assets/. */
    private static final String HOST = "dish.local";
    private static final String START_URL = "https://" + HOST + "/index.html";

    /** Set true for reading/reference dishes — on a reflective screen in
     *  daylight the backlight is off, so keeping the screen on is cheap. */
    private static final boolean KEEP_SCREEN_ON = false;

    private static final int PICK_FILE = 1;
    private static final int VOICE = 2;

    private WebView web;
    private ValueCallback<Uri[]> pendingFileChooser;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (KEEP_SCREEN_ON) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        // Android's system font-size setting scales WebView text via textZoom
        // and shatters layouts. Pin it; make your CSS generously sized instead.
        s.setTextZoom(100);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        // Assets are served by interception below — file access stays off.
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                String path = u.getPath() == null || u.getPath().equals("/") ? "/index.html" : u.getPath();
                try {
                    InputStream in = getAssets().open(path.substring(1));
                    return new WebResourceResponse(mime(path), "utf-8", in);
                } catch (Exception e) {
                    return new WebResourceResponse("text/plain", "utf-8",
                            new ByteArrayInputStream(("404 " + path).getBytes()));
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                // Our origin loads in place; everything else opens in Chrome.
                if (HOST.equals(req.getUrl().getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, req.getUrl()));
                } catch (Exception ignored) {}
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                if (pendingFileChooser != null) pendingFileChooser.onReceiveValue(null);
                pendingFileChooser = cb;
                try {
                    startActivityForResult(params.createIntent(), PICK_FILE);
                } catch (Exception e) {
                    pendingFileChooser = null;
                    return false;
                }
                return true;
            }
        });

        // A bare WebView drops downloads on the floor — the classic dead
        // "export" button. data: URLs are written straight to Downloads;
        // http(s) goes through DownloadManager. (blob: needs a JS bridge —
        // see the template README.)
        web.setDownloadListener((url, ua, contentDisposition, mimetype, len) -> {
            try {
                if (url.startsWith("data:")) {
                    saveDataUrl(url, android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype));
                } else if (url.startsWith("http")) {
                    DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                    r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                            android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype));
                    ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(r);
                }
                Toast.makeText(this, "Saved to Downloads", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Couldn't save file", Toast.LENGTH_SHORT).show();
            }
        });

        // Voice bridge: the Web Speech API (SpeechRecognition) doesn't exist
        // in WebView, so the shell exposes the system recognizer instead.
        // Pair with daylight-voice.js in assets/ for one API in both worlds.
        web.addJavascriptInterface(new VoiceBridge(), "DaylightVoice");

        setContentView(web);
        if (state != null) web.restoreState(state);
        else web.loadUrl(START_URL);
    }

    private class VoiceBridge {
        @JavascriptInterface
        public boolean available() {
            return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .resolveActivity(getPackageManager()) != null;
        }

        /** Opens the system speech dialog (it owns the mic permission), then
         *  calls window.__daylightVoiceResult(transcript, error) in the page. */
        @JavascriptInterface
        public void listen(String lang) {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            if (lang != null && !lang.isEmpty())
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
            runOnUiThread(() -> {
                try {
                    startActivityForResult(i, VOICE);
                } catch (Exception e) {
                    voiceResult(null, "no speech recognizer on this device");
                }
            });
        }
    }

    private void voiceResult(String transcript, String error) {
        String t = transcript == null ? "null" : org.json.JSONObject.quote(transcript);
        String e = error == null ? "null" : org.json.JSONObject.quote(error);
        web.evaluateJavascript("window.__daylightVoiceResult && window.__daylightVoiceResult(" + t + "," + e + ")", null);
    }

    /** data:[<mime>][;base64],<payload> → a real file in Downloads. */
    private void saveDataUrl(String url, String name) throws Exception {
        int comma = url.indexOf(',');
        String meta = url.substring(5, comma);
        byte[] bytes = meta.endsWith(";base64")
                ? Base64.decode(url.substring(comma + 1), Base64.DEFAULT)
                : URLDecoder.decode(url.substring(comma + 1), "utf-8").getBytes("utf-8");
        String mime = meta.contains(";") ? meta.substring(0, meta.indexOf(';')) : meta;
        if (mime.isEmpty()) mime = "application/octet-stream";

        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
        cv.put(MediaStore.Downloads.MIME_TYPE, mime);
        Uri dest = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
        try (OutputStream out = getContentResolver().openOutputStream(dest)) {
            out.write(bytes);
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        if (code == PICK_FILE && pendingFileChooser != null) {
            pendingFileChooser.onReceiveValue(
                    WebChromeClient.FileChooserParams.parseResult(result, data));
            pendingFileChooser = null;
        } else if (code == VOICE) {
            java.util.ArrayList<String> r = data == null ? null
                    : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result == RESULT_OK && r != null && !r.isEmpty()) voiceResult(r.get(0), null);
            else voiceResult(null, "cancelled");
        } else {
            super.onActivityResult(code, result, data);
        }
    }

    @Override
    public void onBackPressed() {
        // Back steps through the app's history; only the root exits.
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    private static String mime(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html";
        if (p.endsWith(".js") || p.endsWith(".mjs")) return "text/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json") || p.endsWith(".webmanifest")) return "application/json";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".gif")) return "image/gif";
        if (p.endsWith(".ico")) return "image/x-icon";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".woff")) return "font/woff";
        if (p.endsWith(".ttf")) return "font/ttf";
        if (p.endsWith(".wasm")) return "application/wasm";
        if (p.endsWith(".mp3")) return "audio/mpeg";
        if (p.endsWith(".ogg")) return "audio/ogg";
        if (p.endsWith(".mp4")) return "video/mp4";
        if (p.endsWith(".txt") || p.endsWith(".md")) return "text/plain";
        return "application/octet-stream";
    }
}
