package com.yimt;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class Utils {

    // 服务请求
    public static String requestService(String urlString, String data, String method) throws IOException {
        int CONN_TIMEOUT = 15000;
        int READ_TIMEOUT = 15000;

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONN_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestMethod(method);
        conn.setDoInput(true);

        if(data != null) {
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // 发送请求数据
            OutputStream os = conn.getOutputStream();
            os.write(data.getBytes(StandardCharsets.UTF_8));
            os.close();
        }

        int responseCode = conn.getResponseCode();

        Log.d("Yimt", "RCode: " + String.valueOf(responseCode));

        if (responseCode >= 400 && responseCode <= 599) {
            // 如果响应码是错误的，获取错误流
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }

            in.close();
            // 输出错误响应内容
            // System.out.println("Error response: " + response.toString());
            throw new IOException(response.toString());
        } else {
            // 获取并处理响应数据
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            reader.close();

            conn.disconnect();

            return response.toString();
        }

    }

    public static JSONObject requestService(String urlString, String data) throws IOException, JSONException {
        return new JSONObject(requestService(urlString, data, "POST"));
    }

    public static String requestService(String urlString) throws IOException {
        return requestService(urlString, null, "GET");
    }

    public static HashMap<String, String> parseLanguages(String languages){
        String[] parts = languages.split(",");

        HashMap<String, String> result = new HashMap<String, String>();
        result.put("auto", "自动检测");
        for(int i=0; i<parts.length; i++){
            String[] p = parts[i].split("=");
            result.put(p[0], p[1]);
        }

        return result;
    }

    public static String encodeFileToBase64(String filePath) throws IOException {
        byte[] audioData = readFileToByteArray(filePath);
        return Base64.encodeToString(audioData, Base64.DEFAULT);
    }

    public static String encodeAudioFileToBase64(String filePath) throws IOException {
        byte[] audioData = readFileToByteArray(filePath);
        Log.i("YIMT", String.valueOf(audioData.length));
        return Base64.encodeToString(audioData, Base64.DEFAULT);
    }

    private static byte[] readFileToByteArray(String filePath) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(filePath);
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }

            return byteArrayOutputStream.toByteArray();
        }
    }

}
