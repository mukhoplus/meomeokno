package com.mukho.meomeokno;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private static final String TAG = "MeoMeokNo";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        setupWebView();
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setSupportMultipleWindows(false);
        
        // User-Agent 설정 (일부 라이브러리 호환성용)
        String newUserAgent = webSettings.getUserAgentString().replace("wv", "");
        webSettings.setUserAgentString(newUserAgent);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // react-router 내부 라우팅을 위해 false 반환
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String urlString = request.getUrl().toString();
                
                if (urlString.contains("maps.googleapis.com/maps/api/place")) {
                    try {
                        Log.d(TAG, "Intercepting Google Places API request: " + urlString);
                        URL url = new URL(urlString);
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod(request.getMethod());
                        
                        InputStream is = connection.getInputStream();
                        String contentType = connection.getContentType();
                        if (contentType != null && contentType.contains(";")) {
                            contentType = contentType.split(";")[0];
                        }
                        
                        Map<String, String> responseHeaders = new HashMap<>();
                        responseHeaders.put("Access-Control-Allow-Origin", "*");
                        responseHeaders.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                        responseHeaders.put("Access-Control-Allow-Headers", "Content-Type, Authorization");
                        
                        return new WebResourceResponse(
                            contentType != null ? contentType : "application/json",
                            "UTF-8",
                            connection.getResponseCode(),
                            connection.getResponseMessage(),
                            responseHeaders,
                            is
                        );
                    } catch (Exception e) {
                        Log.e(TAG, "Error in CORS proxy", e);
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("WebViewConsole", consoleMessage.message() + " -- From line "
                        + consoleMessage.lineNumber() + " of "
                        + consoleMessage.sourceId());
                return true;
            }
        });

        webView.addJavascriptInterface(new WebAppInterface(), "Android");
        webView.loadUrl("https://meomeokno.vercel.app/");
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void requestLocation() {
            Log.d(TAG, "Web requested location");
            runOnUiThread(() -> checkLocationPermissionAndGetLocation());
        }
    }

    private void checkLocationPermissionAndGetLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        double lat = location.getLatitude();
                        double lng = location.getLongitude();
                        Log.d(TAG, "Location obtained: " + lat + ", " + lng);
                        
                        // JS 함수가 준비될 때까지 최대 2초간 0.5초 간격으로 재시도하는 스크립트
                        String script = String.format(
                            "(function() {" +
                            "  var count = 0;" +
                            "  var interval = setInterval(function() {" +
                            "    if (typeof window.onNativeLocation === 'function') {" +
                            "      window.onNativeLocation({ lat: %f, lng: %f });" +
                            "      clearInterval(interval);" +
                            "    } else if (count > 4) {" + // 2초(0.5s * 4) 동안 없으면 포기
                            "      console.error('window.onNativeLocation is still not defined');" +
                            "      clearInterval(interval);" +
                            "    }" +
                            "    count++;" +
                            "  }, 500);" +
                            "})();", lat, lng);
                        
                        webView.evaluateJavascript(script, null);
                    } else {
                        Log.w(TAG, "Location is null");
                        Toast.makeText(MainActivity.this, "위치 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show();
                    }
                });
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkLocationPermissionAndGetLocation();
            } else {
                Toast.makeText(this, "위치 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}

