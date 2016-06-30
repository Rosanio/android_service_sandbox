package com.epicodus.serviceexample;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private static final String PREFERENCE_NAME = "MyPreferenceFileName";

    Button btnStart, btnStop, btnBind, btnUnbind, btnUpBy1, btnUpBy10;
    TextView textStatus, textIntValue, textStrValue;
    Messenger mService = null;
    boolean mIsBound;
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    private SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mEditor;

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MyService.MSG_SET_INT_VALUE:
                    textIntValue.setText("Int Message: " + msg.arg1);
                    break;
                case MyService.MSG_SET_STR_VALUE:
                    String str1 = msg.getData().getString("str1");
                    textStrValue.setText("Str Message: " + str1);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,IBinder service) {
            mService = new Messenger(service);
            textStatus.setText("Attached.");
            try {
                Message msg = Message.obtain(null, MyService.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            mService = null;
            textStatus.setText("Disconnected.");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = (Button)findViewById(R.id.btnStart);
        btnStop = (Button)findViewById(R.id.btnStop);
        btnBind = (Button)findViewById(R.id.btnBind);
        btnUnbind = (Button)findViewById(R.id.btnUnbind);
        textStatus = (TextView)findViewById(R.id.textStatus);
        textIntValue = (TextView)findViewById(R.id.textIntValue);
        textStrValue = (TextView)findViewById(R.id.textStrValue);
        btnUpBy1 = (Button)findViewById(R.id.btnUpby1);
        btnUpBy10 = (Button)findViewById(R.id.btnUpby10);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mEditor = mSharedPreferences.edit();

        btnStart.setOnClickListener(btnStartListener);
        btnStop.setOnClickListener(btnStopListener);
        btnBind.setOnClickListener(btnBindListener);
        btnUnbind.setOnClickListener(btnUnbindListener);
        btnUpBy1.setOnClickListener(btnUpby1Listener);
        btnUpBy10.setOnClickListener(btnUpby10Listener);

        startService(new Intent(MainActivity.this, MyService.class));

        restoreMe(savedInstanceState);

        CheckIfServiceIsRunning();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("textStatus", textStatus.getText().toString());
        outState.putString("textIntValue", textIntValue.getText().toString());
        outState.putString("textStrValue", textStrValue.getText().toString());
    }

    private void restoreMe(Bundle state) {
        if(state != null) {
            textStatus.setText(state.getString("textStatus"));
            textIntValue.setText(state.getString("textIntValue"));
            textStrValue.setText(state.getString("textStrValue"));
        }
    }

    private void CheckIfServiceIsRunning() {
        if(MyService.isRunning()) {
            doBindService();
        }
    }

    private View.OnClickListener btnStartListener = new View.OnClickListener() {
        public void onClick(View v) {
            startService(new Intent(MainActivity.this, MyService.class));
        }
    };

    private View.OnClickListener btnStopListener = new View.OnClickListener() {
        public void onClick(View v) {
            stopService(new Intent(MainActivity.this, MyService.class));
        }
    };

    private View.OnClickListener btnBindListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            doBindService();
        }
    };

    private View.OnClickListener btnUnbindListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            doUnbindService();
        }
    };

    private View.OnClickListener btnUpby1Listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            sendMessageToService(1);
        }
    };

    private View.OnClickListener btnUpby10Listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            sendMessageToService(10);
        }
    };

    private void sendMessageToService(int intValueToSend) {
        if(mIsBound) {
            if(mService != null) {
                try {
                    Message msg = Message.obtain(null, MyService.MSG_SET_INT_VALUE, intValueToSend, 0);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch(RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    void doBindService() {
        bindService(new Intent(this, MyService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
        textStatus.setText("Binding.");
    }

    void doUnbindService() {
        if(mIsBound) {
            if(mService != null) {
                try {
                    Message msg = Message.obtain(null, MyService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch(RemoteException e) {
                    e.printStackTrace();
                }
            }
            unbindService(mConnection);
            mIsBound = false;
            textStatus.setText("Unbinding.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            doUnbindService();
        } catch (Throwable t) {
            Log.e("MainActivity", "Failed to unbind from the service", t);
        }
    }
}
