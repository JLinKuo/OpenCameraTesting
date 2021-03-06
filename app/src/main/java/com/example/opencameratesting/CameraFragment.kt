package com.example.opencameratesting

import android.content.Context
import android.content.IntentSender.SendIntentException
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.opencameratesting.opencamera.CameraVideoHelper
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import java.io.File

internal const val TEMP_SAVE_FILES_PATH = "temp/"

/**
 * A simple [Fragment] subclass.
 * Use the [CameraFragment.newInstance] factory method to
 * create an instance of this fragment.
 */

private const val PHONE_ORIENTATION_LANDSCAPE = "landscape"
private const val PHONE_ORIENTATION_PORTRAIT = "portrait"
private const val REQUEST_CHECK_LOCATION_SETTINGS = 199

class CameraFragment : Fragment(), MainActivity.PermissionListener {

    private val TAG = javaClass.simpleName

    private val isVideoMode by lazy { false }              // 表示在此頁面中的用法是錄影模式還是拍照模式

    private lateinit var mask: View
    private lateinit var texture: FrameLayout
    private lateinit var zoomSeekbar: SeekBar
    private lateinit var showImage: ImageView

    private val cameraVideoHelper by lazy {
        CameraVideoHelper(this, texture, isVideoMode, PHONE_ORIENTATION_LANDSCAPE)
                .setTakePhotoListener {
                    activity.let {
                        showScreen()
                        Toast.makeText(it, "${imageFile.absolutePath}", Toast.LENGTH_SHORT).show()
                        Glide.with(it).load(imageFile).into(showImage)
                    }
                }
                .setCameraVideoErrorListener {
                    Log.d(TAG, "Camera Video Error: $it")
                }
    }

    private lateinit var flash: ImageView
    private lateinit var switchCamera: ImageView
    private lateinit var takePhoto: Button
    private lateinit var recordVideo: Button
    private lateinit var recordVideoTakePhoto: Button
    private val fileDir by lazy {
        File(activity.getExternalFilesDir(null), "$TEMP_SAVE_FILES_PATH")
    }
    private lateinit var imageFile: File
    private lateinit var videoFile: File

    private var isRecording = false

    private lateinit var activity: MainActivity

    private var flashStatus = 0

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if(context is MainActivity) {
            activity = context
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_camera, container, false)

        activity.chkPermission(this)
        setView(view)
        setListener()
        setGPS()

        return view
    }

    private fun setGPS() {
        val locationRequest = LocationRequest.create().apply {
            this.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            this.interval = 10 * 1000
            this.fastestInterval = 10 * 1000 / 2
        }

        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        builder.setAlwaysShow(true)

        val result = LocationServices.getSettingsClient(activity).checkLocationSettings(builder.build())
        result.addOnCompleteListener{
            try {
                val response = it.getResult(ApiException::class.java)
                // All location settings are satisfied. The client can initialize location
                // requests here.

            } catch (ex: ApiException) {
                when(ex.statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                        // Location settings are not satisfied. But could be fixed by showing the
                        // user a dialog.
                        try {
                            // Cast to a resolvable exception.
                            val resolvable = ex as ResolvableApiException
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            resolvable.startResolutionForResult(
                                activity, REQUEST_CHECK_LOCATION_SETTINGS
                            )
                        } catch (ex: SendIntentException) {
                            // Ignore the error.
                        } catch (ex: ClassCastException) {
                            // Ignore the error.
                        }
                    }
                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                        // Location settings are not satisfied. However, we have no way to fix the
                        // settings so we won't show the dialog.
                    }
                }
            }
        }
    }

    private fun setView(view: View?) {
        view?.let {
            flash = view.findViewById(R.id.flash)
            switchCamera = view.findViewById(R.id.switch_camera)
            showImage = view.findViewById(R.id.showImage)
            mask = view.findViewById(R.id.mask)
            texture = view.findViewById(R.id.texture)
            zoomSeekbar = view.findViewById(R.id.zoom_seekbar)
            takePhoto = view.findViewById(R.id.take_photo)
            recordVideo = view.findViewById(R.id.record_video)
            recordVideoTakePhoto = view.findViewById(R.id.record_video_take_photo)
        }
    }

    private fun setListener() {
        flash.setOnClickListener {
            when(flashStatus % 3) {
                // 透過字串flash_on、flash_auto、flash_off變化閃光燈狀態
                0 -> {
                    flash.setImageResource(R.drawable.ic_flash_on)
                    flashStatus++
                    cameraVideoHelper.cameraFlashOn()
                }
                1 -> {
                    flash.setImageResource(R.drawable.ic_flash_auto)
                    flashStatus++
                    cameraVideoHelper.cameraFlashAuto()
                }
                2 -> {
                    flash.setImageResource(R.drawable.ic_flash_off)
                    flashStatus++
                    cameraVideoHelper.cameraFlashOff()
                }
                else -> throw Exception("flashStatus error.")
            }
        }

        switchCamera.setOnClickListener {
            cameraVideoHelper.switchCamera()
        }

        takePhoto.setOnClickListener {
            fileDir.let { fileDir ->
                cameraVideoHelper.let { cameraVideoHelper ->
                    imageFile = cameraVideoHelper.setImageFile(
                        fileDir,
                        "${System.currentTimeMillis()}.jpg"
                    )
                    cameraVideoHelper.takeStillPhoto()
                    maskScreen()
                }
            }
        }

        recordVideo.setOnClickListener {
            if(isRecording) {
                takePhoto.visibility = VISIBLE
                recordVideoTakePhoto.visibility = GONE
                cameraVideoHelper.stopRecordingVideo()
                isRecording = false
            } else {
                fileDir.let { fileDir ->
                    cameraVideoHelper.let { cameraVideoHelper ->
                        videoFile = cameraVideoHelper.setVideoFile(
                            fileDir,
                            "${System.currentTimeMillis()}.mp4"
                        )
                        cameraVideoHelper.startRecordingVideo()
                        takePhoto.visibility = GONE
                        recordVideoTakePhoto.visibility = VISIBLE
                        isRecording = true
                    }
                }
            }
        }

        recordVideoTakePhoto.setOnClickListener {
            fileDir.let { fileDir ->
                cameraVideoHelper.let { cameraVideoHelper ->
                    imageFile = cameraVideoHelper.setImageFile(
                        fileDir,
                        "${System.currentTimeMillis()}.jpg"
                    )
                    cameraVideoHelper.takeRecordingPhoto()
                }
            }
        }

        cameraVideoHelper.setTakePhotoListener {
            activity.let {
                showScreen()
                Toast.makeText(it, "${imageFile.absolutePath}", Toast.LENGTH_SHORT).show()
                Glide.with(it).load(imageFile).into(showImage)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        initCameraState()
        cameraVideoHelper.onResume()
    }

    override fun onPause() {
        cameraVideoHelper.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        cameraVideoHelper.onDestroy()
        super.onDestroy()
    }

    fun cameraSetup() {
        cameraVideoHelper.let {
            zoomSeekbar.apply {
                setOnSeekBarChangeListener(null)
                max = it.preview.maxZoom
                progress = it.preview.maxZoom - it.preview.cameraController.zoom
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        // note we zoom even if !fromUser, as various other UI controls (multitouch, volume key zoom, -/+ zoomcontrol)
                        // indirectly set zoom via this method, from setting the zoom slider
                        it.preview.zoomTo(it.preview.maxZoom - progress)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {}

                    override fun onStopTrackingTouch(seekBar: SeekBar) {}
                })
            }
        }
    }

    fun setSeekbarZoom(new_zoom: Int) {
        cameraVideoHelper.let {
            zoomSeekbar.progress = it.preview.maxZoom - new_zoom
        }
    }

    fun maskScreen() {
        mask.visibility = VISIBLE
    }

    fun showScreen() {
        mask.visibility = GONE
    }

    private fun initCameraState() {
        cameraVideoHelper.cameraFlashOff()
        flash.setImageResource(R.drawable.ic_flash_off)
    }

    // MainActivity.PermissionListener
    override fun allGrantListener() {
        object: CountDownTimer(500, 500) {
            override fun onFinish() { showScreen() }
            override fun onTick(millisUntilFinished: Long) {}
        }.start()
    }
}