package app.ct5.desky

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.tabs.TabLayout
import kotlin.toString
import app.ct5.desky.R

class MainActivity : AppCompatActivity() {
    data class App(
        val name: String,
        val packageName: String,
        val icon: Drawable?
    )

    fun App.launch() {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val flags = PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
        val activities: List<ResolveInfo> = packageManager.queryIntentActivities(intent, flags)

        val installedApps = activities.map { resolveInfo ->
            App(
                name = resolveInfo.loadLabel(packageManager).toString(),
                packageName = resolveInfo.activityInfo.packageName,
                icon = resolveInfo.loadIcon(packageManager)
            )
        }

        val pm = packageManager

        val dialerPackage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val telecom = getSystemService(TelecomManager::class.java)
            telecom?.defaultDialerPackage
        } else {
            Intent(Intent.ACTION_DIAL).resolveActivity(pm)?.packageName
        }

        val smsPackage = Telephony.Sms.getDefaultSmsPackage(this)

        // Default Browser
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
        val browserPackage = browserIntent.resolveActivity(pm)?.packageName

        val galleryIntent = Intent(Intent.ACTION_VIEW).apply {
            setType("image/*")
            addCategory(Intent.CATEGORY_DEFAULT)
        }

        val galleryPackage = galleryIntent.resolveActivity(packageManager)?.packageName

        val defaultDialerApp = installedApps.find { it.packageName == dialerPackage }
        val defaultSMSApp = installedApps.find { it.packageName == smsPackage }
        val defaultBrowserApp = installedApps.find { it.packageName == browserPackage }
        val defaultGalleryApp = installedApps.find { it.packageName == galleryPackage }

        val tabLayout = findViewById<TabLayout>(R.id.TaskBar)

        val tab = tabLayout.newTab()
        tab.icon = defaultDialerApp?.icon
        tabLayout.addTab(tab)

        // After adding the tab
        val tabView = (tabLayout.getChildAt(0) as ViewGroup).getChildAt(tab.position)
        tabView.tooltipText = defaultDialerApp.name
    }
}