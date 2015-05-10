package com.github.xloem.qrstream;


import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;


public class Receive extends Activity {

    private int index;
    private byte[] lastBytes;

    private File tempFile;
    private BufferedWriter tempWriter;

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("index", index);
        outState.putByteArray("lastBytes", lastBytes);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tempFile = new File(getApplicationContext().getExternalCacheDir(), "qrstream.txt");

        if (savedInstanceState == null) {
            index = 1;
            lastBytes = null;
            tempFile.delete();
        } else {
            index = savedInstanceState.getInt("index");
            lastBytes = savedInstanceState.getByteArray("lastBytes");
        }

        try {
            tempWriter = new BufferedWriter(new FileWriter(tempFile, true));
        } catch( IOException e) {
            Toast.makeText(getApplicationContext(), "ERROR: Failed to open " + tempFile.getPath(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            setResult(RESULT_CANCELED, getIntent());
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        readOne();
    }

    private void readOne() {

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        IntentIntegrator integrator = new IntentIntegrator(this);
        // TODO: handle if zxing is not installed (AlertDialog is returned rather than null)
        integrator.addExtra("RESULT_DISPLAY_DURATION_MS", Long.valueOf(sharedPref.getString("scan_delay", "0")));
        integrator.addExtra("PROMPT_MESSAGE", "Scan QR Code #" + String.valueOf(index) + " or hit back if done.");
        integrator.initiateScan(Arrays.asList("QR_CODE", "AZTEC"));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (result != null) try {
            if (result.getFormatName() != null) {
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
                byte[] bytes = result.getRawBytes();
                if (sharedPref.getBoolean("drop_duplicates", true)
                    && Arrays.equals(bytes, lastBytes))
                {
                    // rescan of last qr
                    return;
                }

                tempWriter.write(result.getContents());
                index++;
                lastBytes = bytes;

                // readOne() will be called in onResume
                return;
            }

            // zxing returned without result, done

            tempWriter.close();

            if (tempFile.length() > 0) {
                Intent aIntent = getIntent();
                aIntent.setData(Uri.fromFile(tempFile));
                setResult(RESULT_OK, aIntent);
            } else {
                setResult(RESULT_CANCELED, getIntent());
            }
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "ERROR: Failed to write to " + tempFile.getPath(), Toast.LENGTH_LONG).show();
            e.printStackTrace();

            setResult(RESULT_CANCELED, getIntent());
        }

        finish();
    }
}
