package dev.veeso.biangbiangui.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dev.veeso.biangbiangui.ui.LocalBiangBiangContext
import dev.veeso.biangbiangui.ui.screens.camera.CameraLiveScreen
import dev.veeso.biangbiangui.ui.screens.camera.CameraPermissionScreen

/**
 * Top-level camera entry point. Config-driven port of the reference
 * BiangBiang Hanzi Android `CameraModeView.kt`; mirrors iOS `CameraScreen`.
 *
 * Routes to [CameraPermissionScreen] (strings from `config.strings`) when
 * access is denied, otherwise to `CameraLiveScreen`. The consuming app
 * declares the `CAMERA` permission in its own manifest.
 */
@Composable
fun CameraScreen() {
    val ctx = LocalBiangBiangContext.current
    val context = LocalContext.current
    var hasCameraPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        permissionRequested = true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !permissionRequested) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraLiveScreen(ctx)
    } else {
        CameraPermissionScreen(
            title = ctx.config.strings.cameraDisabledTitle,
            message = ctx.config.strings.cameraDisabledMessage,
            openSettingsLabel = ctx.config.strings.openSettings,
        )
    }
}
