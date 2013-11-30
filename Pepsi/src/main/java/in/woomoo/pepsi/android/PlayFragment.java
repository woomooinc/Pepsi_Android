package in.woomoo.pepsi.android;

import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import de.greenrobot.event.EventBus;
import in.woomoo.pepsi.android.event.ConnectedEvent;
import in.woomoo.pepsi.android.event.GameScoreEvent;

/**
 * Created by Jason on 11/30/13.
 */
public class PlayFragment extends Fragment {
    // The following are used for the shake detection
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private ShakeDetector mShakeDetector;
    private Vibrator vibration;

    private TextView mShakeStatus;
    private TextView mResult;
    private TextView mPreCountView;

    private ImageView mResultIcon;
    private SoundPool soundPool;

    private int soundID;
    private float volume;

    private TextView mTime;
    private int mShakeCount = 0;
    private int mCurrentTime = 10;
    private int mPreCount = 3;

    private boolean mShakeEnable = false;

    private final int MESSAGE_PRECOUNT = 0;
    private final int MESSAGE_TIME = 1;
    private final int MESSAGE_RESULT = 2;


    private String TAG = ServerListFragment.class.getSimpleName();

    private PepsiService mService;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ((PepsiService.LocalBinder)service).getService();
            if(mService.isConnectionExist()) {
                EventBus.getDefault().post(new ConnectedEvent());
            } else {
                mService.startRegistrationAndDiscovery();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case MESSAGE_PRECOUNT:
                    mPreCount--;
                    if(mPreCount > 0) {
                        mPreCountView.setText(Integer.toString(mPreCount));
                        this.sendEmptyMessageDelayed(MESSAGE_PRECOUNT, 1000);
                    } else {
                        startGame();
                    }
                    break;
                case MESSAGE_TIME:
                    mCurrentTime--;
                    mTime.setText(Integer.toString(mCurrentTime));

                    if(mCurrentTime > 0) {
                        this.sendEmptyMessageDelayed(MESSAGE_TIME, 1000);
                    } else {
                        if(mShakeEnable) {
                            mSensorManager.unregisterListener(mShakeDetector);
                            mShakeEnable = false;
                        }
                        mResultIcon.setImageResource(R.drawable.default_result);
                        mResultIcon.setVisibility(View.VISIBLE);
                        mTime.setText("Times up");
                        ConnectionManager connectionManager = mService.getConnectionManager();
                        if(connectionManager != null)
                            connectionManager.write(new String(ConnectionManager.MESSAGE_RESULT + mShakeCount).getBytes());
                    }
                    break;
                case MESSAGE_RESULT:
                    if(msg.arg1 > mShakeCount) {
                        mResult.setText("You lose");
                        mResultIcon.setImageResource(R.drawable.lose);
                    } else if( msg.arg1 < mShakeCount) {
                        mResult.setText("You win");
                        mResultIcon.setImageResource(R.drawable.win);
                    } else {
                        mResult.setText("draw");
                        mResultIcon.setImageResource(R.drawable.draw);
                    }
                    break;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().bindService(new Intent(getActivity(), PepsiService.class), mServiceConnection, Context.BIND_AUTO_CREATE);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().unbindService(mServiceConnection);
        EventBus.getDefault().unregister(this);
    }

    public void onEvent(GameScoreEvent event) {
        Message msg = mHandler.obtainMessage(MESSAGE_RESULT, event.count, 0);
        mHandler.sendMessage(msg);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_play, container, false);
        mShakeStatus = (TextView) root.findViewById(R.id.shake_status);
        soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
        soundID = soundPool.load(getActivity(), R.raw.coin, 1);

        AudioManager audioManager = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
        float actualVolume = (float) audioManager
                .getStreamVolume(AudioManager.STREAM_MUSIC);
        float maxVolume = (float) audioManager
                .getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        volume = actualVolume / maxVolume;

        vibration  = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
        startDetectShake();

        mTime = (TextView) root.findViewById(R.id.time);

        mResult = (TextView) root.findViewById(R.id.result);
        mResultIcon  = (ImageView) root.findViewById(R.id.result_icon);
        mResultIcon.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restartGame();
            }
        });
        mPreCountView = (TextView) root.findViewById(R.id.precount);

        return root;
    }

    private void restartGame() {

        mPreCount = 3;
        mPreCountView.setText(Integer.toString(mPreCount));
        mPreCountView.setVisibility(View.VISIBLE);

        mShakeCount = 0;
        mShakeStatus.setText(Integer.toString(mShakeCount));
        mShakeStatus.setVisibility(View.INVISIBLE);

        mCurrentTime = 10;
        mTime.setText(Integer.toString(mCurrentTime));
        mTime.setVisibility(View.INVISIBLE);

        mResultIcon.setVisibility(View.INVISIBLE);

        mHandler.sendEmptyMessageDelayed(MESSAGE_PRECOUNT, 1000);
    }

    private void startGame() {
        mPreCountView.setVisibility(View.INVISIBLE);
        mShakeStatus.setVisibility(View.VISIBLE);
        mTime.setVisibility(View.VISIBLE);
        mHandler.sendEmptyMessageDelayed(MESSAGE_TIME, 1000);
    }

    @Override
    public void onResume() {
        super.onResume();
        mSensorManager.registerListener(mShakeDetector, mAccelerometer,	SensorManager.SENSOR_DELAY_UI);
        mShakeEnable = true;
        restartGame();
    }

    @Override
    public void onPause() {
        super.onPause();
        if(mShakeEnable)
            mSensorManager.unregisterListener(mShakeDetector);
    }

    private void startDetectShake() {

        // ShakeDetector initialization
        mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager
                .getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mShakeDetector = new ShakeDetector();
        mShakeDetector.setOnShakeListener(new ShakeDetector.OnShakeListener() {

            @Override
            public void onShake(int count) {
				/*
				 * The following method, "handleShakeEvent(count):" is a stub //
				 * method you would use to setup whatever you want done once the
				 * device has been shook.
				 */
                mShakeStatus.setText(Integer.toString(count));
                soundPool.play(soundID, volume, volume, 1, 1, 1f);
                vibration.vibrate(100);
                soundPool.play(soundID, volume, volume, 2, 1, 1f);
                mShakeCount++;
            }
        });
    }
}
