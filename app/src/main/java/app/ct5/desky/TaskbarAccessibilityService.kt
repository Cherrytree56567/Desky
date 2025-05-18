package app.ct5.desky

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onServiceConnected() {
        super.onServiceConnected()

        val themedContext = ContextThemeWrapper(this, R.style.Theme_Desky)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val inflater = LayoutInflater.from(themedContext)
        taskbarView = inflater.inflate(R.layout.taskbar, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM

        windowManager.addView(taskbarView, params)

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
    }
    override fun onInterrupt() {}

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
                DrawableCompat.setTint(wrappedIcon, android.R.attr.colorPrimary)  // Replace Color.RED with your desired color
                tab.setIcon(wrappedIcon)
            }
            tab.contentDescription = packageApp?.name // shows tooltip on hover or long press
            tab.tag = packageApp?.packageName

            tabLayout.addTab(tab)
        }
    }

    fun updateTaskbarApps(windows : List<AccessibilityWindowInfo>) {
        val savedSet = sharedPref.getStringSet("TaskbarApps", emptySet()) ?: emptySet()
        if (savedSet.isEmpty()) {
            initTaskbar()
        }

        tabLayout.clearOnTabSelectedListeners()
        //tabLayout.removeAllTabs()

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
