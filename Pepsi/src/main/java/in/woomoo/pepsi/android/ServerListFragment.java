package in.woomoo.pepsi.android;

import android.app.Fragment;
import android.app.ListFragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import de.greenrobot.event.EventBus;
import in.woomoo.pepsi.android.event.ConnectedEvent;
import in.woomoo.pepsi.android.event.ServerFindEvent;
import in.woomoo.pepsi.android.event.ServerSelectEvent;

/**
 * Created by Jason on 11/29/13.
 */
public class ServerListFragment extends ListFragment {
    private String TAG = ServerListFragment.class.getSimpleName();

    private WiFiDevicesAdapter mAdapter;
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_server_list, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mAdapter = new WiFiDevicesAdapter(getActivity(),
                android.R.layout.simple_expandable_list_item_2, android.R.id.text1,
                new ArrayList<ServerEntity>());

        setListAdapter(mAdapter);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        ServerEntity server = (ServerEntity) l.getItemAtPosition(position);
        mService.connect(server);

    }

    public void onEvent(ServerFindEvent event) {
        mAdapter.add(event.server);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EventBus.getDefault().register(this);

        getActivity().bindService(new Intent(getActivity(), PepsiService.class), mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mService.disconnect();
        EventBus.getDefault().unregister(this);
        getActivity().unbindService(mServiceConnection);
    }

    public class WiFiDevicesAdapter extends ArrayAdapter<ServerEntity> {

        private List<ServerEntity> items;

        public WiFiDevicesAdapter(Context context, int resource,
                                  int textViewResourceId, List<ServerEntity> items) {
            super(context, resource, textViewResourceId, items);
            this.items = items;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) getActivity()
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(android.R.layout.simple_list_item_2, null);
            }
            ServerEntity server = items.get(position);
            if (server != null) {
                TextView nameText = (TextView) v
                        .findViewById(android.R.id.text1);

                if (nameText != null) {
                    nameText.setText(server.device.deviceName + " - " + server.instanceName);
                }
                TextView statusText = (TextView) v
                        .findViewById(android.R.id.text2);
                statusText.setText(getDeviceStatus(server.device.status));
            }
            return v;
        }

    }

    public static String getDeviceStatus(int statusCode) {
        switch (statusCode) {
            case WifiP2pDevice.CONNECTED:
                return "Connected";
            case WifiP2pDevice.INVITED:
                return "Invited";
            case WifiP2pDevice.FAILED:
                return "Failed";
            case WifiP2pDevice.AVAILABLE:
                return "Available";
            case WifiP2pDevice.UNAVAILABLE:
                return "Unavailable";
            default:
                return "Unknown";

        }
    }
}
