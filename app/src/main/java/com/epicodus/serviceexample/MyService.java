package com.epicodus.serviceexample;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MyService extends Service implements SensorEventListener {
    private static final String PREFERENCE_NAME = "MyPreferenceFileName";

    private NotificationManager nm;
    private Timer timer = new Timer();
    private int counter = 0, incrementBy = 1;
    private static boolean isRunning = false;

    private SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mEditor;
    private boolean pref_checked;

    ArrayList<Messenger> mClients = new ArrayList<Messenger>();
    int mValue = 0;
    static final int MSG_REGISTER_CLIENT = 1;
    static final int MSG_UNREGISTER_CLIENT = 2;
    static final int MSG_SET_INT_VALUE = 3;
    static final int MSG_SET_STR_VALUE = 4;
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;

    private long lastUpdate;
    private float last_x;
    private float last_y;
    private float last_z;

    private ArrayList<Float> speedData;

    private int speedCounted;
    private float grossTotalSpeed;
    private float totalAverageSpeed;
    private int stepCount;

    private boolean checkSpeedDirection;

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MSG_REGISTER_CLIENT:
                    mClients.add(msg.replyTo);
                    break;
                case MSG_UNREGISTER_CLIENT:
                    mClients.remove(msg.replyTo);
                    break;
                case MSG_SET_INT_VALUE:
                    incrementBy = msg.arg1;
                    break;
                default:
                    super.handleMessage(msg);

            }
        }
    }

    private void sendMessageToUI(int intValueToSend) {
        for(int i = mClients.size()-1; i >= 0; i--) {
            try {
                mClients.get(i).send(Message.obtain(null, MSG_SET_INT_VALUE, intValueToSend, 0));
//                Bundle b = new Bundle();
//                b.putString("str1", "ab" + intValueToSend + "cd");
//                Message msg = Message.obtain(null, MSG_SET_STR_VALUE);
//                msg.setData(b);
//                mClients.get(i).send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
                mClients.remove(i);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("MyService", "ServiceStarted.");
        showNotification();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                onTimerTick();
            }
        }, 0, 100L);
        isRunning = true;

        mSharedPreferences = getApplicationContext().getSharedPreferences(PREFERENCE_NAME, 0);
        mEditor = mSharedPreferences.edit();

        mSensorManager = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);

        lastUpdate = 0;
        last_x = 0;
        last_y = 0;
        last_z = 0;

        speedData = new ArrayList<>();

        speedCounted = mSharedPreferences.getInt("speedCounted", 1);
        grossTotalSpeed = mSharedPreferences.getFloat("grossTotalSpeed", 0);
        stepCount = mSharedPreferences.getInt("stepsTaken", 0);

        totalAverageSpeed = 1;

        checkSpeedDirection = true;
    }

    private void showNotification() {
        nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        CharSequence text = getText(R.string.service_started);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        Notification.Builder builder = new Notification.Builder(this);
        builder.setAutoCancel(false);
        builder.setTicker(text);
        builder.setContentTitle(getText(R.string.service_label));
        builder.setContentText(text);
        builder.setSmallIcon(R.drawable.icon);
        builder.setContentIntent(contentIntent);
        builder.setOngoing(true);

        Notification notification = builder.build();
        nm.notify(R.string.service_started, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("MyService", "received start id" + startId + ": " + intent + "  " + flags);
        return START_STICKY;
    }

    public static boolean isRunning() {
        return isRunning;
    }

    private void onTimerTick() {
        Log.i("TimerTick", "Timer doing work." + counter);
        try {
            counter += incrementBy;
            sendMessageToUI(counter);
        } catch (Throwable t) {
            Log.e("TimerTick", "Timer Tick Failed.", t);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor mySensor = sensorEvent.sensor;

        if (mySensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if(System.currentTimeMillis()-lastUpdate > 20) {

                float x = sensorEvent.values[0];
                float y = sensorEvent.values[1];
                float z = sensorEvent.values[2];

                long curTime = System.currentTimeMillis();

                long diffTime = (curTime - lastUpdate);
                lastUpdate = curTime;

                float speed = Math.abs(x + y + z - last_x - last_y - last_z) / diffTime * 1000;
                if (speedData.size() < 20) {
                    speedData.add(speed);
                } else {
                    speedData.remove(0);
                    speedData.add(speed);
                }
                float localGrossSpeed = 0;
                float localAverageSpeed;
                for (int i = 0; i < speedData.size(); i++) {
                    localGrossSpeed += speedData.get(i);
                }
                localAverageSpeed = localGrossSpeed / speedData.size();

                if (localAverageSpeed > 20) {
                    speedCounted++;
                    grossTotalSpeed = grossTotalSpeed + localAverageSpeed;
                    totalAverageSpeed = (grossTotalSpeed) / speedCounted;

                }

                if (totalAverageSpeed > 15 && speedCounted > 200) {

                    if (checkSpeedDirection) {
                        if (localAverageSpeed > totalAverageSpeed) {
                            checkSpeedDirection = false;
                            stepCount++;
                            Log.d("step taken", stepCount + "");
                            mEditor.putInt("stepsTaken", stepCount).commit();
                            mEditor.putFloat("grossTotalSpeed", grossTotalSpeed).commit();
                            mEditor.putInt("speedCounted", speedCounted).commit();
                        }
                    } else {
                        if (localAverageSpeed < totalAverageSpeed) {
                            checkSpeedDirection = true;
                            stepCount++;
                            mEditor.putInt("stepsTaken", stepCount).commit();
                            mEditor.putFloat("grossTotalSpeed", grossTotalSpeed).commit();
                            mEditor.putInt("speedCounted", speedCounted).commit();
                            Log.d("step taken", mSharedPreferences.getInt("stepsTaken", 0)+"");

                        }
                    }
                }

                last_x = x;
                last_y = y;
                last_z = z;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(timer != null) {
            timer.cancel();
        }
        counter = 0;
        stepCount = 0;
        nm.cancel(R.string.service_started);
        Log.i("MyService", "Service Stopped.");
        isRunning = false;
        mSensorManager.unregisterListener(this);
    }
}
