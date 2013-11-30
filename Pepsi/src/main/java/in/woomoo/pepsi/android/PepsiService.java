package in.woomoo.pepsi.android;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import de.greenrobot.event.EventBus;
import in.woomoo.pepsi.android.event.ConnectedEvent;
import in.woomoo.pepsi.android.event.ServerFindEvent;
import in.woomoo.pepsi.android.event.StartEvent;

/**
 * Created by Jason on 11/29/13.
 */
public class PepsiService extends Service implements WifiP2pManager.ConnectionInfoListener, Handler.Callback, MessageTarget {
    private static final String TAG = PepsiService.class.getSimpleName();

    public static final String TXTRECORD_PROP_AVAILABLE = "available";
    public static final String SERVICE_INSTANCE = "PEPSI";
    public static final String SERVICE_REG_TYPE = "_presence._tcp";

    private WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;
    private WifiP2pDnsSdServiceRequest mServiceRequest;


    public static final int MY_HANDLE = 0x400 + 2;
    public static final int MESSAGE_START = 0x400 + 3;

    private Handler handler = new Handler(this);

    private ConnectionManager mConnectionManager;

    public Handler getHandler() {
        return handler;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "PepsiService onCreate");
        super.onCreate();
        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);

        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    public void onEvent(ConnectedEvent event) {
        mManager.requestConnectionInfo(mChannel, this);
    }

    public void connect(ServerEntity entity) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = entity.device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;
        Log.d(TAG, entity.device.deviceAddress + " connection status : " + entity.device.status);
        final boolean connected = (entity.device.status == WifiP2pDevice.CONNECTED || entity.device.status == WifiP2pDevice.INVITED?true:false);

        if(mServiceRequest != null) {
            mManager.removeServiceRequest(mChannel, mServiceRequest,
                    new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {

                        }

                        @Override
                        public void onFailure(int reason) {

                        }
                    });

            mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {

                    Log.d(TAG, "connect server success");
                    if(connected) {

                        Log.d(TAG, "Already connected, skip waiting pair");
                        EventBus.getDefault().post(new ConnectedEvent());
                    }
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(TAG, "connect server fail");
                }
            });
        }
    }

    public boolean isConnectionExist() {
        return mConnectionHandler==null?false:true;
    }

    public void disconnect() {
        if(mConnectionHandler != null) {
            mConnectionHandler.interrupt();
            mConnectionHandler = null;
        }
        mManager.cancelConnect(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "disconnect success");
            }

            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "disconnect fail");
            }
        });
    }

    public ConnectionManager getConnectionManager() {
        return mConnectionManager;
    }

    public void startRegistrationAndDiscovery() {
        Map<String, String> record = new HashMap<String, String>();
        record.put(TXTRECORD_PROP_AVAILABLE, "visible");

        WifiP2pDnsSdServiceInfo service = WifiP2pDnsSdServiceInfo.newInstance(
                SERVICE_INSTANCE, SERVICE_REG_TYPE, record);
        mManager.addLocalService(mChannel, service, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.d(TAG, "Added Local Service");
            }

            @Override
            public void onFailure(int error) {
                Log.d(TAG, "Failed to add a service");
            }
        });

        discoverService();
    }

    private void discoverService() {
        mManager.setDnsSdResponseListeners(mChannel, new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String registrationType, WifiP2pDevice srcDevice) {
                if(instanceName.equalsIgnoreCase(SERVICE_INSTANCE)) {
                    ServerFindEvent event = new ServerFindEvent();
                    ServerEntity server = new ServerEntity();
                    server.device = srcDevice;
                    server.instanceName = instanceName;
                    server.serviceRegistrationType = registrationType;
                    event.server = server;
                    EventBus.getDefault().post(event);
                }
            } }, new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
            public void onDnsSdTxtRecordAvailable(String fullDomainName, Map<String, String> txtRecordMap, WifiP2pDevice srcDevice) {
                Log.d(TAG, srcDevice.deviceName + " is " + txtRecordMap.get(TXTRECORD_PROP_AVAILABLE));
            }
        });

        mServiceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        mManager.addServiceRequest(mChannel, mServiceRequest, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "service request success");

                mManager.discoverServices(mChannel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "service discover success");
                    }

                    @Override
                    public void onFailure(int reason) {
                        Log.d(TAG, "service discover fail - " + reason);
                    }
                });
            }

            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "service request fail");
            }
        });

    }

    Thread mConnectionHandler = null;

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        if(info.isGroupOwner) {
            Log.d(TAG, "Connected as group ownner");
            try {
                mConnectionHandler = new ServerSocketHandler(
                        ((MessageTarget) this).getHandler());
                mConnectionHandler.start();
            } catch (IOException e) {
                Log.d(TAG,
                        "Failed to create a server thread - " + e.getMessage());
                return;
            }
        } else {
            Log.d(TAG, "Connected as as peer");
            mConnectionHandler = new ClientSocketHandler(
                    ((MessageTarget) this).getHandler(),
                    info.groupOwnerAddress);
            mConnectionHandler.start();
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MESSAGE_START:
                EventBus.getDefault().post(new StartEvent());
                break;
            case MY_HANDLE:
                mConnectionManager = (ConnectionManager) msg.obj;

                break;
        }
        return false;
    }

    public class LocalBinder extends Binder {
        PepsiService getService() {
            return PepsiService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final IBinder mBinder = new LocalBinder();

}
