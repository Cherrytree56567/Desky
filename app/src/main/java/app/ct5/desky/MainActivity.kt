package app.ct5.desky

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import android.provider.Settings
import android.text.TextUtils
import android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER
import androidx.annotation.RequiresPermission
import androidx.constraintlayout.widget.ConstraintLayout

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        var mainContraint = findViewById<ConstraintLayout>(R.id.main)

        // TODO: Ask for Accessibility Settings
        val hasPermission = isAccessibilityServiceEnabled(this, TaskbarAccessibilityService::class.java)
        if (!hasPermission) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        window.addFlags(
            FLAG_SHOW_WALLPAPER
        )
    }

    fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
        val expectedComponentName = ComponentName(context, serviceClass)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        for (service in colonSplitter) {
            if (service.equals(expectedComponentName.flattenToString(), ignoreCase = true)) {
                return true
            }
        }

        return false
    }
}
