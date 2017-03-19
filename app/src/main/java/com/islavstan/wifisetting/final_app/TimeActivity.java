package com.islavstan.wifisetting.final_app;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;

import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.github.clans.fab.FloatingActionButton;
import com.islavstan.wifisetting.R;
import com.islavstan.wifisetting.adapter.WifiDaysRecAdapter;
import com.islavstan.wifisetting.model.Day;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import cc.mvdan.accesspoint.WifiApControl;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class TimeActivity extends AppCompatActivity {
    FloatingActionButton fab;
    TextView timer;
    private boolean serviceBound;
    private TimeService timeService;
    // Handler to update the UI every second when the timer is running
    private final Handler mUpdateTimeHandler = new TimeActivity.UIUpdateHandler(this);
    RecyclerView recycler;
    WifiDaysRecAdapter adapter;
    DBMethods dbMethods;
    TextView daysOnline;

    // Message type for the handler
    private final static int MSG_UPDATE_TIME = 0;
    List<Day> dayList = new ArrayList<>();
    boolean fabPressed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        dbMethods = new DBMethods(this);
        fab = (FloatingActionButton) findViewById(R.id.fab);
        timer = (TextView) findViewById(R.id.timer);


        fab.setOnClickListener(v -> {
            if (!fabPressed) {

                if (isMobileConnected(TimeActivity.this)) {//если есть интернет то запускаем таймер и вайфай раздачу

                    onWifiHotspot()
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe();

                    if (serviceBound && !timeService.isTimerRunning()) {
                        Log.d("stas", "Starting timer");
                        timeService.startTimer();
                        mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
                        fabPressed = true;
                        fab.setColorNormal(Color.parseColor("#4CAF50"));
                        fab.setColorPressed(Color.parseColor("#43A047"));
                    }


                } else showNo3gpDialog();

            } else {
                if (serviceBound && timeService.isTimerRunning()) {
                    Log.d("stas", "Stopping timer");
                    timeService.stopTimer();
                    mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
                    fabPressed = false;
                    fab.setColorNormal(Color.parseColor("#FF5722"));
                    fab.setColorPressed(Color.parseColor("#FF7043"));

                }

            }


        });


        setAdapter();
        setDaysOnline();
    }


    private Observable<Void> onWifiHotspot() {
        return Observable.create(subscriber -> {


            WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            if (wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(false);
            }
            WifiConfiguration netConfig = new WifiConfiguration();
            netConfig.SSID = "VOMER";
            netConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            netConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            netConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            netConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            try {
                Method setWifiApMethod = wifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
                boolean apstatus = (Boolean) setWifiApMethod.invoke(wifiManager, netConfig, true);

                Method isWifiApEnabledmethod = wifiManager.getClass().getMethod("isWifiApEnabled");
                while (!(Boolean) isWifiApEnabledmethod.invoke(wifiManager)) {
                }
                ;
                Method getWifiApStateMethod = wifiManager.getClass().getMethod("getWifiApState");
                int apstate = (Integer) getWifiApStateMethod.invoke(wifiManager);
                Method getWifiApConfigurationMethod = wifiManager.getClass().getMethod("getWifiApConfiguration");
                netConfig = (WifiConfiguration) getWifiApConfigurationMethod.invoke(wifiManager);
                Log.d("stas", "\nSSID:" + netConfig.SSID + "\nPassword:" + netConfig.preSharedKey + "\n");

            } catch (Exception e) {
                Log.e(this.getClass().toString(), "", e);
            }

            WifiApControl apControl = WifiApControl.getInstance(this);

            apControl.enable();
        });

    }


    private void setAdapter() {
        dbMethods.getDaysList()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(list -> dayList.addAll(list), error -> Log.d("stas", "getDaysList error = " + error.getMessage()));


        recycler = (RecyclerView) findViewById(R.id.recycler);
        adapter = new WifiDaysRecAdapter(dayList);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(this);
        recycler.setLayoutManager(mLayoutManager);
        recycler.setItemAnimator(new DefaultItemAnimator());
        recycler.setAdapter(adapter);


    }


    private void setDaysOnline() {
        daysOnline = (TextView) findViewById(R.id.daysOnline);
        dbMethods.getDaysCount()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(days -> daysOnline.setText("Дней онлайн: " + days + "/30"), error -> Log.d("stas", "getDaysCount error = " + error.getMessage()));
    }


    private void showNo3gpDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(TimeActivity.this);
        builder.setTitle("Важно!")
                .setMessage("Для работы сервиса включите мобильный интернет!")
                .setCancelable(true)
                .setNegativeButton("ОК",
                        (dialog, id) -> dialog.cancel());
        AlertDialog alert = builder.create();
        alert.show();


    }


    public boolean isMobileConnected(Context context) {
        ConnectivityManager connManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        return ((netInfo != null) && netInfo.isConnected());
    }


    @Override
    protected void onStop() {
        super.onStop();
        mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
        if (serviceBound) {
            // If a timer is active, foreground the service, otherwise kill the service
            if (timeService.isTimerRunning()) {
                timeService.foreground();
            } else {
                stopService(new Intent(this, TimeService.class));
            }
            // Unbind the service
            unbindService(connection);
            serviceBound = false;
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        Log.d("stas", "Starting and binding service");
        Intent i = new Intent(this, TimeService.class);
        startService(i);
        bindService(i, connection, 0);
    }


    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TimeService.RunServiceBinder binder = (TimeService.RunServiceBinder) service;
            timeService = binder.getService();
            serviceBound = true;
            // Ensure the service is not in the foreground when bound
            timeService.background();
            if (timeService.isTimerRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
                Log.d("stas", "sendEmptyMessage from ServiceConnection");
            }

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d("stas", "Service disconnect");
            serviceBound = false;
        }
    };

    private void updateUITimer() {
        if (serviceBound) {
            if (timeService.getTime() == null) {
                Log.d("stas", "internet = false");
                mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            } else

                timer.setText(timeService.getTime());
        }
    }


    private static class UIUpdateHandler extends Handler {

        private final static int UPDATE_RATE_MS = 1000;
        private final WeakReference<TimeActivity> activity;

        UIUpdateHandler(TimeActivity activity) {

            this.activity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message message) {
            if (MSG_UPDATE_TIME == message.what) {
                activity.get().updateUITimer();
                sendEmptyMessageDelayed(MSG_UPDATE_TIME, UPDATE_RATE_MS);
            }
        }
    }


}
