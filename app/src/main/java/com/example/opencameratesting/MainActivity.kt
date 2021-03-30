package com.example.opencameratesting

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.permissionx.guolindev.PermissionX

private const val PHONE_ORIENTATION_LANDSCAPE = "landscape"
private const val PHONE_ORIENTATION_PORTRAIT = "portrait"

class MainActivity : AppCompatActivity() {

    private val listPermissions by lazy {
        ArrayList<String>().apply {
            this.add("android.permission.CAMERA")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chkPermission()
        replaceFragment()
    }

    private fun replaceFragment() {
        supportFragmentManager.beginTransaction().apply {
            this.replace(R.id.camera_fragment, CameraFragment())
            this.commit()
        }
    }

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
}