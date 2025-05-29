package com.example.aimcue

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.view.WindowManager
import android.graphics.PixelFormat
import android.view.View
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.aimcue.AdvancedPoolOverlay

class MainActivity: FlutterActivity() {
    private val CHANNEL = "pool_overlay/system"
    private var overlayView: View? = null
    private var isMonitoring = false
    private val handler = Handler(Looper.getMainLooper())
    
    companion object {
        private const val TAG = "PoolOverlay_Main"
    }
    
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        Log.d(TAG, "configureFlutterEngine: Starting Flutter engine configuration")
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            Log.d(TAG, "MethodChannel called: ${call.method}")
            when (call.method) {
                "hasOverlayPermission" -> {
                    val hasPermission = hasOverlayPermission()
                    Log.d(TAG, "hasOverlayPermission: $hasPermission")
                    result.success(hasPermission)
                }
                "requestOverlayPermission" -> { 
                    Log.d(TAG, "requestOverlayPermission: Requesting overlay permission")
                    requestOverlayPermission()
                    result.success(null) 
                }
                "showOverlay" -> { 
                    Log.d(TAG, "showOverlay: Starting overlay monitoring")
                    startMonitoring()
                    result.success(null) 
                }
                "hideOverlay" -> { 
                    Log.d(TAG, "hideOverlay: Stopping overlay monitoring")
                    stopMonitoring()
                    result.success(null) 
                }
                "getForegroundApp" -> {
                    val currentApp = getCurrentApp()
                    Log.d(TAG, "getForegroundApp: Returning '$currentApp'")
                    result.success(currentApp)
                }
                "toggleResizeMode" -> {
                    Log.d(TAG, "toggleResizeMode: Toggling resize mode")
                    (overlayView as? AdvancedPoolOverlay)?.toggleResizeModeFromFlutter()
                    result.success(null)
                }
                "toggleTableLock" -> {
                    Log.d(TAG, "toggleTableLock: Toggling table lock")
                    (overlayView as? AdvancedPoolOverlay)?.toggleTableLockFromFlutter()
                    result.success(null)
                }
                "getOverlayStatus" -> {
                    val detector = overlayView as? AdvancedPoolOverlay
                    val status = mapOf(
                        "isActive" to (overlayView != null),
                        "isResizeMode" to (detector?.isInResizeMode() ?: false),
                        "isTableLocked" to (detector?.isTableLocked() ?: true),
                        "tableRect" to (detector?.getTableRect() ?: mapOf<String, Float>()),
                        "shotPower" to (detector?.getShotPower() ?: 0.5f),
                        "spinX" to (detector?.getSpinX() ?: 0f),
                        "spinY" to (detector?.getSpinY() ?: 0f)
                    )
                    Log.d(TAG, "getOverlayStatus: $status")
                    result.success(status)
                }
                "setShotPower" -> {
                    val power = call.argument<Double>("power")?.toFloat() ?: 0.5f
                    (overlayView as? AdvancedPoolOverlay)?.setShotPower(power)
                    Log.d(TAG, "setShotPower: $power")
                    result.success(null)
                }
                "setSpin" -> {
                    val spinX = call.argument<Double>("spinX")?.toFloat() ?: 0f
                    val spinY = call.argument<Double>("spinY")?.toFloat() ?: 0f
                    (overlayView as? AdvancedPoolOverlay)?.setSpin(spinX, spinY)
                    Log.d(TAG, "setSpin: X=$spinX, Y=$spinY")
                    result.success(null)
                }
                "toggleCushionShots" -> {
                    (overlayView as? AdvancedPoolOverlay)?.toggleCushionShots()
                    result.success(null)
                }
                "togglePowerIndicator" -> {
                    (overlayView as? AdvancedPoolOverlay)?.togglePowerIndicator()
                    result.success(null)
                }
                else -> {
                    Log.w(TAG, "Unknown method called: ${call.method}")
                    result.notImplemented()
                }
            }
        }
        Log.d(TAG, "configureFlutterEngine: Flutter engine configuration completed")
    }
    
    private fun hasOverlayPermission(): Boolean {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
        
        Log.d(TAG, "hasOverlayPermission: SDK=${Build.VERSION.SDK_INT}, hasPermission=$hasPermission")
        return hasPermission
    }
    
    private fun requestOverlayPermission() {
        Log.d(TAG, "requestOverlayPermission: SDK version=${Build.VERSION.SDK_INT}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                Log.d(TAG, "requestOverlayPermission: Starting overlay permission activity for package=$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "requestOverlayPermission: Error starting permission activity", e)
            }
        } else {
            Log.d(TAG, "requestOverlayPermission: SDK < M, permission not required")
        }
    }
    
    private fun startMonitoring() {
        Log.d(TAG, "startMonitoring: Checking overlay permission")
        if (!hasOverlayPermission()) {
            Log.w(TAG, "startMonitoring: No overlay permission, cannot start monitoring")
            return
        }
        isMonitoring = true
        Log.d(TAG, "startMonitoring: Monitoring started, beginning app check")
        checkApp()
    }
    
    private fun stopMonitoring() {
        Log.d(TAG, "stopMonitoring: Stopping monitoring, isMonitoring was: $isMonitoring")
        isMonitoring = false
        hideOverlay()
    }
    
    private fun checkApp() {
        if (!isMonitoring) {
            Log.d(TAG, "checkApp: Monitoring stopped, exiting check loop")
            return
        }
        
        val currentApp = getCurrentApp()
        Log.d(TAG, "checkApp: Current app package = '$currentApp'")
        
        val poolKeywords = listOf("pool", "miniclip", "billiard", "8ball", "eightball", "snooker")
        val isPoolApp = poolKeywords.any { keyword ->
            currentApp.contains(keyword, ignoreCase = true)
        }
        
        Log.d(TAG, "checkApp: Is pool app = $isPoolApp")
        
        if (isPoolApp) {
            if (overlayView == null) {
                Log.i(TAG, "checkApp: Pool app detected, showing overlay")
                showOverlay()
            }
        } else {
            if (overlayView != null) {
                Log.i(TAG, "checkApp: Non-pool app detected, hiding overlay")
                hideOverlay()
            }
        }
        
        // Test overlay every 10 seconds regardless of app
        val currentTime = System.currentTimeMillis()
        if (currentTime % 10000 < 1000) {
            if (overlayView == null) {
                Log.i(TAG, "checkApp: TEST MODE - Forcing overlay display")
                showOverlay()
            }
        }
        
        handler.postDelayed({ checkApp() }, 1000)
    }
    
    private fun getCurrentApp(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                val time = System.currentTimeMillis()
                val appList = usageStatsManager.queryUsageStats(
                    android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                    time - 1000 * 60,
                    time
                )
                
                if (appList != null && appList.isNotEmpty()) {
                    val recentApp = appList.sortedByDescending { it.lastTimeUsed }.firstOrNull()
                    if (recentApp != null && recentApp.packageName != packageName) {
                        Log.d(TAG, "getCurrentApp: From UsageStats = '${recentApp.packageName}'")
                        return recentApp.packageName
                    }
                }
            }
            
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val tasks = am.getRunningTasks(1)
            val currentApp = if (tasks.isNotEmpty()) {
                tasks[0].topActivity?.packageName ?: ""
            } else ""
            
            Log.v(TAG, "getCurrentApp: Retrieved from ActivityManager = '$currentApp'")
            
            if (currentApp.isNotEmpty() && currentApp != packageName) {
                return currentApp
            }
            
            Log.w(TAG, "getCurrentApp: No current app detected, using fallback")
            return "com.miniclip.eightballpool"
            
        } catch (e: SecurityException) {
            Log.w(TAG, "getCurrentApp: SecurityException, using fallback", e)
            return "com.miniclip.eightballpool"
        } catch (e: Exception) { 
            Log.e(TAG, "getCurrentApp: Exception, using fallback", e)
            return "com.miniclip.eightballpool"
        }
    }
    
    private fun showOverlay() {
        if (overlayView != null) {
            Log.w(TAG, "showOverlay: Overlay already exists, skipping")
            return
        }
        
        try {
            Log.d(TAG, "showOverlay: Creating AdvancedPoolOverlay view")
            val advancedOverlay = AdvancedPoolOverlay(this)
            overlayView = advancedOverlay
            
            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else WindowManager.LayoutParams.TYPE_PHONE
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            
            params.gravity = android.view.Gravity.TOP or android.view.Gravity.START
            params.x = 0
            params.y = 0
            
            Log.d(TAG, "showOverlay: Window params - Type=$overlayType")
            
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            windowManager.addView(overlayView, params)
            
            advancedOverlay.setWindowManager(windowManager, params)
            
            Log.i(TAG, "showOverlay: Overlay successfully added to WindowManager")
            overlayView?.invalidate()
            
        } catch (e: SecurityException) {
            Log.e(TAG, "showOverlay: SecurityException - Check overlay permission", e)
            overlayView = null
        } catch (e: WindowManager.BadTokenException) {
            Log.e(TAG, "showOverlay: BadTokenException - Invalid window token", e)
            overlayView = null
        } catch (e: Exception) {
            Log.e(TAG, "showOverlay: Error creating overlay", e)
            overlayView = null
        }
    }
    
    private fun hideOverlay() {
        overlayView?.let { view ->
            try {
                Log.d(TAG, "hideOverlay: Removing overlay from WindowManager")
                val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                windowManager.removeView(view)
                overlayView = null
                Log.d(TAG, "hideOverlay: Overlay successfully removed")
            } catch (e: Exception) {
                Log.e(TAG, "hideOverlay: Error removing overlay", e)
                overlayView = null
            }
        } ?: Log.d(TAG, "hideOverlay: No overlay to remove")
    }
}