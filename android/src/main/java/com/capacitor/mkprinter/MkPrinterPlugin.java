package com.capacitor.mkprinter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

import com.zebra.sdk.comm.BluetoothConnectionInsecure;
import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.printer.PrinterStatus;
import com.zebra.sdk.printer.discovery.DiscoveredPrinter;
import com.zebra.sdk.printer.discovery.DiscoveryHandler;

import com.capacitor.mkprinter.goojprt.util.PrintUtils;
import com.android.print.sdk.PrinterInstance;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Set;

@CapacitorPlugin(
        name = "MkPrinter",
        permissions = {
                @Permission(
                        alias = "bluetooth",
                        strings = {Manifest.permission.BLUETOOTH}
                ),
                @Permission(
                        alias = "bluetooth_admin",
                        strings = {Manifest.permission.BLUETOOTH_ADMIN}
                )
        }
)
public class MkPrinterPlugin extends Plugin implements DiscoveryHandler {
    private PluginCall mCall;
    private boolean mPrinterFound;
    private Connection mPrinterConn;
    private PrinterStatus mPrinterStatus;
    private BluetoothAdapter mBluetoothAdapter;

    private final String LOG_TAG = "MkPrinterPlugin";

    public MkPrinterPlugin() {}

    @PluginMethod
    public void printText(PluginCall call) {
        try {
            String printText = call.getString("rows");

            PrinterInstance mPrinter = PrintUtils.getCurrentPrinter(getContext());
            PrintUtils.printText(mPrinter, printText);
            call.resolve();

        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
            e.printStackTrace();
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void printImage(PluginCall call) {
        String base64Data = call.getString("base64Data");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PrinterInstance mPrinter = PrintUtils.getCurrentPrinter(getContext());
                    if (mPrinter == null) {
                        Log.e(LOG_TAG, "No printer connected");
                        throw new RuntimeException("No printer connected");
                    }
                    PrintUtils.printImage(mPrinter, base64Data);
                    call.resolve();
                } catch (Throwable e) {
                    call.reject(e.getMessage());
                }
            }
        }).start();
    }

    @PluginMethod
    public void listenPrinters(PluginCall call) throws JSONException {
        try {
            JSONArray deviceList = discoverPrinters();
            Log.d(LOG_TAG, deviceList.toString());

            JSObject res = new JSObject();
            res.put("devices", deviceList);
            call.resolve(res);

        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
            e.printStackTrace();
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void connectPrinter(PluginCall call) {
        try {
            String MACAddress = call.getString("macAddress");
            PrintUtils.connectPrinter(getContext(), MACAddress);
            call.resolve();
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
            e.printStackTrace();
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void disconnectPrinter(PluginCall call) {
        try {
            PrintUtils.disconnectPrinter(getContext());
            call.resolve();
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
            e.printStackTrace();
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void getCurrentPrinter(PluginCall call) {
        try {
            HashMap <String, String> deviceInfo = PrintUtils.getCurrentDeviceInfo(getContext());
            JSObject res = new JSObject();
            res.put("name", deviceInfo.get("name"));
            res.put("macAddress", deviceInfo.get("address"));
            call.resolve(res);
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
            e.printStackTrace();
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void openBluetoothSettings(PluginCall call) {
        Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        getActivity().startActivity(intent);
        call.resolve();
    }

    @SuppressLint("MissingPermission")
    private boolean openBluetoothConnection(String MACAddress) throws ConnectionException {

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter.isEnabled()) {
            Log.d(LOG_TAG, "Creating a bluetooth-connection for mac-address " + MACAddress);

            mPrinterConn = new BluetoothConnectionInsecure(MACAddress);

            Log.d(LOG_TAG, "Opening connection...");
            mPrinterConn.open();
            Log.d(LOG_TAG, "connection successfully opened...");

            return true;
        } else {
            Log.d(LOG_TAG, "Bluetooth is disabled...");
            mCall.reject("Bluetooth is not on.");
        }

        return false;
    }

    @PluginMethod
    public void enableBluetooth(PluginCall call) {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(call, intent, "enableBluetoothCallback");
    }

    @SuppressLint("MissingPermission")
    private JSONArray discoverPrinters() throws JSONException {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        JSONArray deviceList = new JSONArray();

        Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();

        for (BluetoothDevice device : bondedDevices) {
            deviceList.put(deviceToJSON(device));
        }

        return deviceList;
    }

    @SuppressLint("MissingPermission")
    private JSONObject deviceToJSON(BluetoothDevice device) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("name", device.getName());
        json.put("macAddress", device.getAddress());
        json.put("id", device.getAddress());
        if (device.getBluetoothClass() != null) {
            json.put("class", device.getBluetoothClass().getDeviceClass());
        }
        return json;
    }

    @Override
    public void foundPrinter(DiscoveredPrinter discoveredPrinter) {
        Log.d(LOG_TAG, "Printer found: " + discoveredPrinter.address);
        if (!mPrinterFound) {
            mPrinterFound = true;
            JSObject res = new JSObject();
            res.put("value", discoveredPrinter.address);
            mCall.resolve(res);
        }
    }

    @Override
    public void discoveryFinished() {
        Log.d(LOG_TAG, "Finished searching for printers...");
        if (!mPrinterFound) {
            mCall.reject("No printer found. If this problem persists, restart the printer.");
        }
    }

    @Override
    public void discoveryError(String s) {
        Log.e(LOG_TAG, "An error occurred while searching for printers. Message: " + s);
        mCall.reject(s);
    }
}
