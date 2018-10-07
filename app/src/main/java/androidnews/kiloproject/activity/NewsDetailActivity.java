package androidnews.kiloproject.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebSettings;
import android.widget.ProgressBar;

import com.blankj.utilcode.util.NetworkUtils;
import com.blankj.utilcode.util.SPUtils;
import com.blankj.utilcode.util.SnackbarUtils;
import com.blankj.utilcode.util.StringUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.blankj.utilcode.util.Utils;
import com.google.gson.reflect.TypeToken;
import com.scwang.smartrefresh.layout.SmartRefreshLayout;
import com.scwang.smartrefresh.layout.api.RefreshLayout;
import com.scwang.smartrefresh.layout.listener.OnRefreshListener;
import com.zhouyou.http.EasyHttp;
import com.zhouyou.http.callback.SimpleCallBack;
import com.zhouyou.http.exception.ApiException;

import java.util.ArrayList;
import java.util.List;

import androidnews.kiloproject.R;
import androidnews.kiloproject.bean.data.CacheNews;
import androidnews.kiloproject.bean.net.NewsDetailData;
import androidnews.kiloproject.system.base.BaseActivity;
import androidnews.kiloproject.widget.NestedScrollWebView;
import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

import static androidnews.kiloproject.bean.data.CacheNews.CACHE_COLLECTION;
import static androidnews.kiloproject.bean.data.CacheNews.CACHE_HISTORY;
import static androidnews.kiloproject.system.AppConfig.getNewsDetailA;
import static androidnews.kiloproject.system.AppConfig.getNewsDetailB;
import static androidnews.kiloproject.system.AppConfig.isNightMode;

public class NewsDetailActivity extends BaseActivity {

    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.appbar)
    AppBarLayout appbar;
    @BindView(R.id.progress)
    ProgressBar progress;
    @BindView(R.id.web_news)
    NestedScrollWebView webView;
    @BindView(R.id.refreshLayout)
    SmartRefreshLayout refreshLayout;
    private String html;
    private NewsDetailData currentData;
    private boolean isStar = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        ButterKnife.bind(this);
        initView();
        initStateBar(R.color.main_background, true);
    }

    @Override
    protected void initSlowly() {
        String docid = getIntent().getStringExtra("docid");
        if (!TextUtils.isEmpty(docid)) {
            EasyHttp.get(getNewsDetailA + docid + getNewsDetailB)
                    .readTimeOut(30 * 1000)//局部定义读超时
                    .writeTimeOut(30 * 1000)
                    .connectTimeout(30 * 1000)
                    .timeStamp(true)
                    .execute(new SimpleCallBack<String>() {
                        @Override
                        public void onError(ApiException e) {
                            SnackbarUtils.with(toolbar).setMessage(getString(R.string.load_fail) + e.getMessage()).showError();
                            progress.setVisibility(View.GONE);
                            refreshLayout.finishRefresh();
                        }

                        @Override
                        public void onSuccess(String response) {
                            progress.setVisibility(View.GONE);
                            if (!TextUtils.isEmpty(response) || TextUtils.equals(response, "{}")) {
                                String jsonNoHeader = response.substring(20, response.length());
                                String jsonFine = jsonNoHeader.substring(0, jsonNoHeader.length() - 1);

                                if (response.contains("点这里升级")) {
                                    ToastUtils.showShort(getString(R.string.server_fail));
                                    finish();
                                    return;
                                }
                                try {
                                    currentData = gson.fromJson(jsonFine, NewsDetailData.class);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    ToastUtils.showShort(getString(R.string.server_fail) + e.getMessage());
                                    finish();
                                }
                                Observable.create(new ObservableOnSubscribe<Boolean>() {
                                    @Override
                                    public void subscribe(ObservableEmitter<Boolean> e) throws Exception {
                                        e.onNext(checkStar(false));
                                    }
                                }).subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe(new Consumer<Boolean>() {
                                            @Override
                                            public void accept(Boolean aBoolean) throws Exception {
                                                if (aBoolean) {
                                                    isStar = true;
                                                    toolbar.getMenu().getItem(3).setIcon(R.drawable.ic_star_ok);
                                                }
                                                refreshLayout.finishRefresh();
                                            }
                                        });
                                if (webView != null) {
                                    initWeb();
                                    loadUrl();
                                }
                            } else {
                                refreshLayout.finishRefresh();
                                progress.setVisibility(View.GONE);
                                SnackbarUtils.with(toolbar).setMessage(getString(R.string.load_fail)).showError();
                            }
                        }
                    });
        } else {
            String html = getIntent().getStringExtra("htmlText");
            if (isNightMode)
                html.replace("<body>", "<body bgcolor=\"#000000\" body text=\"white\">");
            if (!StringUtils.isEmpty(html)) {
                progress.setVisibility(View.GONE);
                initWeb();
                getSupportActionBar().setTitle(R.string.news);
                webView.loadData(html, "text/html; charset=UTF-8", null);
            } else {
                SnackbarUtils.with(toolbar).setMessage(getString(R.string.load_fail)).showError();
            }
            progress.setVisibility(View.GONE);
        }
    }

    private void initView() {
        initToolbar(toolbar, true);
        getSupportActionBar().setTitle("正在加载...");
        //menu item点击事件监听
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Intent intent;
                switch (item.getItemId()) {
                    case R.id.action_share:
                        intent = new Intent();
                        intent.setAction(Intent.ACTION_SEND);//设置分享行为
                        intent.setType("text/plain");//设置分享内容的类型
                        intent.putExtra(Intent.EXTRA_SUBJECT, currentData.getTitle());//添加分享内容标题
                        intent.putExtra(Intent.EXTRA_TEXT, "【" + currentData.getTitle()
                                + "】 " + currentData.getShareLink());//添加分享内容
                        //创建分享的Dialog
                        intent = Intent.createChooser(intent, getString(R.string.action_share));
                        startActivity(intent);
                        break;
                    case R.id.action_star:
                        if (isStar) {
                            item.setIcon(R.drawable.ic_star_no);
                            checkStar(true);
                            SnackbarUtils.with(toolbar).setMessage(getString(R.string.star_no)).showSuccess();
                            isStar = false;
                        } else {
                            item.setIcon(R.drawable.ic_star_ok);
                            saveCache(CACHE_COLLECTION);
                            SnackbarUtils.with(toolbar).setMessage(getString(R.string.star_yes)).showSuccess();
                            isStar = true;
                        }
                        break;
                    case R.id.action_comment:
                        if (currentData == null || TextUtils.isEmpty(currentData.getReplyBoard()) || TextUtils.isEmpty(currentData.getDocid())) {
                            SnackbarUtils.with(toolbar).setMessage(getString(R.string.no_comment)).showError();
                            break;
                        }
                        intent = new Intent(mActivity, CommentActivity.class);
                        intent.putExtra("board", currentData.getReplyBoard());
                        intent.putExtra("docid", currentData.getDocid());
                        startActivity(intent);
                        break;
                    case R.id.action_link:
                        ClipboardManager cm = (ClipboardManager) Utils.getApp().getSystemService(Context.CLIPBOARD_SERVICE);
                        //noinspection ConstantConditions
                        cm.setPrimaryClip(ClipData.newPlainText("link", currentData.getShareLink()));
                        SnackbarUtils.with(toolbar).setMessage(getString(R.string.action_link)
                                + " " + getString(R.string.successfully)).showSuccess();
                        break;
                    case R.id.action_browser:
                        Uri uri = Uri.parse(currentData.getShareLink());
                        intent = new Intent(Intent.ACTION_VIEW, uri);
                        startActivity(intent);
                        break;
                }
                return false;
            }
        });
        refreshLayout.setOnRefreshListener(new OnRefreshListener() {
            @Override
            public void onRefresh(@NonNull RefreshLayout refreshLayout) {
                initSlowly();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (NetworkUtils.isConnected())
            getMenuInflater().inflate(R.menu.detail_items, menu);//加载menu布局
        return true;
    }

    private void initWeb() {
        WebSettings webSetting = webView.getSettings();
        webSetting.setJavaScriptEnabled(true);
        webSetting.setDomStorageEnabled(true);
        webSetting.setAllowFileAccess(true);
        webSetting.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        webSetting.setAppCacheEnabled(true);
        webSetting.setDatabaseEnabled(true);
        webSetting.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
    }

    private void loadUrl() {
        String colorBody = isNightMode ? "<body bgcolor=\"#000000\" body text=\"white\">" : "<body>";
        html = "<!DOCTYPE html>" +
                "<html lang=\"zh\">" +
                "<head>" +
                "<meta charset=\"UTF-8\" />" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />" +
                "<meta http-equiv=\"X-UA-Compatible\" content=\"ie=edge\" />" +
                "<title>Document</title>" +
                "<style>" +
                "body img{" +
                "width: 100%;" +
                "height: 100%;" +
                "}" +
                "body video{" +
                "width: 100%;" +
                "height: 100%;" +
                "}" +
                "div{width:100%;height:30px;} #from{width:auto;float:left;color:gray;} #time{width:auto;float:right;color:gray;}" +
                "</style>" +
                "</head>" +
                colorBody
                + "<p><h2>" + currentData.getTitle() + "</h2></p>"
                + "<p><div><div id=\"from\">" + currentData.getSource() +
                "</div><div id=\"time\">" + currentData.getPtime() + "</div></div></p>"
                + currentData.getBody() + "</body>" +
                "</html>";
        if (currentData.getVideo() != null) {
            for (NewsDetailData.VideoBean videoBean : currentData.getVideo()) {
                html = html.replace(videoBean.getRef(),
                        "<video src=\"" + videoBean.getMp4_url() +
                                "\" controls=\"controls\" poster=\"" + videoBean.getCover() + "\"></video>");
            }
        }
        if (currentData.getImg() != null) {
            for (NewsDetailData.ImgBean imgBean : currentData.getImg()) {
                html = html.replace(imgBean.getRef(), "<img src=\"" + imgBean.getSrc() + "\"/>");
            }
        }
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        webView.loadData(html, "text/html; charset=UTF-8", null);
        new Thread(new Runnable() {
            @Override
            public void run() {
                saveCache(CACHE_HISTORY);
            }
        }).start();
    }


    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            // 如果先调用destroy()方法，则会命中if (isDestroyed()) return;这一行代码，需要先onDetachedFromWindow()，再
            // destory()
            ViewParent parent = webView.getParent();
            if (parent != null) {
                ((ViewGroup) parent).removeView(webView);
            }

            webView.stopLoading();
            // 退出时调用此方法，移除绑定的服务，否则某些特定系统会报错
            webView.getSettings().setJavaScriptEnabled(false);
            webView.clearHistory();
            webView.clearView();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void saveCache(int type) {
        String hisJson = SPUtils.getInstance().getString(type + "", "");
        List<CacheNews> list;
        if (TextUtils.isEmpty(hisJson)) {
            list = new ArrayList<>();
        } else {
            list = gson.fromJson(hisJson, new TypeToken<List<CacheNews>>() {
            }.getType());
            for (CacheNews cacheNews : list) {
                if (cacheNews.getDocid().equals(currentData.getDocid()))
                    return;
            }
        }

        CacheNews cacheNews = new CacheNews(currentData.getTitle(),
                currentData.getRecImgsrc(),
                currentData.getSource(),
                currentData.getDocid(),
                html);
        list.add(cacheNews);

        String saveJson = gson.toJson(list, new TypeToken<List<CacheNews>>() {
        }.getType());
        SPUtils.getInstance().put(type + "", saveJson);
    }

    private boolean checkStar(boolean isClear) {
        String hisJson = SPUtils.getInstance().getString(CACHE_COLLECTION + "", "");
        List<CacheNews> list;
        if (!TextUtils.isEmpty(hisJson)) {
            list = gson.fromJson(hisJson, new TypeToken<List<CacheNews>>() {
            }.getType());
            for (CacheNews cache : list) {
                if (TextUtils.equals(cache.getDocid(), currentData.getDocid())) {
                    if (isClear) {
                        list.remove(cache);
                        String saveJson = gson.toJson(list, new TypeToken<List<CacheNews>>() {
                        }.getType());
                        SPUtils.getInstance().put(CACHE_COLLECTION + "", saveJson);
                    }
                    return true;
                }
            }
        }
        return false;
    }
}