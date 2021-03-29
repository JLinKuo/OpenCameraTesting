package com.example.opencameratesting

import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.SeekBar
import com.example.opencameratesting.opencamera.CameraVideoHelper
import com.example.opencameratesting.opencamera.Preview.Preview
import com.permissionx.guolindev.PermissionX
import java.io.FileOutputStream
import java.io.IOException

private const val PHONE_ORIENTATION_LANDSCAPE = "landscape"
private const val PHONE_ORIENTATION_PORTRAIT = "portrait"

class MainActivity : AppCompatActivity() {

    private val listPermissions by lazy {
        ArrayList<String>().apply {
            this.add("android.permission.CAMERA")
        }
    }

    private val isVideoMode by lazy { false }              // 表示在此頁面中的用法是錄影模式還是拍照模式

    private val texture: FrameLayout by lazy { findViewById(R.id.texture) }
    private val zoomSeekbar: SeekBar by lazy { findViewById(R.id.zoom_seekbar) }

    private val cameraVideoHelper by lazy { CameraVideoHelper(this, texture) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chkPermission()
    }

    override fun onResume() {
        super.onResume()
        cameraVideoHelper.preview.onResume()
    }

    override fun onPause() {
        cameraVideoHelper.preview.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        cameraVideoHelper.preview.onDestroy()
        super.onDestroy()
    }

    fun getPreview() = cameraVideoHelper.preview

    private fun chkPermission() {
        PermissionX.init(this)
            .permissions(listPermissions)
            .onExplainRequestReason { scope, deniedList ->
                scope.showRequestReasonDialog(
                    deniedList,
                    "aaaaaaaaaaaaaaaaa",
                    "OK",
                    "Cancel"
                )
            }
            .onForwardToSettings { scope, deniedList ->
                scope.showForwardToSettingsDialog(
                    deniedList,
                    "please",
                    "Go to settings",
                    "Cancel"
                )
            }
            .request { allGranted, grantedList, deniedList ->
                if (!allGranted) {

                }
            }
    }

    fun getApplicationInterface() = cameraVideoHelper.cameraInterface

    fun cameraSetup() {
        zoomSeekbar.apply {
            setOnSeekBarChangeListener(null)
            max = cameraVideoHelper.preview.maxZoom
            progress = cameraVideoHelper.preview.maxZoom - cameraVideoHelper.preview.cameraController.zoom
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    // note we zoom even if !fromUser, as various other UI controls (multitouch, volume key zoom, -/+ zoomcontrol)
                    // indirectly set zoom via this method, from setting the zoom slider
                    cameraVideoHelper.preview.zoomTo(cameraVideoHelper.preview.maxZoom - progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}

                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
    }

    fun setSeekbarZoom(new_zoom: Int) {
        zoomSeekbar.progress = cameraVideoHelper.preview.maxZoom - new_zoom
    }

//    fun saveImage(image: ByteArray): Boolean {
//        if (isLoginTakePic) {
//            showLoginView(image)
//            (activity as CameraActivity).dismissProgressDialog()
//        } else {
//            val context = ContextSingleton.getInstance()!!.getContext()
//            val file = File(context.getExternalFilesDir(null), "${TYPE.PHOTO.value}${System.currentTimeMillis()}.jpg")
//            var output: FileOutputStream? = null
//            try {
//                output = FileOutputStream(file).apply {
//                    write(image)
//                }
//            } catch (e: IOException) {
//                FirebaseCrashlytics.getInstance().recordException(
//                    Throwable(
//                        "file write IOException @saveImage @$TAG: ${e.message}"
//                    )
//                )
//                Log.e(TAG, e.toString())
//            } finally {
//                output?.let {
//                    try {
//                        it.close()
//                    } catch (e: IOException) {
//                        FirebaseCrashlytics.getInstance().recordException(
//                            Throwable(
//                                "file outputstream close IOException @saveImage @$TAG: ${e.message}"
//                            )
//                        )
//                        Log.e(TAG, e.toString())
//                        return false
//                    }
//                }
//            }
//
//            if (file.exists()) {
//                val fileName = File(file.absolutePath).name
//                val timestamp = fileName.substring(1, fileName.indexOf(".")).toLong()
//
//                val exifInterface = ExifInterface(file.absolutePath)
//                exifInterface.apply {
//                    setAttribute(ExifInterface.TAG_DATETIME, Tools.getDate(timestamp))
//                    saveAttributes()
//                }
//
//                val fileSize = File(file.absolutePath).length()
//                val tools = Tools()
//                val crc = File(file.absolutePath).crc32()
//                val enHash = Base64.encodeToString(
//                    tools.doHashEncryption(File(file.absolutePath).sha256(), timestamp.toString(), crc),
//                    Base64.NO_WRAP
//                )
//
//                AsyncTask.execute {
//                    val photo = CamexData(
//                        filename = fileName,
//                        path = file.absolutePath,
//                        size = fileSize,
//                        type = TYPE.PHOTO,
//                        imei = camexRepository.imei,
//                        timestamp = timestamp,
//                        hash = enHash,
//                        date = Tools.getDate(timestamp)
//                    )
//                    camexDao.insert(photo)
//
//                    ImageTask().execute()
//                }
//
//                binding.texture.visibility = View.VISIBLE
//                binding.photo.isEnabled = true
//                isTakingPhoto = false
//            }
//        }
//
//        return true
//    }
}