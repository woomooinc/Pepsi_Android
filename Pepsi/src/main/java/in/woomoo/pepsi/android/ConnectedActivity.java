package in.woomoo.pepsi.android;

import android.app.Activity;
import android.os.Bundle;

import de.greenrobot.event.EventBus;
import in.woomoo.pepsi.android.event.StartEvent;

/**
 * Created by Jason on 11/30/13.
 */
public class ConnectedActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_connected);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new StandbyFragment())
                    .commit();
        }
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }



    public void onEvent(StartEvent event) {
        getFragmentManager().beginTransaction()
                .add(R.id.container, new PlayFragment())
                .commit();
    }
}
