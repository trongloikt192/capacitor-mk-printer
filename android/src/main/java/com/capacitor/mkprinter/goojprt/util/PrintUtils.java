package com.capacitor.mkprinter.goojprt.util;


import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.android.print.sdk.Barcode;
import com.android.print.sdk.bluetooth.BluetoothPort;
import com.android.print.sdk.PrinterConstants;
import com.android.print.sdk.PrinterConstants.Command;
import com.android.print.sdk.PrinterInstance;
import com.android.print.sdk.util.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Properties;

public class PrintUtils {

    public static BluetoothDevice mBluetoothDevice;

    /**
     * Connects to a printer using the provided MAC address
     *
     * @param context The application context
     * @param macAddress The MAC address of the printer to connect to
     * @return PrinterInstance The connected printer instance
     */
    public static PrinterInstance connectPrinter(Context context, String macAddress) throws RuntimeException, InterruptedException {
        PrinterInstance printerInstance;
        clearBluetoothDeviceInfo(context);
        File file = new File(context.getFilesDir(), "btinfo.properties");
        if (file.exists()) {
            file.delete();
        }

        BluetoothPort bluetoothPort = new BluetoothPort();

        // Create handler on main thread for printer callbacks
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            throw new RuntimeException("Failed to connect to printer: Bluetooth is disabled or unavailable");
        }

        // Create handler on main thread for printer callbacks
        Handler handler = new Handler(Looper.getMainLooper());

        // Use BluetoothPort to establish the connection and get PrinterInstance
        printerInstance = bluetoothPort.btConnnect(context, macAddress, bluetoothAdapter, handler);

        // Add explicit check for null printerInstance, which indicates connection failure
        if (printerInstance == null) {
            throw new RuntimeException("Failed to connect to printer: Connection attempt returned null");
        }

        // Loop to check if the printer is connected, with a timeout 5s
        int attempts = 0;
        while (!printerInstance.isConnected() && attempts < 10) {
            Thread.sleep(500);
            attempts++;
        }

        // If the printer is still not connected after 10 attempts, throw an exception
        if (!printerInstance.isConnected()) {
            throw new RuntimeException("Failed to connect to printer: Printer instance created but not connected");
        }

        // Save printer name and MAC address
        mBluetoothDevice = bluetoothAdapter.getRemoteDevice(macAddress);

        // Save connection info for potential future auto-reconnect
        Utils.saveBtConnInfo(context, macAddress);

        return printerInstance;
    }

    /**
     * Disconnects and closes the printer connection
     * @param context The application context
     */
    public static void disconnectPrinter(Context context) throws InterruptedException {
        PrinterInstance printerInstance = getCurrentPrinter(context);
        if (printerInstance != null && printerInstance.isConnected()) {
            printerInstance.closeConnection();
            clearBluetoothDeviceInfo(context);
        }
    }

    /**
     * Attempts to auto-connect to the last connected printer
     * @param context The application context
     * @throws RuntimeException If the connection fails
     * @return PrinterInstance The connected printer instance
     */
    public static PrinterInstance getCurrentPrinter(Context context) throws InterruptedException {
        PrinterInstance printerInstance;
        clearBluetoothDeviceInfo(context);

        BluetoothPort bluetoothPort = new BluetoothPort();
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Use btAutoConn before create new connection
        printerInstance = bluetoothPort.btAutoConn(context, bluetoothAdapter, new Handler(Looper.getMainLooper()));

        // Check if printerInstance is null
        if (printerInstance == null) {
            throw new RuntimeException("Failed to connect to printer: Auto-connection attempt returned null");
        }

        // Loop to check if the printer is connected, with a timeout 5s
        int attempts = 0;
        while (!printerInstance.isConnected() && attempts < 10) {
            Thread.sleep(500);
            attempts++;
        }

        // If the printer is still not connected after 10 attempts, throw an exception
        if (!printerInstance.isConnected()) {
            throw new RuntimeException("Failed to connect to printer: Printer instance created but not connected");
        }

        // Save printer name and MAC address
        Properties pro = Utils.getBtConnInfo(context);
        mBluetoothDevice = bluetoothAdapter.getRemoteDevice(pro.getProperty("mac"));

        return printerInstance;
    }

   /**
    * Returns information about the currently connected Bluetooth device
    * @return HashMap containing device name and address, or null values if no device connected
    */
   public static HashMap<String, String> getCurrentDeviceInfo(Context context) {
       HashMap<String, String> deviceInfo = new HashMap<>();
       boolean permissionGranted = ActivityCompat.checkSelfPermission(
               context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED;

       if (mBluetoothDevice == null || !permissionGranted) {
           deviceInfo.put("name", null);
           deviceInfo.put("address", null);
       } else {
           deviceInfo.put("name", mBluetoothDevice.getName());
           deviceInfo.put("address", mBluetoothDevice.getAddress());
       }

       return deviceInfo;
   }


    public static void printText(PrinterInstance mPrinter, String text) {
        mPrinter.init();
        mPrinter.printText(text);
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);
    }

    /**
     * Prints an image to the printer
     * @param mPrinter The printer instance to use for printing
     * @param base64Data The base64 encoded image string.
     * @throws RuntimeException If the image fails to load or print
     */
    public static void printImage(PrinterInstance mPrinter, String base64Data) throws IOException, InterruptedException {
        mPrinter.init();
        //Bitmap bitmapOrigin = BitmapFactory.decodeStream(resources.getAssets().open("receipt_2items.png"));
        Bitmap bitmapOrigin = convertBase64ToBitmap(base64Data);
        Bitmap bitmap1 = prepareImageForPrinting(bitmapOrigin);
        mPrinter.printImage(bitmap1);
        mPrinter.printText("\n\n\n\n");
    }

    /**
     * Prints an update to the printer
     * @param resources The application resources
     * @param mPrinter The printer instance to use for printing
     * @param filePath The path to the update file
     */
    public static void printUpdate(Resources resources, PrinterInstance mPrinter, String filePath) {
        try {

            FileInputStream fis = new FileInputStream(new File(filePath));
            //InputStream is = resources.getAssets().open("PT8761-HT-BAT-9170.bin");
            int length = fis.available();
            byte[] fileByte = new byte[length];
            fis.read(fileByte);
            mPrinter.init();
            mPrinter.updatePrint(fileByte);
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Converts a base64 encoded string to a Bitmap
     * @param base64Data The base64 encoded image string
     * @return The decoded Bitmap
     */
    private static Bitmap convertBase64ToBitmap(String base64Data) throws IOException {
       byte[] decodedBytes = Base64.decode(base64Data, Base64.DEFAULT);
       return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
    }

    /**
     * Prepares an image for printing by resizing and converting to black and white
     * @param originalBitmap The original Bitmap to be printed
     * @return Bitmap The prepared Bitmap for printing
     */
    private static Bitmap prepareImageForPrinting(Bitmap originalBitmap) {
        // Lấy kích thước máy in (ví dụ: 384 pixel cho máy in nhiệt)
        int printerWidth = 384;

        // Tính toán tỷ lệ để giữ nguyên tỷ lệ khung hình
        float ratio = (float) printerWidth / originalBitmap.getWidth();
        int newHeight = (int) (originalBitmap.getHeight() * ratio);

        // Thay đổi kích thước hình ảnh phù hợp với máy in
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(
                originalBitmap, printerWidth, newHeight, true);

        // Chuyển đổi sang định dạng đen trắng nếu cần (cho máy in nhiệt)
        return convertToBlackAndWhite(resizedBitmap);
    }

    /**
     * Converts a color Bitmap to a black and white Bitmap
     * @param colorBitmap The color Bitmap to be converted
     * @return Bitmap The black and white Bitmap
     */
    private static Bitmap convertToBlackAndWhite(Bitmap colorBitmap) {
        int width = colorBitmap.getWidth();
        int height = colorBitmap.getHeight();
        Bitmap bwBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = colorBitmap.getPixel(x, y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                int gray = (r + g + b) / 3;
                int bwPixel = gray < 128 ? Color.BLACK : Color.WHITE;
                bwBitmap.setPixel(x, y, bwPixel);
            }
        }
        return bwBitmap;
    }

    /**
     * Clears the Bluetooth device information
     */
    private static void clearBluetoothDeviceInfo(Context context) {
        mBluetoothDevice = null;
    }




    // ==========================  TESTING  ==========================
    /**
     * Prints a text string to the printer
     * @param mPrinter The printer instance to use for printing
     */
    public static void printTextTest(PrinterInstance mPrinter) {
        mPrinter.init();

        mPrinter.printText(("R.string.str_text"));
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);


        mPrinter.setFont(0, 0, 0, 0);
        mPrinter.setPrinter(Command.ALIGN, 0);
        mPrinter.printText(("R.string.str_text_left"));
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);// 换2行


        mPrinter.setPrinter(Command.ALIGN, 1);
        mPrinter.printText(("R.string.str_text_center"));
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);// 换2行

        mPrinter.setPrinter(Command.ALIGN, 2);
        mPrinter.printText(("R.string.str_text_right"));
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 3); // 换3行

        mPrinter.setPrinter(Command.ALIGN, 0);
        mPrinter.setFont(0, 0, 1, 0);
        mPrinter.printText(("R.string.str_text_strong"));
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2); // 换2行

        mPrinter.setFont(0, 0, 0, 1);
        mPrinter.sendByteData(new byte[]{(byte) 0x1C, (byte) 0x21, (byte) 0x80});
        mPrinter.printText(("R.string.str_text_underline"));
        mPrinter.sendByteData(new byte[]{(byte) 0x1C, (byte) 0x21, (byte) 0x00});
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2); // 换2行

        mPrinter.setFont(0, 0, 0, 0);
        mPrinter.printText(("R.string.str_text_height"));
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);
        for (int i = 0; i < 4; i++) {
            mPrinter.setFont(i, i, 0, 0);
            mPrinter.printText((i + 1) + ("R.string.times"));

        }
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 1);
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 3);

        for (int i = 0; i < 4; i++) {
            mPrinter.setFont(i, i, 0, 0);
            mPrinter.printText(("R.string.bigger") + (i + 1) + ("R.string.bigger1"));
            mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 3);
        }

        mPrinter.setFont(0, 0, 0, 0);
        mPrinter.setPrintModel(true, false, false, false);
        mPrinter.printText("文字加粗演示\n");

        mPrinter.setPrintModel(true, true, false, false);
        mPrinter.printText("文字加粗倍高演示\n");

        mPrinter.setPrintModel(true, false, true, false);
        mPrinter.printText("文字加粗倍宽演示\n");

        mPrinter.setPrintModel(true, true, true, false);
        mPrinter.printText("加工済みスキャンパーツ\n");

        mPrinter.setPrintModel(false, false, false, true);
        mPrinter.printText("一括管理システム\n");


        mPrinter.setFont(0, 0, 0, 0);
        mPrinter.setPrinter(Command.ALIGN, 0);
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 3);
    }

    /**
     * Prints a barcode to the printer
     * @param mPrinter
     */
    public static void printBarcodeTest(PrinterInstance mPrinter) {

        mPrinter.init();
        mPrinter.printText(("R.string.print") + "BarcodeType.CODE39" + ("R.string.str_show"));
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);
        Barcode barcode0 = new Barcode(PrinterConstants.BarcodeType.CODE39, 2, 150, 2, "123456");
        mPrinter.printBarCode(barcode0);
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);

        mPrinter.printText(("R.string.print") + " BarcodeType.CODABAR" + ("R.string.str_show"));
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);
        Barcode barcode1 = new Barcode(PrinterConstants.BarcodeType.CODABAR, 2, 150, 2, "123456");
        mPrinter.printBarCode(barcode1);
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);

        mPrinter.printText(("R.string.print") + "BarcodeType.ITF" + ("R.string.str_show"));
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);
        Barcode barcode2 = new Barcode(PrinterConstants.BarcodeType.ITF, 2, 150, 2, "123456");
        mPrinter.printBarCode(barcode2);
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);

        mPrinter.printText(("R.string.print") + "BarcodeType.CODE93" + ("R.string.str_show"));
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);
        Barcode barcode3 = new Barcode(PrinterConstants.BarcodeType.CODE93, 2, 150, 2, "123456");
        mPrinter.printBarCode(barcode3);
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);

        mPrinter.printText(("R.string.print") + " BarcodeType.CODE128" + ("R.string.str_show"));
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);
        Barcode barcode4 = new Barcode(PrinterConstants.BarcodeType.CODE128, 2, 150, 2, "123456");
        mPrinter.printBarCode(barcode4);
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);

        mPrinter.printText(("R.string.print") + " BarcodeType.UPC_A" + ("R.string.str_show"));
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);
        Barcode barcode5 = new Barcode(PrinterConstants.BarcodeType.UPC_A, 2, 63, 2, "000000000000");
        mPrinter.printBarCode(barcode5);
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);

        mPrinter.printText(("R.string.print") + "BarcodeType.UPC_E" + ("R.string.str_show"));
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);
        Barcode barcode6 = new Barcode(PrinterConstants.BarcodeType.UPC_E, 2, 63, 2, "000000000000");
        mPrinter.printBarCode(barcode6);
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);

        mPrinter.printText(("R.string.print") + "BarcodeType.JAN13" + ("R.string.str_show"));
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);
        Barcode barcode7 = new Barcode(PrinterConstants.BarcodeType.JAN13, 2, 63, 2, "000000000000");
        mPrinter.printBarCode(barcode7);
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);

        mPrinter.printText(("R.string.print") + "BarcodeType.JAN8" + ("R.string.str_show"));
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);
        Barcode barcode8 = new Barcode(PrinterConstants.BarcodeType.JAN8, 2, 63, 2, "0000000");
        mPrinter.printBarCode(barcode8);
        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);


//        mPrinter.printText(("R.string.print") + "BarcodeType.PDF417" + ("R.string.str_show"));
//        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);
//        Barcode barcode9 = new Barcode(PrinterConstants.BarcodeType.PDF417, 2, 3, 6, "123456");
//        mPrinter.printBarCode(barcode9);
//        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);
//
//        mPrinter.printText(("R.string.print") + " BarcodeType.QRCODE" + ("R.string.str_show"));
//        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);
//        Barcode barcode10 = new Barcode(PrinterConstants.BarcodeType.QRCODE, 2, 3, 6, "123456");
//        mPrinter.printBarCode(barcode10);
//        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);
//
//        mPrinter.printText(("R.string.print") + "BarcodeType.DATAMATRIX" + ("R.string.str_show"));
//        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);
//        Barcode barcode11 = new Barcode(PrinterConstants.BarcodeType.DATAMATRIX, 2, 3, 6, "123456");
//        mPrinter.printBarCode(barcode11);
//        mPrinter.setPrinter(Command.PRINT_AND_WAKE_PAPER_BY_LINE, 2);
    }


    public static void printBigDataTest(Resources resources, PrinterInstance mPrinter) {
        try {
            InputStream is = resources.getAssets().open("58-big-data-test.bin");
            int length = is.available();
            byte[] fileByte = new byte[length];
            is.read(fileByte);
            mPrinter.init();
            mPrinter.sendByteData(fileByte);
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
