package com.github.xloem.qrstream;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.view.View;
import android.widget.Toast;


import com.github.xloem.qrstream.R;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.CharBuffer;

public class Launcher extends Activity {

    private static final int REQUEST_CODE_RECEIVE = 0x0f01;
    private static final int REQUEST_CODE_FILE = 0x0f02;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        findViewById(R.id.receive_stream_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(v.getContext(), Receive.class);
                startActivityForResult(intent, REQUEST_CODE_RECEIVE);
            }
        });

        findViewById(R.id.send_file_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, REQUEST_CODE_FILE);
            }
        });

        findViewById(R.id.send_clipboard_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clip = (ClipboardManager)v.getContext().getSystemService(CLIPBOARD_SERVICE);
                if (!clip.hasText()) {
                    Toast.makeText(getApplicationContext(), "Found nothing in clipboard.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(v.getContext(), Send.class);
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, clip.getText().toString());
                startActivity(intent);
                finish();
            }
        });

        findViewById(R.id.settings_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(v.getContext(), Settings.class);
                startActivity(intent);
            }
        });
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode != Activity.RESULT_OK || intent == null || intent.getData() == null)
            return;

        Uri dataUri = intent.getData();

        switch(requestCode) {
        case REQUEST_CODE_RECEIVE:
            try {
                InputStream stream = getContentResolver().openInputStream(dataUri);
                InputStreamReader reader = new InputStreamReader(stream);

                CharBuffer buffer = CharBuffer.allocate(stream.available());
                while (reader.ready()) {
                    reader.read(buffer);
                }
                buffer.rewind();
    
                ClipboardManager clip = (ClipboardManager)getApplicationContext().getSystemService(CLIPBOARD_SERVICE);
                clip.setText(buffer.toString());
                Toast.makeText(getApplicationContext(), "Result in clipboard and " + dataUri.getPath(), Toast.LENGTH_LONG).show();
    
            } catch (FileNotFoundException e) {
                Toast.makeText(getApplicationContext(), "File not found: " + dataUri.toString(), Toast.LENGTH_LONG).show();
                e.printStackTrace();
                return;
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), "IO Error: " + e.getMessage() + ": " + dataUri.toString(), Toast.LENGTH_LONG).show();
                e.printStackTrace();
                return;
            }
            break;

        case REQUEST_CODE_FILE:
            intent = new Intent(this, Send.class);
            intent.setAction(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_STREAM, dataUri);
            startActivity(intent);
            break;

        default:
            finish();
            break;
        }
    }

}
