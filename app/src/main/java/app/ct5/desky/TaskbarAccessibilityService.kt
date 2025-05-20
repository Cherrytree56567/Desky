package app.ct5.desky

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.tabs.TabLayout
import kotlin.collections.mutableListOf

class TaskbarAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var taskbarView: View
    private lateinit var tabLayout: TabLayout
    private lateinit var sharedPref: android.content.SharedPreferences
    private lateinit var installedApps: List<App>
    private lateinit var params: WindowManager.LayoutParams
    private var isTaskbarVisible = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val isLocked = keyguardManager.isKeyguardLocked || keyguardManager.isDeviceLocked

            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> removeTaskbar()
                Intent.ACTION_USER_PRESENT -> {
                    if (!isLocked) {
                        showTaskbar()
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onServiceConnected() {
        super.onServiceConnected()

        val themedContext = ContextThemeWrapper(this, R.style.Theme_Desky)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val inflater = LayoutInflater.from(themedContext)
        taskbarView = inflater.inflate(R.layout.taskbar, null)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        tabLayout = taskbarView.findViewById(R.id.TaskBar)
        sharedPref = getSharedPreferences("app.ct5.deskyPrefs", Context.MODE_PRIVATE)

        val flags = PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
        val launchIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val activities = packageManager.queryIntentActivities(launchIntent, flags)

        installedApps = activities.map { resolveInfo ->
            App(
                name = resolveInfo.loadLabel(packageManager).toString(),
                packageName = resolveInfo.activityInfo.packageName,
                icon = resolveInfo.loadIcon(packageManager)
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(taskbarView.findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        addTaskbarApps()

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val app = installedApps.find { it.packageName == tab.tag }
                app?.launch(this@TaskbarAccessibilityService)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)

        showTaskbar()
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(taskbarView)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val currentWindows = mutableListOf<AccessibilityWindowInfo>()
        for (window in windows) {
            if (window.root?.packageName == "app.ct5.desky") {

            } else if (window.root?.packageName == "com.android.systemui") {

            } else if (window.root?.packageName == "null") {

            } else {
                val bounds = Rect()
                window.getBoundsInScreen(bounds)
                Log.d("WindowInfo", "Package: ${window.root?.packageName}")
                currentWindows.add(window)
            }
        }
        updateTaskbarApps(currentWindows)
        val currentWindow = windows?.firstOrNull { it.isFocused }
        val currentPackage = currentWindow?.root?.packageName?.toString()

        if (currentPackage != null) {
            Log.d("PackageName", currentPackage)
            selectTabForPackage(currentPackage)
        }
    }
    override fun onInterrupt() {}

    private fun selectTabForPackage(packageName: String) {
        for (i in 0 until tabLayout.tabCount) {
            val tab = tabLayout.getTabAt(i)
            if (tab?.tag == packageName) {
                tab.select()
                return
            }
        }
    }

    private fun showTaskbar() {
        if (!isTaskbarVisible && !taskbarView.isAttachedToWindow) {
            windowManager.addView(taskbarView, params)
            isTaskbarVisible = true
        }
    }

    private fun removeTaskbar() {
        if (isTaskbarVisible && taskbarView.isAttachedToWindow) {
            windowManager.removeView(taskbarView)
            isTaskbarVisible = false
        }
    }

    fun initTaskbar() {
        val pm = packageManager

        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
        val browserResolveInfo = packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val browserPackage = browserResolveInfo?.activityInfo?.packageName

        val playStorePackage = "com.android.vending"

        val settingsIntent = Intent(Settings.ACTION_SETTINGS)
        val settingsResolveInfo = packageManager.resolveActivity(settingsIntent, 0)
        val settingsPackage = settingsResolveInfo?.activityInfo?.packageName

        val stringSet = setOfNotNull(browserPackage, playStorePackage, settingsPackage)
        sharedPref.edit().remove("TaskbarApps").commit()
        sharedPref.edit().putStringSet("TaskbarApps", stringSet).commit()
    }

    fun addTaskbarApps() {
        val savedSet = sharedPref.getStringSet("TaskbarApps", emptySet()) ?: emptySet()
        if (savedSet.isEmpty()) {
            initTaskbar()
        }

        for (app in savedSet) {
            Log.d("MainActivity", app)
            val packageApp = installedApps.find { it.packageName == app }
            val tab = tabLayout.newTab()

            packageApp?.icon?.let { icon ->
                val wrappedIcon = DrawableCompat.wrap(icon).mutate()
                DrawableCompat.setTint(wrappedIcon, android.R.attr.colorPrimary)
                tab.setIcon(wrappedIcon)
            }
            tab.contentDescription = packageApp?.name
            tab.tag = packageApp?.packageName

            tabLayout.addTab(tab)
        }
    }

    @SuppressLint("ResourceAsColor")
    fun updateTaskbarApps(windows : List<AccessibilityWindowInfo>) {
        val savedSet = sharedPref.getStringSet("TaskbarApps", emptySet()) ?: emptySet()
        if (savedSet.isEmpty()) {
            initTaskbar()
        }

        tabLayout.clearOnTabSelectedListeners()

        var taskbarPackages = mutableListOf<String>()
        var newTaskbarPackages = mutableListOf<String>()
        for (i in 0 until tabLayout.tabCount) {
            taskbarPackages.add(tabLayout.getTabAt(i)?.tag.toString())
        }

        for (app in savedSet) {
            newTaskbarPackages.add(app)
        }

        for (window in windows) {
            if (!savedSet.contains(window.root?.packageName.toString())) {
                newTaskbarPackages.add(window.root?.packageName.toString())
            }
        }

        val removedPackages = taskbarPackages.subtract(newTaskbarPackages.toSet())
        for (i in tabLayout.tabCount - 1 downTo 0) {
            val tab = tabLayout.getTabAt(i)
            val tabPackage = tab?.tag as? String
            if (tabPackage != null && removedPackages.contains(tabPackage)) {
                tabLayout.removeTabAt(i)
            }
        }

        val addedPackages = newTaskbarPackages.filter { it !in taskbarPackages }
        for (pkg in addedPackages) {
            val app = installedApps.find { it.packageName == pkg }
            if (app != null) {
                val tab = tabLayout.newTab()

                app.icon?.let { icon ->
                    val wrappedIcon = DrawableCompat.wrap(icon).mutate()
                    DrawableCompat.setTint(wrappedIcon, android.R.attr.colorPrimary)
                    tab.setIcon(wrappedIcon)
                }

                tab.contentDescription = app.name
                tab.tag = app.packageName

                tabLayout.addTab(tab)
            }
        }

        val currentWindow = windows?.firstOrNull { it.isFocused }
        val currentPackage = currentWindow?.root?.packageName?.toString()

        if (currentPackage != null) {
            for (i in 0 until tabLayout.tabCount) {
                val tab = tabLayout.getTabAt(i)
                if (tab?.tag == currentPackage) {
                    // 3. Select this tab
                    tab.select()
                    break
                }
            }
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val app = installedApps.find { it.packageName == tab.tag }
                app?.launch(this@TaskbarAccessibilityService)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }
}
