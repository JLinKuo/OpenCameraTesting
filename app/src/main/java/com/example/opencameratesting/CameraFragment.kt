package com.example.opencameratesting

import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.bumptech.glide.Glide
import com.example.opencameratesting.opencamera.CameraVideoHelper
import java.io.File

internal const val TEMP_SAVE_FILES_PATH = "temp/"

/**
 * A simple [Fragment] subclass.
 * Use the [CameraFragment.newInstance] factory method to
 * create an instance of this fragment.
 */

private const val PHONE_ORIENTATION_LANDSCAPE = "landscape"
private const val PHONE_ORIENTATION_PORTRAIT = "portrait"

class CameraFragment : Fragment() {

    private val isVideoMode by lazy { false }              // 表示在此頁面中的用法是錄影模式還是拍照模式

    private lateinit var texture: FrameLayout
    private lateinit var zoomSeekbar: SeekBar
    private lateinit var showImage: ImageView

    private val cameraVideoHelper by lazy {
        activity?.let {
            CameraVideoHelper(this, texture, isVideoMode, PHONE_ORIENTATION_LANDSCAPE)
        }
    }

    private lateinit var takePhoto: Button
    private val fileDir by lazy {
        activity?.let {
            File(it.getExternalFilesDir(null), "$TEMP_SAVE_FILES_PATH")
        }
    }
    private lateinit var imageFile: File

    private var isRecording = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_camera, container, false)

        showImage = view.findViewById(R.id.showImage)
        texture = view.findViewById(R.id.texture)
        zoomSeekbar = view.findViewById(R.id.zoom_seekbar)
        takePhoto = view.findViewById(R.id.take_photo)

        cameraVideoHelper?.setTakePhotoListener {
            activity?.let {
                Toast.makeText(it, "${imageFile.absolutePath}", Toast.LENGTH_SHORT).show()
                Glide.with(it).load(imageFile).into(showImage)
            }
        }

        takePhoto.setOnClickListener {
            fileDir?.let { fileDir ->
                cameraVideoHelper?.let { cameraVideoHelper ->
                    imageFile = cameraVideoHelper.setImageFile(fileDir, "${System.currentTimeMillis()}.jpg")
                    cameraVideoHelper.takeStillPhoto()
                }
            }
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        cameraVideoHelper?.preview?.onResume()
    }

    override fun onPause() {
        cameraVideoHelper?.preview?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        cameraVideoHelper?.preview?.onDestroy()
        super.onDestroy()
    }

    fun cameraSetup() {
        cameraVideoHelper?.let {
            zoomSeekbar.apply {
                setOnSeekBarChangeListener(null)
                max = it.preview.maxZoom
                progress = it.preview.maxZoom - it.preview.cameraController.zoom
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
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
        cameraVideoHelper?.let {
            zoomSeekbar.progress = it.preview.maxZoom - new_zoom
        }
    }
}