package com.cloud.control.expand.service.module.virtuallocation;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import com.cloud.control.expand.service.BuildConfig;
import com.cloud.control.expand.service.R;
import com.cloud.control.expand.service.base.BaseActivity;
import com.cloud.control.expand.service.entity.LocationInfoEntity;
import com.cloud.control.expand.service.entity.VirtualLocationInfoEntity;
import com.cloud.control.expand.service.injector.components.DaggerVirtualLocationComponent;
import com.cloud.control.expand.service.injector.modules.VirtualLocationModule;
import com.cloud.control.expand.service.interfaces.MenuCallback;
import com.cloud.control.expand.service.log.KLog;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * Author：abin
 * Date：2020/9/29
 * Description：虚拟定位
 */
public class VirtualLocationActivity extends BaseActivity<VirtualLocationPresenter> implements VirtualLocationView {

    @BindView(R.id.toolbar)
    Toolbar mToolbar;
    @BindView(R.id.wb_virtual_location)
    WebView wbVirtualLocation;
    @BindView(R.id.tv_save_location)
    TextView tvSaveLocation;

    protected boolean isOnReceivedError = false;
    //当前的经纬度、城市、IP信息
    private LocationInfoEntity mInfoEntity = new LocationInfoEntity();

    @Override
    protected int attachLayoutRes() {
        return R.layout.activity_virtual_location;
    }

    @Override
    protected void initInjector() {
        DaggerVirtualLocationComponent.builder()
                .virtualLocationModule(new VirtualLocationModule(this))
                .build()
                .inject(this);
    }

    @Override
    protected void initViews() {
        initToolBar(mToolbar, false, "");
        initWebViewSetting();
        wbVirtualLocation.loadUrl("file:android_asset/map/map.html");
    }

    @Override
    protected void updateViews(boolean isRefresh) {

    }

    @Override
    public void loadData(VirtualLocationInfoEntity virtualLocationInfoEntity) {
        try {
            //更新默认数据
            mInfoEntity.setLongitude(virtualLocationInfoEntity.getData().getLongitude());
            mInfoEntity.setLatitude(virtualLocationInfoEntity.getData().getLatitude());
            mInfoEntity.setIp((String) virtualLocationInfoEntity.getData().getIp());
            HashMap<String, Object> map = new HashMap<>();
            Map<String, String> objectHashMap = new HashMap<>();
            objectHashMap.put("longitude", TextUtils.isEmpty(virtualLocationInfoEntity.getData().getLongitude()) ? "" : virtualLocationInfoEntity.getData().getLongitude());
            objectHashMap.put("latitude", TextUtils.isEmpty(virtualLocationInfoEntity.getData().getLatitude()) ? "" : virtualLocationInfoEntity.getData().getLatitude());
            objectHashMap.put("ip", TextUtils.isEmpty((String) virtualLocationInfoEntity.getData().getIp()) ? "" : (String) virtualLocationInfoEntity.getData().getIp());
            map.put("type", "setGps");
            map.put("data", objectHashMap);
            KLog.e("loadUrl data " + new Gson().toJson(map));
            wbVirtualLocation.loadUrl("javascript:setJingWeiToJs('" + new Gson().toJson(map) + "')");
        } catch (Exception e) {
            KLog.e("e " + e.getMessage());
        }
    }

    @Override
    public void showDialog(String title, String content, String leftStr, String rightStr, boolean isBack) {
        showExpireDialog(title, content, leftStr, rightStr, new MenuCallback() {
            @Override
            public void onLeftButtonClick(Object value) {

            }

            @Override
            public void onRightButtonClick(Object value) {
                if (isBack){
                    finish();
                }
            }
        });
    }

    @Override
    public void toast(String message) {
        toastMessage(message);
    }

    @Override
    public void dialog(String title, String content, String leftStr, String rightStr) {
        showExpireDialog(title, content, leftStr, rightStr, new MenuCallback() {
            @Override
            public void onLeftButtonClick(Object value) {

            }

            @Override
            public void onRightButtonClick(Object value) {
                finish();
            }
        });
    }

    @OnClick({R.id.tv_save_location, R.id.iv_virtual_location_back})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.iv_virtual_location_back:
                finish();
                break;
            case R.id.tv_save_location:
                if (TextUtils.isEmpty(mInfoEntity.getLongitude()) || TextUtils.isEmpty(mInfoEntity.getLatitude())) {
                    toastMessage("请选择需定位的位置");
                    return;
                }
                mPresenter.startLocation(mInfoEntity.getLongitude(), mInfoEntity.getLatitude(), mInfoEntity.getCity());
                break;
        }
    }

    /**
     * 初始化webView
     */
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    protected void initWebViewSetting() {
        wbVirtualLocation.getSettings().setJavaScriptEnabled(true);
        wbVirtualLocation.getSettings().setDomStorageEnabled(true);
        wbVirtualLocation.getSettings().setAllowFileAccess(false);
        // 特别注意：5.1以上默认禁止了https和http混用，以下方式是开启
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            wbVirtualLocation.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        wbVirtualLocation.setWebViewClient(new BaseWebViewClient());
        wbVirtualLocation.addJavascriptInterface(new BaseJsInterface(), "android");
        // 允许webView debug
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        }

        initWebChromeClient();
    }

    /**
     * 基类
     */
    protected class BaseWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            isOnReceivedError = false;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return true;
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            isOnReceivedError = true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            //页面加载完成请求数据给到H5
            mPresenter.getData();
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.proceed();
        }
    }

    protected void initWebChromeClient() {
        wbVirtualLocation.setWebChromeClient(new BaseWebChromeClient());
    }

    /**
     * 基类
     */
    protected class BaseWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
        }

    }

    /**
     * js接口基类<br/>
     * 本类包含一些通用的接口
     */
    protected class BaseJsInterface {

        /**
         * 点击事件
         * H5传递过来的参数
         */
        @JavascriptInterface
        public void onClickWebView(String longitude, String latitude, String city) {
            KLog.e("onClickWebView : " + "longitude : " + longitude + ", latitude : " + latitude + ", city : " + city);
            mInfoEntity.setLongitude(longitude);
            mInfoEntity.setLatitude(latitude);
            if(!TextUtils.isEmpty(city) && city.length() > 1 && city.contains("市"))
            mInfoEntity.setCity(city.substring(0, city.length() - 1));
        }

        /**
         * 提示
         * H5调用方法
         */
        @JavascriptInterface
        public void setMessage(String message) {
            toastMessage(message);
        }
    }

}
