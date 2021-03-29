package com.example.opencameratesting.opencamera

import android.view.ViewGroup
import com.example.opencameratesting.CameraInterface
import com.example.opencameratesting.MainActivity
import com.example.opencameratesting.opencamera.Preview.Preview

class CameraVideoHelper(mainActivity: MainActivity, texture: ViewGroup) {
    val cameraInterface by lazy { CameraInterface(mainActivity) }
    val preview by lazy { Preview(cameraInterface, texture) }

}