package com.yimt;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ImageUtils {
    public static final int CODE_SETHDIMG_ALNUM = 572;
    public static final int CODE_SETHDIMG_CAM = 231;
    public static final int CODE_CROP_IMG = 318;


    public File camImgFile = null;
    public File cropImgFile = null;

    public void gotoCam(Activity context) {
        //获取当前系统的android版本号
        int currentApiVersion = android.os.Build.VERSION.SDK_INT;

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String str = dateFormat.format(new Date(System.currentTimeMillis()));

        //设置保存拍摄照片路径(DCIM/Camera/Modle_PictureWall_img_20170212_122223.jpg)
        //路径默认，若修改则不能保存照片
        camImgFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "/Camera/LaibaDoctor_img_" + str + ".jpg");
        try {
            if (camImgFile.exists()) {
                camImgFile.delete();
            }
            camImgFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Uri outputImgUriFromCam;
        if (currentApiVersion < 24) {
            outputImgUriFromCam = Uri.fromFile(camImgFile);
        } else {
            ContentValues contentValues = new ContentValues(1);
            contentValues.put(MediaStore.Images.Media.DATA, camImgFile.getAbsolutePath());

            outputImgUriFromCam = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
        }

        //跳转到照相机拍照
        Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        it.putExtra(MediaStore.EXTRA_OUTPUT, outputImgUriFromCam);
        context.startActivityForResult(it, CODE_SETHDIMG_CAM);
    }

    public void gotoAlbum(Activity context) {
        Intent it = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        (context).startActivityForResult(it, CODE_SETHDIMG_ALNUM);
    }

    /**
     * 裁剪图片
     * @param context 上下文
     * @param isFromCam 是否来自于相机
     * @param data      图片返回的uri
     * @param fileName  路径名称
     */

    public void cropImg(Activity context, boolean isFromCam, Intent data, String fileName) {
        File inputFile;

        if (isFromCam) {
            inputFile = camImgFile;
        } else {
            inputFile = new File(getRealPathFromURI(context, data.getData()));
        }
        //设置保存路径名称
        cropImgFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),fileName+String.valueOf(System.currentTimeMillis()) + ".jpg");
        try {
            if (cropImgFile.exists()) {
                cropImgFile.delete();
            }
            cropImgFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        WindowManager manager = context.getWindowManager();
        DisplayMetrics outMetrics = new DisplayMetrics();
        manager.getDefaultDisplay().getMetrics(outMetrics);

        Intent it = new Intent("com.android.camera.action.CROP");
        it.setDataAndType(getImageContentUri(context, inputFile), "image/jpg");

        it.putExtra("output", Uri.fromFile(cropImgFile));
        it.putExtra("crop", "true");

        it.putExtra("scale", true); //缩放

        // 返回格式
        it.putExtra("outputFormat", "JPEG");

        (context).startActivityForResult(it, CODE_CROP_IMG);
    }

    public String getRealPathFromURI(Activity context, Uri uri) {
        String[] filePathColumn = {MediaStore.Images.Media.DATA};

        Cursor cursor = context.getContentResolver().query(uri, filePathColumn, null, null, null);

        cursor.moveToFirst();

        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);

        String picturePath = cursor.getString(columnIndex);

        cursor.close();

        return picturePath;
    }

    public Uri getImageContentUri(Activity context, File imageFile) {
        String filePath = imageFile.getAbsolutePath();
        Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[]{"_id"}, "_data=? ", new String[]{filePath}, (String) null);
        if (cursor != null && cursor.moveToFirst()) {
            @SuppressLint("Range") int values1 = cursor.getInt(cursor.getColumnIndex("_id"));
            Uri baseUri = Uri.parse("content://media/external/images/media");
            return Uri.withAppendedPath(baseUri, "" + values1);
        } else if (imageFile.exists()) {
            ContentValues values = new ContentValues();
            values.put("_data", filePath);
            return context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        } else {
            return null;
        }
    }

    public static String encodebase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] bytes = baos.toByteArray();

        return new String(Base64.encode(bytes, Base64.DEFAULT));
    }

    public void refreshAlbum(Context context, String path) {
        context.sendBroadcast(new Intent("android.intent.action.MEDIA_SCANNER_SCAN_FILE", Uri.parse("file://" + path)));
    }

    public File getImg(Activity context, boolean isFromCam, boolean isSetHd, Intent data, String fileName) {
        File inputFile = null;
        try {
            if (isFromCam) {
                inputFile = camImgFile;
            } else {
                inputFile = new File(getRealPathFromURI(context, data.getData()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        return inputFile;

    }
}