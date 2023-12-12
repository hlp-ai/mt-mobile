package com.yimt;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.LongBuffer;

import java.nio.channels.FileChannel;
import java.util.Arrays;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ex extends AppCompatActivity {

    private Interpreter tflite;
    private EditText inputText;
    private TextView outputText;
    private static final String TAG = "TFLite";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ex);

        inputText = findViewById(R.id.source);
        outputText = findViewById(R.id.target);
        Button inferButton = findViewById(R.id.button);
        Button detectButton = findViewById(R.id.image);

        inferButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    tflite = new Interpreter(loadModelFile("model.tflite"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                String inputString = inputText.getText().toString();
                long[] tokenizedInput = tokenize(inputString);

                tflite.resizeInput(0, new int[]{1, tokenizedInput.length});
                tflite.allocateTensors();

//                LongBuffer outputs = LongBuffer.allocate(15);
                long[][] inputs = new long[1][tokenizedInput.length];
                inputs[0] = tokenizedInput;

                long time = System.currentTimeMillis();
                tflite.run(inputs, null);

                ByteBuffer outputBuffer = tflite.getOutputTensor(0).asReadOnlyBuffer();

                Log.d(TAG, "time cost: " + (System.currentTimeMillis() - time));
                // 将outputBuffer的值输出打印到TextView
                LongBuffer longBuffer = outputBuffer.asLongBuffer();
                long[] longArray = new long[longBuffer.remaining()];
                longBuffer.get(longArray);
                outputText.setText(Arrays.toString(longArray));
            }
        });

        detectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    // Load the model file
                    tflite = new Interpreter(loadModelFile("save_model.tflite"));
                    String inputString = inputText.getText().toString();
                    long[] tokenizedInput = tokenize(inputString);

                    // Resize the input tensor to match the shape of the input image
                    tflite.resizeInput(0, new int[]{1, (int) tokenizedInput[0], (int) tokenizedInput[1], 3});
                    tflite.allocateTensors();

                    // Create a buffer for the input data and set the input data
                    float[][][][] input = new float[1][(int) tokenizedInput[0]][(int) tokenizedInput[1]][3];

                    // Invoke the model
                    tflite.run(input, null);

                    Tensor outputs = tflite.getOutputTensor(0);
                    Log.d("TFLite", "Output shape: " + Arrays.toString(outputs.shape()));
                    outputText.setText(Arrays.toString(outputs.shape()));

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

    }

    private MappedByteBuffer loadModelFile(String modelName) throws IOException {
        FileInputStream inputStream = new FileInputStream(getAssets().openFd(modelName).getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = getAssets().openFd(modelName).getStartOffset();
        long declaredLength = getAssets().openFd(modelName).getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private long[] tokenize(String text) {
        // 以空格分割字符串，转为long数组
        String[] tokens = text.split(" ");
        long[] result = new long[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            result[i] = Long.parseLong(tokens[i]);
        }
        return result;
    }
}