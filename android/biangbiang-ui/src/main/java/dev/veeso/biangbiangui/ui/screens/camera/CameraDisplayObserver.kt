package dev.veeso.biangbiangui.ui.screens.camera

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

@Composable
internal fun CameraDisplayObserver(onDisplayChange: (Boolean, Int) -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val latestOnDisplayChange = rememberUpdatedState(onDisplayChange)
    val displayManager = remember(context) {
        context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    }
    val refresh = remember(context, view, configuration, latestOnDisplayChange) {
        {
            val display = hostingDisplay(context)
            val rotation = CameraLayoutPolicy.landscapeSideRotation(
                display?.rotation ?: Surface.ROTATION_0,
                configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
            )
            latestOnDisplayChange.value(
                view.isAttachedToWindow && display?.displayId == Display.DEFAULT_DISPLAY,
                rotation,
            )
        }
    }

    LaunchedEffect(context, configuration) {
        refresh()
    }

    DisposableEffect(displayManager, view, context, refresh) {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = refresh()
            override fun onDisplayRemoved(displayId: Int) = refresh()
            override fun onDisplayChanged(displayId: Int) = refresh()
        }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = refresh()
            override fun onViewDetachedFromWindow(view: View) = refresh()
        }

        displayManager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        view.addOnAttachStateChangeListener(attachListener)
        refresh()

        onDispose {
            displayManager?.unregisterDisplayListener(listener)
            view.removeOnAttachStateChangeListener(attachListener)
        }
    }
}

@Suppress("DEPRECATION")
private fun hostingDisplay(context: Context): Display? = if (Build.VERSION.SDK_INT >= 30) {
    context.display
} else {
    (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
}
