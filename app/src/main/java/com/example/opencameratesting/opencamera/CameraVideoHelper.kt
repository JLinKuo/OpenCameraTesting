package com.example.opencameratesting.opencamera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.opencameratesting.CameraInterface
import com.example.opencameratesting.CameraInterface.*
import com.example.opencameratesting.opencamera.Preview.Preview
import java.io.*

private const val MAX_IMAGE_LENGTH = 640
private const val CAMERA_FLASH_OFF = "flash_off"        // preview index: 0
private const val CAMERA_FLASH_AUTO = "flash_auto"      // preview index: 1
private const val CAMERA_FLASH_ON = "flash_on"          // preview index: 2

class CameraVideoHelper(
    val fragment: Fragment,
    texture: ViewGroup,
    isVideoMode: Boolean,
    orientation: String
) {
    private val TAG = javaClass.simpleName
    private lateinit var videoFile: File
    private lateinit var imageFile: File
    private var cameraVideoErrorListener: CameraVideoErrorListener? = null

    val cameraInterface by lazy {
        CameraInterface(this, isVideoMode, orientation)
    }
    val preview by lazy { Preview(cameraInterface, texture) }

    fun setBackFrontCamera(cameraId: Int): CameraVideoHelper {
        cameraInterface.cameraIdPref = cameraId
        return this
    }

    fun startRecordingVideo() {
        setVideoMode()
        cameraInterface.startingVideo()
        preview.takePicturePressed(false, false)
    }

    fun stopRecordingVideo() {
        cameraInterface.stoppingVideo()
        preview.takePicturePressed(false, false)
    }

    fun takeRecordingPhoto() {
        preview.takePicturePressed(true, false)
    }

    fun takeStillPhoto() {
        preview.takePicturePressed(false, false)
    }

    fun onResume() {
        preview.onResume()
        setLocationOnOff(true)
    }

    fun onPause() {
        preview.onPause()
        setLocationOnOff(false)
    }

    fun onDestroy() {
        preview.onDestroy()
        cameraInterface.onDestroy()
    }

    fun getActivity() = fragment.activity

    fun getRotation() = fragment.activity?.windowManager?.defaultDisplay?.rotation;

    fun setVideoMode() {
        preview.switchVideo(true, true)
    }

    fun cameraFlashOff() {
        preview.updateFlash(CAMERA_FLASH_OFF)
    }
    fun cameraFlashOn() {
        preview.updateFlash(CAMERA_FLASH_ON)
    }
    fun cameraFlashAuto() {
        preview.updateFlash(CAMERA_FLASH_AUTO)
    }

    // 取得目前影片完整路徑及檔案名稱
    fun setVideoFile(videoDir: File, fileName: String): File {
        if(!videoDir.exists()) {
            videoDir.mkdir()
        }

        return File(videoDir, fileName).apply {
            videoFile = this
        }
    }

    fun setImageFile(imageFileDir: File, fileName: String): File {
        if(!imageFileDir.exists()) {
            imageFileDir.mkdir()
        }

        return File(imageFileDir, fileName).apply {
            imageFile = this
        }
    }

    fun setTakePhotoListener(listener: TakePhotoListener?): CameraVideoHelper {
        cameraInterface.setTakePhotoListener(listener)
        return this
    }

    fun setCameraVideoErrorListener(listener: CameraVideoErrorListener?): CameraVideoHelper {
        cameraInterface.setCameraVideoErrorListener(listener)
        cameraVideoErrorListener = listener
        return this
    }

    // 取得目前影片完整路徑及檔案名稱
    fun getVideoFile(): File {
        return videoFile
    }

    fun saveVideo(videoFileName: String) {
        val mediaMetadataRetriever = MediaMetadataRetriever()
        mediaMetadataRetriever.setDataSource(videoFileName)
    }

    fun saveImage(bytes: ByteArray): Boolean {
        var isSaveSuccess: Boolean
        var output: FileOutputStream? = null
        try {
            output = FileOutputStream(imageFile)
            output.write(bytes)

            isSaveSuccess = true
        } catch (e: IOException) {
            isSaveSuccess = false
            e.printStackTrace()
            cameraVideoErrorListener?.errOccurListener("catch IOException @ saveImage @ $TAG: ${e.message}")
        } finally {
            if (null != output) {
                try {
                    output.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                    cameraVideoErrorListener?.errOccurListener("finally @ saveImage @ $TAG: ${e.message}")
                }
            }
        }

        return isSaveSuccess
    }

    private fun setLocationOnOff(isEnable: Boolean) {
        getActivity()?.let {
            val sharedPreferences = it.getSharedPreferences("default_name", Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putBoolean(PreferenceKeys.LocationPreferenceKey, isEnable)
            editor.apply()
            cameraInterface.locationSupplier.setupLocationListener()
        }
    }

    private fun correctRotate(filePath: String): Matrix {
        val matrix = Matrix()
        var exif: ExifInterface? = null
        try {
            exif = ExifInterface(filePath)
            when (exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL)) {

                ExifInterface.ORIENTATION_NORMAL -> {
                    matrix.postRotate(00f)
                }

                ExifInterface.ORIENTATION_ROTATE_90 -> {
                    matrix.postRotate(90f)
                }

                ExifInterface.ORIENTATION_ROTATE_180 -> {
                    run { matrix.postRotate(180f) }
                    run { matrix.postRotate(270f) }
                }

                ExifInterface.ORIENTATION_ROTATE_270 -> {
                    matrix.postRotate(270f)
                }

                else -> {
                    matrix.postRotate(00f)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            cameraVideoErrorListener?.errOccurListener("catch IOException @ correctRotate @ $TAG: ${e.message}")
        }
        return matrix
    }

    fun flipPic(file: File) {
        try { // 原檔太大，故先縮小尺寸
            val bitmap: Bitmap = resizeBitmap(file.absolutePath)
            val cx = bitmap.width / 2f
            val cy = bitmap.height / 2f
            val matrix = correctRotate(file.absolutePath)
            matrix.postScale(-1f, 1f, cx, cy)
            val flipBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            val baos = ByteArrayOutputStream()
            flipBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
            val bitmapData = baos.toByteArray()
            val fos = FileOutputStream(file)
            fos.write(bitmapData)
            fos.flush()
            fos.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            cameraVideoErrorListener?.errOccurListener("catch FileNotFoundException @ flipPic @ $TAG: ${e.message}")
        } catch (e: IOException) {
            e.printStackTrace()
            cameraVideoErrorListener?.errOccurListener("catch IOException @ flipPic @ $TAG: ${e.message}")
        }
    }

    private fun resizeBitmap(filePath: String): Bitmap {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true // 為了解析這個Bitmap的寬高，但並不生成bitmap物件，故回傳是會個null
        BitmapFactory.decodeFile(filePath, options)
        options.inJustDecodeBounds = false
        val width = options.outWidth
        val height = options.outHeight
        val maxWidth = MAX_IMAGE_LENGTH
        val maxHeight = MAX_IMAGE_LENGTH
        var scale = 1f // 不縮放
        if (width > height && width > maxWidth) {
            scale = width.toFloat() / maxWidth
        } else if (height > width && height > maxHeight) {
            scale = height.toFloat() / maxHeight
        }
        if (scale <= 0) { // scale若小於0的話，後續的動作將會使圖變成放大
            scale = 1f
        }
        options.inSampleSize = scale.toInt() // 设置缩放比例
        return BitmapFactory.decodeFile(filePath, options)
    }

    enum class CameraType { BACK, FRONT }

    enum class SignHand { RIGHT, LEFT }

}