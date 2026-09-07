package com.nexus.cyberforge;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.SafeBrowsingResponse;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String LOCAL_URL = "file:///android_asset/index.html";
    private static final int FILE_REQUEST = 701;
    private static final int MEDIA_PERMISSION_REQUEST = 702;
    private static final int GEO_PERMISSION_REQUEST = 703;

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingPermissionRequest;
    private String pendingGeoOrigin;
    private GeolocationPermissions.Callback pendingGeoCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(7, 11, 20));
        getWindow().setNavigationBarColor(Color.rgb(7, 11, 20));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(7, 11, 20));
        setContentView(webView);

        configureWebView();
        webView.addJavascriptInterface(new NativeBridge(), "NexusNative");

        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            () -> {
                if (webView != null && webView.canGoBack()) webView.goBack();
                else finish();
            }
        );

        if (savedInstanceState == null) {
            handleIntent(getIntent());
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return routeUrl(request.getUrl());
            }

            @Override
            public void onSafeBrowsingHit(WebView view, WebResourceRequest request, int threatType, SafeBrowsingResponse callback) {
                callback.backToSafety(true);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && request.getUrl() != null && request.getUrl().toString().startsWith("file:///android_asset/")) {
                    showFatalLocalError();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.startsWith("file:///android_asset/")) injectNativeHelpers();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try {
                    startActivityForResult(Intent.createChooser(i, "Escolher arquivo"), FILE_REQUEST);
                } catch (ActivityNotFoundException e) {
                    fileCallback.onReceiveValue(null);
                    fileCallback = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                if (!isLocalOrigin(request.getOrigin())) {
                    request.deny();
                    return;
                }
                List<String> runtime = new ArrayList<>();
                for (String r : request.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        runtime.add(Manifest.permission.CAMERA);
                    }
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        runtime.add(Manifest.permission.RECORD_AUDIO);
                    }
                }
                if (runtime.isEmpty()) request.grant(request.getResources());
                else {
                    pendingPermissionRequest = request;
                    requestPermissions(runtime.toArray(new String[0]), MEDIA_PERMISSION_REQUEST);
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (!origin.startsWith("file://")) {
                    callback.invoke(origin, false, false);
                    return;
                }
                boolean fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                boolean coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                if (fine || coarse) {
                    callback.invoke(origin, true, true);
                    return;
                }
                pendingGeoOrigin = origin;
                pendingGeoCallback = callback;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, GEO_PERMISSION_REQUEST);
            }
        });
    }

    private boolean isLocalOrigin(Uri origin) {
        return origin != null && "file".equalsIgnoreCase(origin.getScheme());
    }

    private boolean routeUrl(Uri uri) {
        if (uri == null) return true;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String raw = uri.toString();
        if ("file".equals(scheme) && raw.startsWith("file:///android_asset/")) return false;
        if ("about".equals(scheme) || "blob".equals(scheme) || "data".equals(scheme)) return false;
        if ("nexus".equals(scheme)) {
            handleNexusLink(uri);
            return true;
        }
        if ("https".equals(scheme) || "http".equals(scheme) || "mailto".equals(scheme) || "tel".equals(scheme)) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
            catch (ActivityNotFoundException ignored) { }
            return true;
        }
        return true;
    }

    private void handleIntent(Intent intent) {
        Uri deep = intent == null ? null : intent.getData();
        if (deep != null && "nexus".equalsIgnoreCase(deep.getScheme())) handleNexusLink(deep);
        else webView.loadUrl(LOCAL_URL);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleNexusLink(Uri u) {
        String host = u.getHost() == null ? "" : u.getHost();
        String last = u.getLastPathSegment();
        String target = LOCAL_URL;
        if ("reel".equalsIgnoreCase(host) && last != null) target += "?view=reels&reel=" + Uri.encode(last);
        else if ("profile".equalsIgnoreCase(host) && last != null) target += "?view=profile&profile=" + Uri.encode(last);
        webView.loadUrl(target);
    }

    private void injectNativeHelpers() {
        String js = "(function(){" +
            "window.NEXUS_NATIVE_APP=true;" +
            "if(window.__nexusNativeDownloadInstalled)return;window.__nexusNativeDownloadInstalled=true;" +
            "async function send(a){try{var h=a.href||'';var name=a.download||'nexus-export';" +
            "if(h.indexOf('blob:')===0){var b=await fetch(h).then(function(r){return r.blob()});var fr=new FileReader();fr.onloadend=function(){var s=String(fr.result||'');window.NexusNative.saveBase64(name,b.type||'application/octet-stream',s.split(',')[1]||'')};fr.readAsDataURL(b);return true;}" +
            "if(h.indexOf('data:')===0){var parts=h.split(',');var meta=parts[0];var mime=(meta.match(/^data:([^;,]+)/)||[])[1]||'application/octet-stream';var data=parts.slice(1).join(',');if(meta.indexOf(';base64')<0)data=btoa(unescape(encodeURIComponent(decodeURIComponent(data))));window.NexusNative.saveBase64(name,mime,data);return true;}" +
            "}catch(e){console.warn('native download',e)}return false;}" +
            "document.addEventListener('click',function(e){var a=e.target&&e.target.closest?e.target.closest('a[download]'):null;if(!a)return;var h=a.href||'';if(h.indexOf('blob:')===0||h.indexOf('data:')===0){e.preventDefault();e.stopImmediatePropagation();send(a);}},true);" +
            "})();";
        webView.evaluateJavascript(js, null);
    }

    private void showFatalLocalError() {
        String html = "<html><meta name='viewport' content='width=device-width,initial-scale=1'><body style='margin:0;background:#070b14;color:#e5e7eb;font-family:sans-serif;display:grid;place-items:center;height:100vh'><div style='text-align:center;padding:24px'><h2>NEXUS</h2><p style='color:#94a3b8'>O conteúdo local do aplicativo não pôde ser carregado.</p></div></body></html>";
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_REQUEST || fileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                result = new Uri[count];
                for (int i = 0; i < count; i++) result[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MEDIA_PERMISSION_REQUEST && pendingPermissionRequest != null) {
            boolean granted = true;
            for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) granted = false;
            if (granted) pendingPermissionRequest.grant(pendingPermissionRequest.getResources());
            else pendingPermissionRequest.deny();
            pendingPermissionRequest = null;
        }
        if (requestCode == GEO_PERMISSION_REQUEST && pendingGeoCallback != null) {
            boolean granted = false;
            for (int r : grantResults) if (r == PackageManager.PERMISSION_GRANTED) granted = true;
            pendingGeoCallback.invoke(pendingGeoOrigin, granted, granted);
            pendingGeoOrigin = null;
            pendingGeoCallback = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("NexusNative");
            webView.destroy();
        }
        super.onDestroy();
    }

    private final class NativeBridge {
        @JavascriptInterface
        public void saveBase64(String filename, String mime, String base64) {
            runOnUiThread(() -> {
                try {
                    String safe = filename == null || filename.trim().isEmpty() ? "nexus-export" : filename.replaceAll("[\\\\/:*?\"<>|]", "_");
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, safe);
                    values.put(MediaStore.Downloads.MIME_TYPE, mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                    values.put(MediaStore.Downloads.IS_PENDING, 1);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new IllegalStateException("Não foi possível criar o arquivo");
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null) throw new IllegalStateException("Não foi possível abrir o arquivo");
                        out.write(bytes);
                    }
                    values.clear();
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);
                    Toast.makeText(MainActivity.this, "Arquivo salvo em Downloads", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Falha ao salvar arquivo", Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
