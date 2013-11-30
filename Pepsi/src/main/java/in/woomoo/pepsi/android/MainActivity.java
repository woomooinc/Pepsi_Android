package in.woomoo.pepsi.android;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;

import de.greenrobot.event.EventBus;
import in.woomoo.pepsi.android.event.ConnectedEvent;

public class MainActivity extends Activity {

    private BluetoothAdapter mBluetoothAdapter;

    private boolean mConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new ServerListFragment())
                    .commit();
//            getFragmentManager().beginTransaction()
//                    .add(R.id.container, new PlayFragment())
//                    .commit();
        }

        EventBus.getDefault().register(this);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        EventBus.getDefault().unregister(this);
    }

    public void onEvent(ConnectedEvent event) {

        if(mConnected)
            return;
        mConnected = true;

        startActivity(new Intent(this, ConnectedActivity.class));
    }
}
