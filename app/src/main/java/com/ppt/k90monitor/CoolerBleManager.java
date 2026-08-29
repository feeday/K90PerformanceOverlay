package com.ppt.k90monitor;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Locale;
import java.util.UUID;

/**
 * BLE integration shell for REDMAGIC Cooler 8 Pro.
 * It safely discovers/connects/subscribes and exposes raw packets.
 * Protocol-specific field decoding is intentionally left disabled until
 * real device packets are captured and mapped.
 */
public final class CoolerBleManager {
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static CoolerBleManager instance;

    public static synchronized CoolerBleManager get(Context context) {
        if (instance == null) instance = new CoolerBleManager(context.getApplicationContext());
        return instance;
    }

    public static final class State {
        public boolean scanning;
        public boolean connected;
        public String deviceName = "--";
        public String address = "--";
        public float clampTempC = Float.NaN;
        public int fanRpm = -1;
        public float powerW = Float.NaN;
        public Boolean coolerOn = null;
        public String lastPacket = "--";
        public String message = "未连接";

        State copy() {
            State s = new State();
            s.scanning = scanning;
            s.connected = connected;
            s.deviceName = deviceName;
            s.address = address;
            s.clampTempC = clampTempC;
            s.fanRpm = fanRpm;
            s.powerW = powerW;
            s.coolerOn = coolerOn;
            s.lastPacket = lastPacket;
            s.message = message;
            return s;
        }
    }

    private final Context context;
    private final Object lock = new Object();
    private final State state = new State();
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;

    private CoolerBleManager(Context context) {
        this.context = context;
        BluetoothManager bm = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = bm == null ? null : bm.getAdapter();
    }

    public State getState() {
        synchronized (lock) { return state.copy(); }
    }

    public boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public void startAutoConnect() {
        if (!hasPermissions()) {
            setMessage("缺少蓝牙权限");
            return;
        }
        if (adapter == null || !adapter.isEnabled()) {
            setMessage("蓝牙未开启");
            return;
        }
        if (getState().connected || getState().scanning) return;
        try {
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) { setMessage("BLE扫描不可用"); return; }
            synchronized (lock) {
                state.scanning = true;
                state.message = "正在寻找红魔散热器…";
            }
            scanner.startScan(scanCallback);
        } catch (Throwable e) {
            setMessage("扫描失败: " + e.getClass().getSimpleName());
        }
    }

    public void disconnect() {
        try { if (scanner != null && hasPermissions()) scanner.stopScan(scanCallback); } catch (Throwable ignored) { }
        try { if (gatt != null) gatt.disconnect(); } catch (Throwable ignored) { }
        try { if (gatt != null) gatt.close(); } catch (Throwable ignored) { }
        gatt = null;
        synchronized (lock) {
            state.scanning = false;
            state.connected = false;
            state.message = "未连接";
            state.coolerOn = null;
            state.fanRpm = -1;
            state.clampTempC = Float.NaN;
            state.powerW = Float.NaN;
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            if (result == null || result.getDevice() == null) return;
            String name = safeName(result);
            String low = name.toLowerCase(Locale.US);
            if (!(low.contains("redmagic") || low.contains("red magic") || low.contains("cooler") || low.contains("goper") || low.contains("散热"))) {
                return;
            }
            connect(result.getDevice(), name);
        }

        @Override public void onScanFailed(int errorCode) {
            synchronized (lock) {
                state.scanning = false;
                state.message = "扫描失败 code=" + errorCode;
            }
        }
    };

    private void connect(BluetoothDevice device, String name) {
        try { if (scanner != null) scanner.stopScan(scanCallback); } catch (Throwable ignored) { }
        synchronized (lock) {
            state.scanning = false;
            state.deviceName = name;
            try { state.address = device.getAddress(); } catch (Throwable ignored) { }
            state.message = "连接中…";
        }
        try {
            gatt = Build.VERSION.SDK_INT >= 23
                    ? device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                    : device.connectGatt(context, false, callback);
        } catch (Throwable e) {
            setMessage("连接失败: " + e.getClass().getSimpleName());
        }
    }

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            synchronized (lock) {
                state.connected = newState == BluetoothProfile.STATE_CONNECTED;
                state.message = state.connected ? "已连接，发现服务…" : "已断开";
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                try { g.discoverServices(); } catch (Throwable ignored) { }
            }
        }

        @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                setMessage("服务发现失败: " + status);
                return;
            }
            int subscribed = 0;
            for (BluetoothGattService service : g.getServices()) {
                for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                    int p = c.getProperties();
                    if ((p & (BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_INDICATE)) == 0) continue;
                    try {
                        if (g.setCharacteristicNotification(c, true)) {
                            BluetoothGattDescriptor d = c.getDescriptor(CCCD);
                            if (d != null) {
                                byte[] value = (p & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                                        ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                        : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                                if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, value);
                                else {
                                    d.setValue(value);
                                    g.writeDescriptor(d);
                                }
                            }
                            subscribed++;
                        }
                    } catch (Throwable ignored) { }
                }
            }
            setMessage("已连接 · Notify通道 " + subscribed);
        }

        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) {
            onPacket(c, value);
        }

        @SuppressWarnings("deprecation")
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            if (Build.VERSION.SDK_INT < 33) onPacket(c, c.getValue());
        }

        @Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) onPacket(c, value);
        }
    };

    private void onPacket(BluetoothGattCharacteristic c, byte[] value) {
        String hex = toHex(value);
        synchronized (lock) {
            state.lastPacket = c.getUuid() + " " + hex;
        }
        // Protocol decoder intentionally disabled until real REDMAGIC 8 Pro packets are mapped.
    }

    private String safeName(ScanResult r) {
        try {
            String n = r.getDevice().getName();
            if (n != null && !n.trim().isEmpty()) return n.trim();
        } catch (Throwable ignored) { }
        try {
            ScanRecord sr = r.getScanRecord();
            if (sr != null && sr.getDeviceName() != null) return sr.getDeviceName();
        } catch (Throwable ignored) { }
        return "Unknown BLE";
    }

    private void setMessage(String s) {
        synchronized (lock) { state.message = s; }
    }

    private static String toHex(byte[] data) {
        if (data == null || data.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(String.format(Locale.US, "%02X", b & 0xFF));
        }
        return sb.toString();
    }
}
