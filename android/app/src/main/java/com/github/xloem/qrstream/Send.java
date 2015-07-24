package com.github.xloem.qrstream;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.CharBuffer;
import java.util.EnumMap;
import java.util.Map;


public class Send extends Activity {

    private String data;
    private Uri uri;
    private Reader dataReader;
    private int offset;

    private CharBuffer buffer;
    private int index;
    private int total;

    private BarcodeFormat codeFormat;
    Map<EncodeHintType,Object> hints;
    private int displaySize;
    private Bitmap bitmap;

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
        setContentView(R.layout.activity_send);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        codeFormat = BarcodeFormat.valueOf(sharedPref.getString("code_format", "QR_CODE"));

        hints = new EnumMap<EncodeHintType,Object>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "ISO-8859-1");

        LinearLayout rootLayout = (LinearLayout) findViewById(R.id.root_layout);
        Display display = getWindowManager().getDefaultDisplay();
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int displayWidth = display.getWidth();
        int displayHeight = display.getHeight();
        float displayInches;
        if (displayWidth < displayHeight) {
            displayInches = displayWidth / metrics.xdpi;
            displaySize = displayWidth;
            rootLayout.setOrientation(LinearLayout.VERTICAL);
        } else {
            displayInches = displayHeight / metrics.ydpi;
            displaySize = displayHeight;
            rootLayout.setOrientation(LinearLayout.HORIZONTAL);
        }
        bitmap = Bitmap.createBitmap(displaySize, displaySize, Bitmap.Config.ARGB_8888);


        // Guess the max barcode capacity given a target cell size
        float minCellMicrometers = Float.valueOf(sharedPref.getString("cell_size", "640"));
        int codeSize = (int)(displayInches * 25400f / minCellMicrometers);

        if (codeFormat == BarcodeFormat.QR_CODE) {
            // we specify a 2-cell margin on all sides
            // TODO: make this a preference
            hints.put(EncodeHintType.MARGIN, 2);
            codeSize -= 4;
        }

        CodeMetric metric = CodeMetric.create(codeFormat);
        metric.setDimension(codeSize);
        int codeCapacity = metric.getCapacity();

        int dataRemaining;

        if (savedInstanceState == null) {
            Intent intent = getIntent();
            index = 0;
            offset = 0;
            if (intent.getAction().equals(Intent.ACTION_SEND)) {
                data = intent.getStringExtra(Intent.EXTRA_TEXT);
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            } else {
                Toast.makeText(getApplicationContext(), String.format(getString(R.string.except_unexpected_intent), getString(R.string.app_name) + " Send", intent.getAction()), Toast.LENGTH_LONG).show();
                cancel();
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
            Toast.makeText(getApplicationContext(), String.format(getString(R.string.except_file_not_found), String.valueOf(uri)), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            cancel();
            return;
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), String.format(getString(R.string.except_io), e.getMessage(), String.valueOf(uri)), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            cancel();
            return;
        }

        // total number of blocks if we use max size
        total = (dataRemaining - 1) / codeCapacity + 1;
        // use actual average size given total number
        buffer = CharBuffer.allocate((dataRemaining - 1) / total + 1);

        total += index;

        if (offset == 0) {
            Toast.makeText(getApplicationContext(), String.format(getString(R.string.send_sending_summary), dataRemaining, total), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (buffer.position() == 0)
            readOne();
        writeOne();
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent event) {
        if (super.onKeyDown(code, event))
            return true;
        if (code == KeyEvent.KEYCODE_CAMERA
                || code == KeyEvent.KEYCODE_SEARCH
                || code == KeyEvent.KEYCODE_CALL) {
            readOne();
            writeOne();
            return true;
        }
        return false;
    }


    private void cancel() {
        setResult(RESULT_CANCELED, getIntent());
        finish();
    }

    private void readOne() {
        try {
            buffer.rewind();
            buffer.limit(buffer.capacity());
            int len = dataReader.read(buffer);
            if (len <= 0) {
                setResult(RESULT_OK, getIntent());
                finish();
            }
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), String.format(getString(R.string.except_io), e.getMessage(), ""), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            cancel();
        }
    }

    private void writeOne() {
        int len = buffer.position();
        if (len == 0)
            return;
        index ++;
        offset += len;

        buffer.rewind();
        buffer.limit(len);
        BitMatrix bitmatrix;
        try {
            bitmatrix = new MultiFormatWriter().encode(buffer.toString(), codeFormat, displaySize, displaySize, hints);
        } catch (WriterException e) {
            Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            cancel();
            return;
        }

        int[] pixels = new int[displaySize * displaySize];
        int pixelIndex = 0;
        for (int y = 0; y < displaySize; y++) {
            for (int x = 0; x < displaySize; x++) {
                pixels[pixelIndex ++] = bitmatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }

        bitmap.setPixels(pixels, 0, displaySize, 0, 0, displaySize, displaySize);

        ImageView view = (ImageView) findViewById(R.id.image_view);
        view.setImageBitmap(bitmap);

        TextView codeLabel = (TextView) findViewById(R.id.code_label);
        codeLabel.setText(String.format(getString(R.string.send_code_label), index, total, len, offset - len));
    }

}
