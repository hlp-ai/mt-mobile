package com.yimt;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import java.io.File;
import java.io.IOException;


public class ImageUtils {
    public static final int CODE_SETIMG_ALNUM = 572;
    public static final int CODE_SETIMG_CAM = 231;
    public static final int CODE_CROP_IMG = 318;


    public File camImgFile = null;
    public File cropImgFile = null;

    public Uri camImageUril = null;

    public void gotoCam(Activity context) {
        //获取当前系统的android版本号
        int currentApiVersion = android.os.Build.VERSION.SDK_INT;

//        //路径默认，若修改则不能保存照片
//        camImgFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
//                "cam.jpg");
//
//        try {
//            if (camImgFile.exists())
//                camImgFile.delete();
//            camImgFile.createNewFile();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        Uri outputImgUriFromCam;
        if (currentApiVersion < 24) {
            camImgFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    "cam.jpg");
            outputImgUriFromCam = Uri.fromFile(camImgFile);
        }
        else {
            if (currentApiVersion < 29) { // Android 7+
                camImgFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                        "cam.jpg");

                ContentValues contentValues = new ContentValues(1);
                contentValues.put(MediaStore.Images.Media.DATA, camImgFile.getAbsolutePath());

                outputImgUriFromCam = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);

                Log.d("yimt", "相机输出Uri: " + outputImgUriFromCam.toString());
            } else { // Android 10+
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.Video.Media.DISPLAY_NAME, "yimt-photo.jpg");
                contentValues.put(MediaStore.Video.Media.MIME_TYPE, "image/jpeg");

                outputImgUriFromCam = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);

                Log.d("yimt", "相机输出Uri: " + outputImgUriFromCam.toString());
            }
        }

        camImageUril = outputImgUriFromCam;

        //跳转到照相机拍照
        Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        it.putExtra(MediaStore.EXTRA_OUTPUT, outputImgUriFromCam);
        context.startActivityForResult(it, CODE_SETIMG_CAM);
    }

    public void gotoAlbum(Activity context) {
        Intent it = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        context.startActivityForResult(it, CODE_SETIMG_ALNUM);
    }

    public void cropImg(Activity context, boolean isFromCam, Intent data) {
        File inputFile;

        if (isFromCam) {
            inputFile = camImgFile;
        } else {
            inputFile = new File(getRealPathFromURI(context, data.getData()));
        }

        //设置保存路径名称
        cropImgFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "yimt_crop_" + System.currentTimeMillis() + ".jpg");

//        try {
//            if (cropImgFile.exists()) {
//                cropImgFile.delete();
//            }
//            cropImgFile.createNewFile();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

//        WindowManager manager = context.getWindowManager();
//        DisplayMetrics outMetrics = new DisplayMetrics();
//        manager.getDefaultDisplay().getMetrics(outMetrics);

        Intent it = new Intent("com.android.camera.action.CROP");
        Uri imageUri = camImageUril; // getImageContentUri(context, inputFile);
        Log.d("yimt", "启动裁剪Uri: " + imageUri.toString());
        it.setDataAndType(imageUri, "image/jpg");

        it.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(cropImgFile));
        it.putExtra("return-data", false);
        it.putExtra("crop", "true");
        it.putExtra("scale", true); //缩放
        // 返回格式
        it.putExtra("outputFormat", "JPEG");

//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
//            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//        }

        Log.d("yimt", "启动裁剪: " + inputFile);

        context.startActivityForResult(it, CODE_CROP_IMG);
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
        Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{"_id"}, "_data=? ",
                new String[]{filePath}, (String) null);
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

}