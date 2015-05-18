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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;


public class Receive extends Activity {

    private int index;
    private byte[] lastBytes;

    private File tempFile;
    private BufferedWriter tempWriter;

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        try {
            tempWriter.flush();
            outState.putInt("index", index);
            outState.putByteArray("lastBytes", lastBytes);
        } catch( IOException e) {
            Toast.makeText(getApplicationContext(), String.format(getString(R.string.except_io), e.getMessage(), tempFile.getPath()), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            setResult(RESULT_CANCELED, getIntent());
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tempFile = new File(getApplicationContext().getExternalCacheDir(), "qrstream");

        if (savedInstanceState == null) {
            index = 1;
            lastBytes = null;
            tempFile.delete();

            readOne();
        } else {
            index = savedInstanceState.getInt("index");
            lastBytes = savedInstanceState.getByteArray("lastBytes");
        }

        try {
            tempWriter = new BufferedWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(tempFile, true),
                            "ISO-8859-1"
                    ));
        } catch( IOException e) {
            Toast.makeText(getApplicationContext(), String.format(getString(R.string.except_io), e.getMessage(), tempFile.getPath()), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            setResult(RESULT_CANCELED, getIntent());
            finish();
        }
    }

    private void readOne() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.addExtra("RESULT_DISPLAY_DURATION_MS", Long.valueOf(sharedPref.getString("scan_delay", "0")));
        integrator.addExtra("PROMPT_MESSAGE", String.format(getString(R.string.receive_zxing_prompt), index));

        if(integrator.initiateScan(Arrays.asList("QR_CODE", "AZTEC")) != null) {

            // zxing not installed
            setResult(RESULT_CANCELED, getIntent());
            finish();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (result != null) try {
            if (result.getFormatName() != null) {
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
                byte[] bytes = result.getRawBytes();
                if (bytes == null)
                    bytes = result.getContents().getBytes("ISO-8859-1");
                if (!sharedPref.getBoolean("drop_duplicates", true)
                    || !Arrays.equals(bytes, lastBytes))
                {
                    // not a rescan of last code
                    lastBytes = bytes;

                    // If this is a single blob of bytes, preserve binary data.
                    bytes = intent.getByteArrayExtra("SCAN_RESULT_BYTE_SEGMENTS_0");
                    if (bytes != null && intent.getByteArrayExtra("SCAN_RESULT_BYTE_SEGMENTS_1") == null) {
                        tempWriter.write(new String(bytes, "ISO-8859-1"));
                    } else {
                        tempWriter.write(result.getContents());
                    }
                    index++;

                }

                readOne();
            } else {
                // zxing returned without result, done
    
                tempWriter.close();
    
                if (tempFile.length() > 0) {
                    Intent aIntent = getIntent();
                    aIntent.setData(Uri.fromFile(tempFile));
                    setResult(RESULT_OK, aIntent);
                } else {
                    setResult(RESULT_CANCELED, getIntent());
                }
                finish();
            }
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), String.format(getString(R.string.except_io), e.getMessage(), tempFile.getPath()), Toast.LENGTH_LONG).show();
            e.printStackTrace();

            setResult(RESULT_CANCELED, getIntent());
            finish();
        }

    }
}
