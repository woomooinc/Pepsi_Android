package in.woomoo.pepsi.android;

import android.app.Application;
import android.content.Intent;

/**
 * Created by Jason on 11/30/13.
 */
public class PepsiApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Intent intent = new Intent("in.woomoo.pepsi.android");
        intent.setClass(this, PepsiService.class);
        startService(intent);
    }
}
