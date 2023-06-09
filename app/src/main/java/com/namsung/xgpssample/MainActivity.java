package com.namsung.xgpssample;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Debug;
import android.os.Handler;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import com.namsung.xgpsmanager.utils.DLog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import com.namsung.xgpsmanager.XGPSListener;
import com.namsung.xgpsmanager.XGPSManager;
import com.namsung.xgpsmanager.data.LogBulkData;
import com.namsung.xgpsmanager.data.LogData;
import com.namsung.xgpsmanager.data.SettingInfo;
import com.namsung.xgpsmanager.data.XGPSError;
import com.namsung.xgpsmanager.utils.Constants;
import com.namsung.xgpssample.R;

public class MainActivity extends AppCompatActivity implements XGPSListener, BottomNavigationView.OnNavigationItemSelectedListener {
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PAIRING_BT = 2;
    public static final int REQUEST_CODE_PERMISSIONS_LOCATION = 3;

    private int mCurrentMenuId = -1;
    private XGPSManager xgpsManager;
    private boolean btBackState = false;
    private BaseFragment mCurrentFragment;
    private boolean isShowingOtherIntent = false;

    private Handler xgpsHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SimpleDateFormat date =
                new SimpleDateFormat("dd_MM_yyyy_hh_mm_ss");
        String logDate = date.format(new Date());
        Debug.startMethodTracing("xgpsSample-" + logDate);

        xgpsManager = new XGPSManager(this, this);

        Log.i("XGPSManager", "SDK version : " + xgpsManager.getSDKVersion());

        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.tab_menu);
        navigation.setOnNavigationItemSelectedListener(this);

        // setting current fragment
        setFragment(R.id.menu_gps);

        HandlerThread handlerThread = new HandlerThread("MyHandlerThread");
        handlerThread.start();
        xgpsHandler = new Handler(handlerThread.getLooper());
    }

    private void setFragment(int menuId) {
        if (mCurrentMenuId == menuId)
            return;

        mCurrentMenuId = menuId;
        FragmentManager fragmentManager = getSupportFragmentManager();

        if (menuId == R.id.menu_gps) {
            xgpsManager.sendCommandToDevice(Constants.cmd160_streamResume, null,0);
            mCurrentFragment = StatusFragment.newInstance(xgpsManager);
            fragmentManager.beginTransaction()
                    .replace(R.id.content, mCurrentFragment)
                    .commitAllowingStateLoss();
        } else if (menuId == R.id.menu_trips) {
            xgpsManager.sendCommandToDevice(Constants.cmd160_streamStop, null, 0);
            xgpsManager.sendCommandToDevice(Constants.cmd160_logList, null, 0);
            mCurrentFragment = TripsFragment.newInstance(xgpsManager);
            fragmentManager.beginTransaction()
                    .replace(R.id.content, mCurrentFragment)
                    .commitAllowingStateLoss();
        }
    }

    @Override
    public void onBackPressed() {
        if (mCurrentFragment.getChildFragmentManager().getBackStackEntryCount() > 0 && mCurrentFragment.getClass() == TripsFragment.class) {
            mCurrentFragment.onBackPressed();
        }
        else {
            if(!btBackState) { // 한 번만 누른 경우
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        btBackState = false;
                    }
                }, 2000);
                btBackState = true;
                Toast.makeText(getApplicationContext(), R.string.exit_message, Toast.LENGTH_SHORT).show();
            } else {// 2초 내에 두 번 누른 경우
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        Debug.stopMethodTracing();
        xgpsManager.setMockEnable(false);
        xgpsManager.onDestroy();
        super.onDestroy();
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        setFragment(item.getItemId());
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
            case REQUEST_PAIRING_BT:
                // When the request to enable Bluetooth returns
                isShowingOtherIntent = false;
                if (resultCode == Activity.RESULT_OK) {
                    xgpsManager.onResume();
                } else {
                    // User did not enable Bluetooth or an error occured
                    Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
        }
    }

    @Override
    public void connecting(final BluetoothDevice device) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "try to connect " + device.getName(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // start XGPSListner
    @Override
    public void connected(final boolean isConnect, final int error) {
        if (!isConnect) {
            if (error == XGPSError.ERR_BLUETOOTH_ENABLE  && !isShowingOtherIntent) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                isShowingOtherIntent = true;
                return;
            }
            else if (error == XGPSError.ERR_NO_BONDED_DEVICE && !isShowingOtherIntent) {
                Intent intentOpenBluetoothSettings = new Intent();
                intentOpenBluetoothSettings.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                startActivity(intentOpenBluetoothSettings);
                isShowingOtherIntent = true;
                return;
            }
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCurrentFragment.connected(isConnect, error);
            }
        });
    }

    @Override
    public void updateGPSVoltage() {

    }

    @Override
    public void updateLocationInfo() {
        xgpsHandler.post(new Runnable() {
            public void run() {
                if (mCurrentFragment != null)
                    mCurrentFragment.updateLocationInfo();
            }
        });
    }

    @Override
    public void updateSatellitesInfo(final int systemId) {
        if (xgpsHandler == null) return;
        xgpsHandler.post(new Runnable() {
            public void run() {
                if (mCurrentFragment != null)
                    mCurrentFragment.updateSatellitesInfo(systemId);
            }
        });
    }

//    @Override
//    public void updateSettings(final boolean positionEnable, final boolean overWrite) {
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                mCurrentFragment.updateSettings(positionEnable, overWrite);
//            }
//        });
//    }

    @Override
    public void getLogListComplete(final ArrayList<LogData> logList) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCurrentFragment.getLogListComplete(logList);
            }
        });
    }

    @Override
    public void getLogDetailProgress(final int bulkCount) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCurrentFragment.getLogDetailProgress(bulkCount);
            }
        });
    }

    @Override
    public void getLogDetailComplete(final ArrayList<LogBulkData> logBulkList) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCurrentFragment.getLogDetailComplete(logBulkList);
            }
        });
    }

    @Override
    public void throwException(Exception e) {
        if (e.getClass() == SecurityException.class) {
            DLog.e(e.getMessage());
            Intent intentOpenBluetoothSettings = new Intent();
            intentOpenBluetoothSettings.setAction(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
            startActivity(intentOpenBluetoothSettings);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch(requestCode) {
            case REQUEST_CODE_PERMISSIONS_LOCATION:
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    finish();    // location 권한을 거부한 경우
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void updateFirmwareInfo() {}
    @Override
    public void updateConfidenceInfo() {}
    @Override
    public void updateSatellitesInfoADSB(){}
    @Override
    public void updateCalibrationInfo(){}
    @Override
    public void updateADSBStatus(boolean isSelected){}
    @Override
    public void updateTrafficInfo(){}
    @Override
    public void updateSettings(SettingInfo info){}

    @Override
    public void updateDrivingMode(int mode) {

    }

    // for skypro gps
    @Override
    public void getSelectedMountPoint(String mountpoint){}

    @Override
    public void receiveNtripData(long length) {

    }

    @Override
    public void onNtripError(String error) {}

}
