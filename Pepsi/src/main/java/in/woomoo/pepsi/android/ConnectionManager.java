package in.woomoo.pepsi.android;

import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

import de.greenrobot.event.EventBus;
import in.woomoo.pepsi.android.event.GameScoreEvent;

/**
 * Created by Jason on 11/30/13.
 */
public class ConnectionManager implements Runnable {
    private Socket socket = null;
    private Handler handler;

    public ConnectionManager(Socket socket, Handler handler) {
        this.socket = socket;
        this.handler = handler;
    }

    private InputStream iStream;
    private OutputStream oStream;
    private static final String TAG = ConnectionManager.class.getSimpleName();

    @Override
    public void run() {
        try {

            iStream = socket.getInputStream();
            oStream = socket.getOutputStream();
            byte[] buffer = new byte[1024];
            int bytes;
            handler.obtainMessage(PepsiService.MY_HANDLE, this)
                    .sendToTarget();

            while (true) {
                try {
                    // Read from the InputStream
                    bytes = iStream.read(buffer);
                    if (bytes == -1) {
                        break;
                    }

                    // Send the obtained bytes to the UI Activity
                    String rec = new String(buffer, "UTF-8").substring(0,bytes);

                    Log.d(TAG, "Rec:" + rec);
                    if(rec.equals(MESSAGE_START))
                        handler.obtainMessage(PepsiService.MESSAGE_START,0,0).sendToTarget();
                    else if(rec.substring(0, MESSAGE_RESULT.length()).equals(MESSAGE_RESULT)) {
                        int count = Integer.parseInt(rec.substring(MESSAGE_START.length()+1));
                        EventBus.getDefault().post(new GameScoreEvent(count));
                    }
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void write(byte[] buffer) {
        try {
            oStream.write(buffer);

            // also start local
            String rec = new String(buffer, "UTF-8");
            Log.d(TAG, "write:" + rec);
            if(rec.equals(MESSAGE_START)) {
                handler.obtainMessage(PepsiService.MESSAGE_START,0,0).sendToTarget();
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception during write", e);
        }
    }

    public static final String MESSAGE_START = "start";
    public static final String MESSAGE_RESULT = "result";
}
