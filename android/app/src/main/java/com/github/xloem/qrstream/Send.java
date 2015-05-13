package com.github.xloem.qrstream;

import com.google.zxing.integration.android.IntentIntegrator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.CharBuffer;


public class Send extends Activity {

    private String data;
    private Uri uri;
    private Reader dataReader;
    private int offset;

    private CharBuffer buffer;
    private int index;
    private int total;

    private String codeFormat;

    private boolean waiting;

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("index", index);
        outState.putInt("total", total);
        outState.putInt("offset", offset);
        outState.putString("data", data);
        outState.putParcelable("uri", uri);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        codeFormat = sharedPref.getString("code_format", "QR_CODE");

        // Guess the max barcode capacity given a target cell size
        float minCellMicrometers = Float.valueOf(sharedPref.getString("cell_size", "640"));
        int codeCapacity;

        Display display = getWindowManager().getDefaultDisplay();
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float displayWidth = display.getWidth() / metrics.xdpi;
        float displayHeight = display.getHeight() / metrics.ydpi;
        float displayInches = displayWidth < displayHeight ? displayWidth : displayHeight;

        displayInches *= 0.82; // guesstimate at zxing display % of display width
        int maxSize = (int)(displayInches * 25400f / minCellMicrometers);

        if (codeFormat == "AZTEC")
            codeCapacity = Metrics.aztecCapacity(maxSize);
        else
            // zxing adds a 4 cell quiet zone on each side of a QR code
            codeCapacity = Metrics.qrcodeCapacity(maxSize - 8);

        int dataRemaining;

        if (savedInstanceState == null) {
            Intent intent = getIntent();
            index = 0;
            offset = 0;
            if (intent.getAction().equals(Intent.ACTION_SEND)) {
                data = intent.getStringExtra(Intent.EXTRA_TEXT);
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            } else {
                Toast.makeText(getApplicationContext(), "QRStream Send launched with unexpected intent " + intent.getAction(), Toast.LENGTH_LONG).show();
                setResult(RESULT_CANCELED, getIntent());
                finish();
                return;
            }
        } else {
            data = savedInstanceState.getString("data");
            uri = savedInstanceState.getParcelable("uri");
            index = savedInstanceState.getInt("index");
            offset = savedInstanceState.getInt("offset");
        }

        try {

            if (data != null) {
                dataReader = new StringReader(data);
                dataRemaining = data.length();
            } else {
                InputStream istream = getContentResolver().openInputStream(uri);
                dataReader = new InputStreamReader(istream, "ISO-8859-1");
                dataRemaining = istream.available();
            }
            dataRemaining -= offset;
            dataReader.skip(offset);

        } catch (FileNotFoundException e) {
            Toast.makeText(getApplicationContext(), "File not found: " + String.valueOf(uri), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            cancel();
            return;
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "IO Error: " + e.getMessage() + ": " + String.valueOf(uri), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            cancel();
            return;
        }

        // total number of blocks if we use max size
        total = (dataRemaining - 1) / codeCapacity + 1;
        // use actual average size given total number
        buffer = CharBuffer.allocate((dataRemaining - 1) / total + 1);

        // Warn if there will be a lot of codes to display
        if (offset == 0
            && total > Integer.valueOf(sharedPref.getString("warning_threshold", "20")))
        {
            waiting = true;

            AlertDialog.Builder confirmationDialog = new AlertDialog.Builder(this);
            confirmationDialog.setTitle("Are you sure?");
            confirmationDialog.setMessage("The current cell size will require " + String.valueOf(total) + " barcodes for this data.  If this is too many you can try decreasing the cell size.");
            confirmationDialog.setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    waiting = false;
                    writeOne();
                }
            });
            confirmationDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    cancel();
                }

            });
            confirmationDialog.show();
        } else {
            waiting = false;

            total += index;

            if (offset == 0) {
                Toast.makeText(getApplicationContext(), "Sending " + String.valueOf(dataRemaining) + " bytes in " + String.valueOf(total) + " codes", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!waiting)
            writeOne();
    }

    private void cancel() {
        setResult(RESULT_CANCELED, getIntent());
        finish();
    }

    private void writeOne() {
        int len;
        try {
            buffer.rewind();
            buffer.limit(buffer.capacity());
            len = dataReader.read(buffer);
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "IOException " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            cancel();
            return;
        }
        if (len > 0) {
            index ++;
            offset += len;

            IntentIntegrator integrator = new IntentIntegrator(this);
            integrator.addExtra("ENCODE_SHOW_CONTENTS", false);
            buffer.rewind();
            buffer.limit(len);
            integrator.addExtra("ENCODE_FORMAT", codeFormat);
            if (integrator.shareText(buffer) != null)
                // zxing not installed
                cancel();
        } else {
            setResult(RESULT_OK, getIntent());
            finish();
        }
    }

}
