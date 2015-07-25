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

import java.io.BufferedReader;
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

    private int remaining;
    private CharBuffer buffer;
    private int index;
    private int total;

    private BarcodeFormat codeFormat;
    Map<EncodeHintType,Object> hints;
    private CodeMetric codeMetric;
    private int displaySize;
    private float displayInches;
    private Bitmap bitmap;

    SharedPreferences sharedPref;

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

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        hints = new EnumMap<EncodeHintType,Object>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "ISO-8859-1");
        codeFormat = BarcodeFormat.valueOf(sharedPref.getString("code_format", "QR_CODE"));
        codeMetric = CodeMetric.create(codeFormat);
        if (codeFormat == BarcodeFormat.QR_CODE) {
            // we specify a 2-cell margin on all sides
            // TODO: make this a preference
            codeMetric.setMargin(2);
            hints.put(EncodeHintType.MARGIN, codeMetric.getMargin());
        }

        LinearLayout rootLayout = (LinearLayout) findViewById(R.id.root_layout);
        Display display = getWindowManager().getDefaultDisplay();
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int displayWidth = display.getWidth();
        int displayHeight = display.getHeight();
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
                remaining = data.length();
            } else {
                InputStream istream = getContentResolver().openInputStream(uri);
                dataReader = new BufferedReader(new InputStreamReader(istream, "ISO-8859-1"));
                remaining = istream.available();
            }
            remaining -= offset;
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

        setMetricFromPreference();
        allocateBufferFromMetric();

        if (offset == 0) {
            Toast.makeText(getApplicationContext(), String.format(getString(R.string.send_sending_summary), remaining, total), Toast.LENGTH_SHORT).show();
        }
    }

    private void setMetricFromPreference() {
        // Guess the max barcode capacity given a target cell size
        float minCellMicrometers = Float.valueOf(sharedPref.getString("cell_size", "640"));
        int codeSize = (int)(displayInches * 25400f / minCellMicrometers);

        codeMetric.setDimension(codeSize);
    }

    private void allocateBufferFromMetric() {
        int codeCapacity = codeMetric.getCapacity();

        // total number of blocks if we use max size
        total = (remaining - 1) / codeCapacity + 1;
        // use actual average size given total number
        buffer = CharBuffer.allocate((remaining - 1) / total + 1);

        total += index;
    }

    private void setPreferenceFromMetric() {
        int codeSize = codeMetric.getDimension();
        float displayMicrometers = displayInches * 25400f;
        int minCellMicrometers = (int)(displayMicrometers / (codeSize + 1)) + 1;
        sharedPref.edit().putString("cell_size", String.valueOf(minCellMicrometers)).commit();
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
        switch(code) {
            case KeyEvent.KEYCODE_CAMERA:
            case KeyEvent.KEYCODE_MENU:
                readOne();
                writeOne();
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
                unreadOne();
                int capacity = buffer.capacity();
                try {
                    do {
                        if (code == KeyEvent.KEYCODE_VOLUME_DOWN)
                            codeMetric.grow();
                        else
                            codeMetric.shrink();
                        allocateBufferFromMetric();
                    } while (capacity == buffer.capacity());
                } catch (IndexOutOfBoundsException e) { }
                setPreferenceFromMetric();
                readOne();
                writeOne();
                return true;
            default:
                return super.onKeyDown(code, event);
        }
    }


    private void cancel() {
        setResult(RESULT_CANCELED, getIntent());
        finish();
    }

    private void readOne() {
        try {
            buffer.rewind();
            buffer.limit(buffer.capacity());
            dataReader.mark(buffer.capacity());
            int len = dataReader.read(buffer);
            if (len <= 0) {
                setResult(RESULT_OK, getIntent());
                finish();
            }
            index ++;
            offset += len;
            remaining -= len;
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), String.format(getString(R.string.except_io), e.getMessage(), "readOne()"), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            cancel();
        }
    }

    private void unreadOne() {
        try {
            dataReader.reset();
            int len = buffer.position();
            remaining += len;
            offset -= len;
            index--;
        } catch(IOException e) {
            Toast.makeText(getApplicationContext(), String.format(getString(R.string.except_io), e.getMessage(), "unreadOne()"), Toast.LENGTH_LONG);
        }
    }

    private void writeOne() {
        int len = buffer.position();
        if (len == 0)
            return;

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
        buffer.position(len);

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
