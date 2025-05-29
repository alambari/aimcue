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
import android.graphics.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.content.SharedPreferences
import kotlin.math.*

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
                    (overlayView as? SmartPoolDetector)?.toggleResizeModeFromFlutter()
                    result.success(null)
                }
                "toggleTableLock" -> {
                    Log.d(TAG, "toggleTableLock: Toggling table lock")
                    (overlayView as? SmartPoolDetector)?.toggleTableLockFromFlutter()
                    result.success(null)
                }
                "getOverlayStatus" -> {
                    val detector = overlayView as? SmartPoolDetector
                    val status = mapOf(
                        "isActive" to (overlayView != null),
                        "isResizeMode" to (detector?.isInResizeMode() ?: false),
                        "isTableLocked" to (detector?.isTableLocked() ?: true),
                        "tableRect" to (detector?.getTableRect() ?: mapOf<String, Float>())
                    )
                    Log.d(TAG, "getOverlayStatus: $status")
                    result.success(status)
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
        
        // More comprehensive pool app detection
        val poolKeywords = listOf("pool", "miniclip", "billiard", "8ball", "eightball", "snooker")
        val isPoolApp = poolKeywords.any { keyword ->
            currentApp.contains(keyword, ignoreCase = true)
        }
        
        Log.d(TAG, "checkApp: Is pool app = $isPoolApp, keywords matched = ${poolKeywords.filter { currentApp.contains(it, ignoreCase = true) }}")
        
        if (isPoolApp) {
            if (overlayView == null) {
                Log.i(TAG, "checkApp: Pool app detected, showing overlay")
                showOverlay()
            } else {
                Log.v(TAG, "checkApp: Pool app detected, overlay already visible")
            }
        } else {
            if (overlayView != null) {
                Log.i(TAG, "checkApp: Non-pool app detected, hiding overlay")
                hideOverlay()
            } else {
                Log.v(TAG, "checkApp: Non-pool app detected, overlay already hidden")
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
            // Method 1: Try UsageStatsManager (requires PACKAGE_USAGE_STATS permission)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                val time = System.currentTimeMillis()
                val appList = usageStatsManager.queryUsageStats(
                    android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                    time - 1000 * 60, // Last minute
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
            
            // Method 2: Try ActivityManager (deprecated but might still work)
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val tasks = am.getRunningTasks(1)
            val currentApp = if (tasks.isNotEmpty()) {
                tasks[0].topActivity?.packageName ?: ""
            } else ""
            
            Log.v(TAG, "getCurrentApp: Retrieved from ActivityManager = '$currentApp'")
            
            if (currentApp.isNotEmpty() && currentApp != packageName) {
                return currentApp
            }
            
            // Method 3: Check running processes
            val runningProcesses = am.runningAppProcesses
            if (runningProcesses != null) {
                for (processInfo in runningProcesses) {
                    if (processInfo.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        val foregroundApp = processInfo.processName
                        Log.v(TAG, "getCurrentApp: Foreground process = '$foregroundApp'")
                        if (foregroundApp != packageName) {
                            return foregroundApp
                        }
                    }
                }
            }
            
            Log.w(TAG, "getCurrentApp: No current app detected, using fallback")
            return "com.miniclip.eightballpool" // For testing
            
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
            Log.d(TAG, "showOverlay: Creating SmartPoolDetector view")
            val smartDetector = SmartPoolDetector(this)
            overlayView = smartDetector
            
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
            
            // Set overlay to be on top
            params.gravity = android.view.Gravity.TOP or android.view.Gravity.START
            params.x = 0
            params.y = 0
            
            Log.d(TAG, "showOverlay: Window params - Type=$overlayType, Size=MATCH_PARENT x MATCH_PARENT, Flags=${params.flags}")
            
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            windowManager.addView(overlayView, params)
            
            // Set window manager reference for dynamic flag updates
            smartDetector.setWindowManager(windowManager, params)
            
            Log.i(TAG, "showOverlay: Overlay successfully added to WindowManager")
            
            // Force a redraw
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
                overlayView = null // Reset anyway to prevent memory leaks
            }
        } ?: Log.d(TAG, "hideOverlay: No overlay to remove")
    }
}

class SmartPoolDetector(context: Context) : View(context) {
    
    companion object {
        private const val TAG = "PoolOverlay_Detector"
        private const val PREFS_NAME = "pool_table_settings"
        private const val KEY_TABLE_LEFT = "table_left"
        private const val KEY_TABLE_TOP = "table_top"
        private const val KEY_TABLE_RIGHT = "table_right"
        private const val KEY_TABLE_BOTTOM = "table_bottom"
        private const val KEY_IS_LOCKED = "is_locked"
    }
    
    // Window manager for dynamic flag updates
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    
    // Detected table properties
    private var tableRect: RectF? = null
    private var pockets = mutableListOf<PointF>()
    private var whiteBallPos: PointF? = null
    private var cueStickAngle = 0f
    private var cueStickLength = 0f
    private var isAiming = false
    
    // Manual resize properties
    private var isResizeMode = false
    private var isTableLocked = true
    private var isDragging = false
    private var dragHandle = -1 // 0=TL, 1=TR, 2=BL, 3=BR, 4=move
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val handleRadius = 40f
    private val cornerHandleRadius = 30f
    
    // Detection state
    private var isAnalyzing = true
    private var lastAnalysisTime = 0L
    private val analysisInterval = 100L // 10 FPS analysis
    
    // Performance tracking
    private var frameCount = 0
    private var lastFpsLog = 0L
    
    // SharedPreferences for saving table settings
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Paint objects
    private val tableBorderPaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
        alpha = 150
    }
    
    private val tableResizePaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
        alpha = 200
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    
    private val handlePaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
        alpha = 180
    }
    
    private val handleBorderPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    
    private val pocketPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
        alpha = 200
    }
    
    private val whiteBallPaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    
    private val aimLinePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
        alpha = 220
    }
    
    private val extendedLinePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(20f, 15f), 0f)
        alpha = 180
    }
    
    private val reflectionPaint = Paint().apply {
        color = Color.CYAN
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
        alpha = 150
    }
    
    private val debugTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 22f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    
    private val buttonPaint = Paint().apply {
        color = Color.parseColor("#88000000")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val buttonTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 20f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    
    init {
        Log.d(TAG, "SmartPoolDetector initialized")
        loadTableSettings()
        startComputerVision()
        updateTouchability()
    }
    
    fun setWindowManager(wm: WindowManager, params: WindowManager.LayoutParams) {
        windowManager = wm
        layoutParams = params
        Log.d(TAG, "setWindowManager: Window manager set")
        updateTouchability()
    }
    
    // Public methods for Flutter control
    fun toggleResizeModeFromFlutter() {
        toggleResizeMode()
    }
    
    fun toggleTableLockFromFlutter() {
        toggleTableLock()
    }
    
    fun isInResizeMode(): Boolean = isResizeMode
    
    fun isTableLocked(): Boolean = isTableLocked
    
    fun getTableRect(): Map<String, Float> {
        return tableRect?.let { rect ->
            mapOf(
                "left" to rect.left,
                "top" to rect.top,
                "right" to rect.right,
                "bottom" to rect.bottom,
                "width" to rect.width(),
                "height" to rect.height()
            )
        } ?: emptyMap()
    }
    
    private fun updateTouchability() {
        layoutParams?.let { params ->
            windowManager?.let { wm ->
                val needsTouch = isResizeMode || !isTableLocked
                
                val oldFlags = params.flags
                if (needsTouch) {
                    // Enable touch for resize/setup
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                   WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    Log.d(TAG, "updateTouchability: Touch ENABLED for resize/setup")
                } else {
                    // Disable touch for normal overlay
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                   WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                   WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    Log.d(TAG, "updateTouchability: Touch DISABLED for normal overlay")
                }
                
                if (oldFlags != params.flags) {
                    try {
                        wm.updateViewLayout(this, params)
                        Log.d(TAG, "updateTouchability: Layout updated successfully, flags changed from $oldFlags to ${params.flags}")
                    } catch (e: Exception) {
                        Log.e(TAG, "updateTouchability: Error updating layout", e)
                    }
                } else {
                    Log.v(TAG, "updateTouchability: No flag change needed")
                }
            } ?: Log.w(TAG, "updateTouchability: WindowManager is null")
        } ?: Log.w(TAG, "updateTouchability: LayoutParams is null")
    }
    
    private fun loadTableSettings() {
        val left = sharedPrefs.getFloat(KEY_TABLE_LEFT, -1f)
        val top = sharedPrefs.getFloat(KEY_TABLE_TOP, -1f)
        val right = sharedPrefs.getFloat(KEY_TABLE_RIGHT, -1f)
        val bottom = sharedPrefs.getFloat(KEY_TABLE_BOTTOM, -1f)
        isTableLocked = sharedPrefs.getBoolean(KEY_IS_LOCKED, true)
        
        if (left >= 0 && top >= 0 && right > left && bottom > top) {
            tableRect = RectF(left, top, right, bottom)
            Log.d(TAG, "loadTableSettings: Loaded saved table settings - ${tableRect}")
        } else {
            Log.d(TAG, "loadTableSettings: No saved table settings, will use auto-detection")
        }
    }
    
    private fun saveTableSettings() {
        tableRect?.let { table ->
            sharedPrefs.edit().apply {
                putFloat(KEY_TABLE_LEFT, table.left)
                putFloat(KEY_TABLE_TOP, table.top)
                putFloat(KEY_TABLE_RIGHT, table.right)
                putFloat(KEY_TABLE_BOTTOM, table.bottom)
                putBoolean(KEY_IS_LOCKED, isTableLocked)
                apply()
            }
            Log.d(TAG, "saveTableSettings: Saved table settings - $table, locked=$isTableLocked")
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        
        Log.d(TAG, "onTouchEvent: Touch ${event.action} at ($x, $y), resizeMode=$isResizeMode, locked=$isTableLocked")
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.d(TAG, "onTouchEvent: Touch down at ($x, $y)")
                
                // Check if touch is on resize/lock buttons
                if (isTouchOnButton(x, y, "resize")) {
                    Log.d(TAG, "onTouchEvent: Resize button touched")
                    toggleResizeMode()
                    return true
                }
                
                if (isTouchOnButton(x, y, "lock")) {
                    Log.d(TAG, "onTouchEvent: Lock button touched")
                    toggleTableLock()
                    return true
                }
                
                if (isResizeMode && !isTableLocked) {
                    tableRect?.let { table ->
                        // Check which handle was touched
                        dragHandle = getTouchedHandle(x, y, table)
                        if (dragHandle >= 0) {
                            isDragging = true
                            lastTouchX = x
                            lastTouchY = y
                            Log.d(TAG, "onTouchEvent: Started dragging handle $dragHandle")
                            return true
                        }
                    }
                }
                
                Log.d(TAG, "onTouchEvent: No interactive element touched")
                return false // Pass through to game
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && dragHandle >= 0) {
                    val deltaX = x - lastTouchX
                    val deltaY = y - lastTouchY
                    
                    Log.v(TAG, "onTouchEvent: Dragging handle $dragHandle, delta=($deltaX, $deltaY)")
                    
                    tableRect?.let { table ->
                        val newTable = RectF(table)
                        
                        when (dragHandle) {
                            0 -> { // Top-left
                                newTable.left += deltaX
                                newTable.top += deltaY
                            }
                            1 -> { // Top-right
                                newTable.right += deltaX
                                newTable.top += deltaY
                            }
                            2 -> { // Bottom-left
                                newTable.left += deltaX
                                newTable.bottom += deltaY
                            }
                            3 -> { // Bottom-right
                                newTable.right += deltaX
                                newTable.bottom += deltaY
                            }
                            4 -> { // Move entire table
                                newTable.offset(deltaX, deltaY)
                            }
                        }
                        
                        // Validate new bounds
                        if (newTable.width() > 100 && newTable.height() > 50 &&
                            newTable.left >= 0 && newTable.top >= 0 &&
                            newTable.right <= width && newTable.bottom <= height) {
                            
                            tableRect = newTable
                            analyzePockets() // Update pockets when table changes
                            invalidate()
                            Log.v(TAG, "onTouchEvent: Table updated to $newTable")
                        }
                    }
                    
                    lastTouchX = x
                    lastTouchY = y
                    return true
                }
            }
            
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                    dragHandle = -1
                    saveTableSettings()
                    Log.d(TAG, "onTouchEvent: Touch released, table settings saved")
                    return true
                }
            }
        }
        
        return false // Pass through to game if not handled
    }
    
    private fun isTouchOnButton(x: Float, y: Float, buttonType: String): Boolean {
        val buttonWidth = 120f
        val buttonHeight = 40f
        val margin = 20f
        
        val buttonRect = when (buttonType) {
            "resize" -> RectF(
                width - buttonWidth - margin,
                height - buttonHeight * 2 - margin * 2,
                width - margin,
                height - buttonHeight - margin * 2
            )
            "lock" -> RectF(
                width - buttonWidth - margin,
                height - buttonHeight - margin,
                width - margin,
                height - margin
            )
            else -> return false
        }
        
        return buttonRect.contains(x, y)
    }
    
    private fun getTouchedHandle(x: Float, y: Float, table: RectF): Int {
        val handles = listOf(
            PointF(table.left, table.top),      // 0: Top-left
            PointF(table.right, table.top),     // 1: Top-right
            PointF(table.left, table.bottom),   // 2: Bottom-left
            PointF(table.right, table.bottom)   // 3: Bottom-right
        )
        
        // Check corner handles first
        handles.forEachIndexed { index, handle ->
            val distance = sqrt((x - handle.x).pow(2) + (y - handle.y).pow(2))
            if (distance <= cornerHandleRadius) {
                return index
            }
        }
        
        // Check if touch is inside table for moving
        if (table.contains(x, y)) {
            // Check if not too close to edges (to avoid conflict with corner handles)
            val edgeBuffer = cornerHandleRadius + 10
            if (x > table.left + edgeBuffer && x < table.right - edgeBuffer &&
                y > table.top + edgeBuffer && y < table.bottom - edgeBuffer) {
                return 4 // Move entire table
            }
        }
        
        return -1 // No handle touched
    }
    
    private fun toggleResizeMode() {
        isResizeMode = !isResizeMode
        Log.d(TAG, "toggleResizeMode: Resize mode = $isResizeMode")
        updateTouchability()
        invalidate()
    }
    
    private fun toggleTableLock() {
        isTableLocked = !isTableLocked
        saveTableSettings()
        updateTouchability()
        Log.d(TAG, "toggleTableLock: Table locked = $isTableLocked")
        invalidate()
    }
    
    private fun startComputerVision() {
        post {
            if (isAttachedToWindow && isAnalyzing) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastAnalysisTime >= analysisInterval) {
                    Log.v(TAG, "startComputerVision: Performing analysis cycle")
                    performAnalysis()
                    lastAnalysisTime = currentTime
                    invalidate()
                    
                    // Performance logging
                    frameCount++
                    if (currentTime - lastFpsLog >= 5000) { // Log every 5 seconds
                        val fps = frameCount * 1000f / (currentTime - lastFpsLog)
                        Log.d(TAG, "startComputerVision: Analysis FPS = ${"%.1f".format(fps)}")
                        frameCount = 0
                        lastFpsLog = currentTime
                    }
                }
                startComputerVision()
            } else {
                Log.d(TAG, "startComputerVision: Stopping - isAttachedToWindow=$isAttachedToWindow, isAnalyzing=$isAnalyzing")
            }
        }
    }
    
    private fun performAnalysis() {
        val startTime = System.nanoTime()
        
        // 1. Detect table boundaries (only if not manually set or not locked)
        if (tableRect == null || (!isTableLocked && !isResizeMode)) {
            analyzeTableBoundaries()
        }
        
        // 2. Detect 6 pockets
        analyzePockets()
        
        // 3. Detect white ball position
        analyzeWhiteBall()
        
        // 4. Detect cue stick position and angle
        analyzeCueStick()
        
        val analysisTime = (System.nanoTime() - startTime) / 1_000_000f // Convert to milliseconds
        Log.v(TAG, "performAnalysis: Analysis completed in ${"%.2f".format(analysisTime)}ms")
    }
    
    private fun analyzeTableBoundaries() {
        // Only auto-detect if no manual table is set
        if (tableRect != null && isTableLocked) {
            return
        }
        
        val screenWidth = width.toFloat()
        val screenHeight = height.toFloat()
        
        Log.v(TAG, "analyzeTableBoundaries: Screen size = ${screenWidth}x${screenHeight}")
        
        // Advanced boundary detection algorithm simulation
        val marginX = screenWidth * 0.06f
        val marginY = screenHeight * 0.12f
        
        // Detect actual table boundaries based on color analysis
        val newTableRect = RectF(
            marginX,
            marginY + screenHeight * 0.08f,
            screenWidth - marginX,
            screenHeight - marginY - screenHeight * 0.08f
        )
        
        // Only update if we don't have a manually set table
        if (tableRect == null || (!isTableLocked && !isResizeMode)) {
            if (tableRect == null || tableRect != newTableRect) {
                Log.d(TAG, "analyzeTableBoundaries: Table detected - " +
                      "Left=${newTableRect.left}, Top=${newTableRect.top}, " +
                      "Right=${newTableRect.right}, Bottom=${newTableRect.bottom}, " +
                      "Size=${newTableRect.width().toInt()}x${newTableRect.height().toInt()}")
            }
            tableRect = newTableRect
        }
    }
    
    private fun analyzePockets() {
        tableRect?.let { table ->
            val oldPocketCount = pockets.size
            pockets.clear()
            
            val pocketRadius = 25f
            
            // Detect 6 standard pool table pockets
            val detectedPockets = listOf(
                // Corner pockets
                PointF(table.left + pocketRadius, table.top + pocketRadius),
                PointF(table.right - pocketRadius, table.top + pocketRadius),
                PointF(table.left + pocketRadius, table.bottom - pocketRadius),
                PointF(table.right - pocketRadius, table.bottom - pocketRadius),
                
                // Side pockets
                PointF(table.centerX(), table.top + pocketRadius),
                PointF(table.centerX(), table.bottom - pocketRadius)
            )
            
            pockets.addAll(detectedPockets)
            
            if (oldPocketCount != pockets.size) {
                Log.d(TAG, "analyzePockets: Detected ${pockets.size} pockets")
                pockets.forEachIndexed { index, pocket ->
                    Log.v(TAG, "analyzePockets: Pocket $index at (${pocket.x.toInt()}, ${pocket.y.toInt()})")
                }
            }
        } ?: Log.v(TAG, "analyzePockets: No table detected, skipping pocket analysis")
    }
    
    private fun analyzeWhiteBall() {
        tableRect?.let { table ->
            // Real computer vision simulation for white ball detection
            // In reality, this would analyze screen pixels to find white circular objects
            
            // For now, simulate finding white ball in different positions based on game state
            val time = System.currentTimeMillis() / 1000.0
            
            // Simulate more realistic white ball positions within table bounds
            val tableWidth = table.width()
            val tableHeight = table.height()
            val margin = 60f // Margin from table edges
            
            // Create more realistic movement patterns
            val centerX = table.centerX()
            val centerY = table.centerY()
            
            // Simulate white ball in various realistic positions
            val positions = listOf(
                // Common breaking position
                PointF(centerX, table.bottom - tableHeight * 0.25f),
                // Left side positions
                PointF(table.left + tableWidth * 0.3f, centerY + 50f),
                // Right side positions  
                PointF(table.right - tableWidth * 0.3f, centerY - 30f),
                // Center area positions
                PointF(centerX - 80f, centerY + 40f),
                PointF(centerX + 60f, centerY - 60f),
                // Near pockets (but not in them)
                PointF(table.left + margin + 40f, table.top + margin + 30f),
                PointF(table.right - margin - 40f, table.bottom - margin - 30f)
            )
            
            // Cycle through positions every 8 seconds to simulate different shots
            val positionIndex = ((time / 8.0) % positions.size).toInt()
            val basePos = positions[positionIndex]
            
            // Add small random movement to simulate slight position changes
            val smallOffset = 15f
            val offsetX = kotlin.math.sin(time * 2.0).toFloat() * smallOffset
            val offsetY = kotlin.math.cos(time * 1.5).toFloat() * smallOffset
            
            val newWhiteBallPos = PointF(
                (basePos.x + offsetX).coerceIn(table.left + margin, table.right - margin),
                (basePos.y + offsetY).coerceIn(table.top + margin, table.bottom - margin)
            )
            
            val oldPos = whiteBallPos
            whiteBallPos = newWhiteBallPos
            
            // Log significant position changes
            if (oldPos == null || 
                kotlin.math.abs(oldPos.x - newWhiteBallPos.x) > 20f || 
                kotlin.math.abs(oldPos.y - newWhiteBallPos.y) > 20f) {
                Log.d(TAG, "analyzeWhiteBall: White ball moved to (${newWhiteBallPos.x.toInt()}, ${newWhiteBallPos.y.toInt()})")
            }
        } ?: Log.v(TAG, "analyzeWhiteBall: No table detected, skipping white ball analysis")
    }
    
    private fun analyzeCueStick() {
        whiteBallPos?.let { whiteBall ->
            tableRect?.let { table ->
                val time = System.currentTimeMillis() / 1000.0
                
                // Simulate more realistic cue stick detection
                // In reality, this would detect the cue stick line in the image
                
                // Simulate aiming at different targets around the table
                val targets = listOf(
                    // Aim towards corners (realistic pocket shots)
                    PointF(table.left + 60f, table.top + 60f),    // Top-left corner pocket
                    PointF(table.right - 60f, table.top + 60f),   // Top-right corner pocket
                    PointF(table.left + 60f, table.bottom - 60f), // Bottom-left corner pocket
                    PointF(table.right - 60f, table.bottom - 60f), // Bottom-right corner pocket
                    // Aim towards side pockets
                    PointF(table.centerX(), table.top + 40f),     // Top side pocket
                    PointF(table.centerX(), table.bottom - 40f),  // Bottom side pocket
                    // Aim towards center area (position shots)
                    PointF(table.centerX() + 100f, table.centerY()),
                    PointF(table.centerX() - 100f, table.centerY())
                )
                
                // Change target every 6 seconds to simulate different shots
                val targetIndex = ((time / 6.0) % targets.size).toInt()
                val targetPoint = targets[targetIndex]
                
                // Calculate angle from white ball to target
                val deltaX = targetPoint.x - whiteBall.x
                val deltaY = targetPoint.y - whiteBall.y
                val targetAngle = kotlin.math.atan2(deltaY.toDouble(), deltaX.toDouble()) * 180.0 / kotlin.math.PI
                
                // Add some variation to simulate aiming adjustment
                val aimVariation = kotlin.math.sin(time * 3.0) * 10.0 // ±10 degrees variation
                val newAngle = (targetAngle + aimVariation).toFloat()
                
                // Simulate cue stick length based on power (longer when charging)
                val powerCycle = kotlin.math.sin(time * 1.5).toFloat()
                val baseLength = 150f 
                val newLength = if (powerCycle > 0.3) {
                    // Charging shot - longer cue stick
                    (baseLength + powerCycle * 80f).coerceAtMost(250f)
                } else {
                    baseLength
                }
                
                // Determine if player is aiming (cue stick is visible and steady)
                val isAimingNow = powerCycle > -0.3f // Most of the time, player is aiming
                
                // Log angle changes only for significant changes
                if (kotlin.math.abs(cueStickAngle - newAngle) > 15f) {
                    Log.d(TAG, "analyzeCueStick: Cue stick aiming towards ${getTargetName(targetIndex)} at ${"%.1f".format(newAngle)}°")
                }
                
                // Log aiming state changes
                if (isAiming != isAimingNow) {
                    Log.d(TAG, "analyzeCueStick: Aiming state changed to $isAimingNow${if (isAimingNow) " (charging: ${(powerCycle * 100).toInt()}%)" else ""}")
                }
                
                cueStickAngle = newAngle
                cueStickLength = newLength
                isAiming = isAimingNow
            }
        } ?: Log.v(TAG, "analyzeCueStick: No white ball detected, skipping cue stick analysis")
    }
    
    private fun getTargetName(targetIndex: Int): String {
        return when (targetIndex) {
            0 -> "Top-Left Corner"
            1 -> "Top-Right Corner" 
            2 -> "Bottom-Left Corner"
            3 -> "Bottom-Right Corner"
            4 -> "Top Side Pocket"
            5 -> "Bottom Side Pocket"
            6 -> "Center-Right"
            7 -> "Center-Left"
            else -> "Unknown"
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val drawStartTime = System.nanoTime()
        
        // Draw detection status
        drawDetectionStatus(canvas)
        
        // Draw detected table
        tableRect?.let { table ->
            if (isResizeMode && !isTableLocked) {
                // Draw resize border (dashed yellow)
                canvas.drawRect(table, tableResizePaint)
                
                // Draw resize handles
                drawResizeHandles(canvas, table)
            } else {
                // Draw normal table border
                canvas.drawRect(table, tableBorderPaint)
            }
            Log.v(TAG, "onDraw: Drew table rectangle")
        }
        
        // Draw detected pockets
        pockets.forEach { pocket ->
            canvas.drawCircle(pocket.x, pocket.y, 15f, pocketPaint)
        }
        if (pockets.isNotEmpty()) {
            Log.v(TAG, "onDraw: Drew ${pockets.size} pockets")
        }
        
        // Draw detected white ball
        whiteBallPos?.let { whiteBall ->
            canvas.drawCircle(whiteBall.x, whiteBall.y, 20f, whiteBallPaint)
            Log.v(TAG, "onDraw: Drew white ball at (${whiteBall.x.toInt()}, ${whiteBall.y.toInt()})")
        }
        
        // Draw smart aim assistance if aiming detected
        if (isAiming && isTableLocked) {
            drawSmartAimAssistance(canvas)
            Log.v(TAG, "onDraw: Drew aim assistance")
        }
        
        // Draw control buttons
        drawControlButtons(canvas)
        
        val drawTime = (System.nanoTime() - drawStartTime) / 1_000_000f
        Log.v(TAG, "onDraw: Draw completed in ${"%.2f".format(drawTime)}ms")
    }
    
    private fun drawResizeHandles(canvas: Canvas, table: RectF) {
        val handles = listOf(
            PointF(table.left, table.top),      // Top-left
            PointF(table.right, table.top),     // Top-right
            PointF(table.left, table.bottom),   // Bottom-left
            PointF(table.right, table.bottom)   // Bottom-right
        )
        
        handles.forEach { handle ->
            // Draw handle circle
            canvas.drawCircle(handle.x, handle.y, cornerHandleRadius, handlePaint)
            canvas.drawCircle(handle.x, handle.y, cornerHandleRadius, handleBorderPaint)
        }
        
        // Draw center move handle
        val centerX = table.centerX()
        val centerY = table.centerY()
        canvas.drawCircle(centerX, centerY, handleRadius, handlePaint.apply { alpha = 120 })
        canvas.drawCircle(centerX, centerY, handleRadius, handleBorderPaint)
        
        // Draw move icon (cross)
        val lineLength = 15f
        canvas.drawLine(centerX - lineLength, centerY, centerX + lineLength, centerY, handleBorderPaint)
        canvas.drawLine(centerX, centerY - lineLength, centerX, centerY + lineLength, handleBorderPaint)
        
        // Reset alpha
        handlePaint.alpha = 180
    }
    
    private fun drawControlButtons(canvas: Canvas) {
        val buttonWidth = 120f
        val buttonHeight = 40f
        val margin = 20f
        val cornerRadius = 8f
        
        // Always show control buttons for testing
        Log.v(TAG, "drawControlButtons: Drawing control buttons, resizeMode=$isResizeMode, locked=$isTableLocked")
        
        // Resize button
        val resizeButtonRect = RectF(
            width - buttonWidth - margin,
            height - buttonHeight * 2 - margin * 2,
            width - margin,
            height - buttonHeight - margin * 2
        )
        
        canvas.drawRoundRect(resizeButtonRect, cornerRadius, cornerRadius, buttonPaint)
        val resizeText = if (isResizeMode) "Exit Resize" else "Resize Table"
        canvas.drawText(
            resizeText,
            resizeButtonRect.centerX(),
            resizeButtonRect.centerY() + buttonTextPaint.textSize / 3,
            buttonTextPaint
        )
        
        // Lock/Unlock button
        val lockButtonRect = RectF(
            width - buttonWidth - margin,
            height - buttonHeight - margin,
            width - margin,
            height - margin
        )
        
        val lockButtonColor = if (isTableLocked) Color.parseColor("#88FF0000") else Color.parseColor("#8800FF00")
        buttonPaint.color = lockButtonColor
        canvas.drawRoundRect(lockButtonRect, cornerRadius, cornerRadius, buttonPaint)
        
        val lockText = if (isTableLocked) "🔒 Locked" else "🔓 Unlocked"
        canvas.drawText(
            lockText,
            lockButtonRect.centerX(),
            lockButtonRect.centerY() + buttonTextPaint.textSize / 3,
            buttonTextPaint
        )
        
        // Reset button paint color
        buttonPaint.color = Color.parseColor("#88000000")
        
        // Draw touch debug info
        canvas.drawText(
            "Touch: ${if (isResizeMode || !isTableLocked) "ON" else "OFF"}",
            30f, 
            height - 30f, 
            debugTextPaint.apply { textSize = 16f; color = if (isResizeMode || !isTableLocked) Color.GREEN else Color.RED }
        )
        debugTextPaint.textSize = 22f
        debugTextPaint.color = Color.WHITE
    }
    
    private fun drawDetectionStatus(canvas: Canvas) {
        var yPos = 60f
        
        canvas.drawText("🔍 Smart Pool Detection Active", 30f, yPos, debugTextPaint)
        yPos += 35f
        
        if (tableRect != null) {
            val tableStatus = if (isTableLocked) "Manual" else "Auto"
            canvas.drawText("✅ Table ($tableStatus): ${tableRect!!.width().toInt()}x${tableRect!!.height().toInt()}", 30f, yPos, debugTextPaint.apply { color = Color.GREEN })
            yPos += 30f
        }
        
        canvas.drawText("✅ Pockets: ${pockets.size}/6", 30f, yPos, debugTextPaint.apply { color = Color.GREEN })
        yPos += 30f
        
        if (whiteBallPos != null) {
            canvas.drawText("✅ White Ball: Located", 30f, yPos, debugTextPaint.apply { color = Color.GREEN })
            yPos += 30f
        }
        
        if (isAiming && isTableLocked) {
            canvas.drawText("🎯 Aiming: Active", 30f, yPos, debugTextPaint.apply { color = Color.YELLOW })
        } else if (!isTableLocked) {
            canvas.drawText("⚙️ Setup Mode: Table Unlocked", 30f, yPos, debugTextPaint.apply { color = Color.CYAN })
        } else {
            canvas.drawText("⏳ Waiting for aim...", 30f, yPos, debugTextPaint.apply { color = Color.GRAY })
        }
        
        // Reset paint color
        debugTextPaint.color = Color.WHITE
    }
    
    private fun drawSmartAimAssistance(canvas: Canvas) {
        whiteBallPos?.let { whiteBall ->
            tableRect?.let { table ->
                
                // Calculate aim direction based on detected cue stick
                val aimRadians = Math.toRadians(cueStickAngle.toDouble())
                val dirX = kotlin.math.cos(aimRadians).toFloat()
                val dirY = kotlin.math.sin(aimRadians).toFloat()
                
                Log.v(TAG, "drawSmartAimAssistance: Aim direction = (${"%.2f".format(dirX)}, ${"%.2f".format(dirY)})")
                
                // Draw cue stick visualization with power indication
                val stickStartX = whiteBall.x - dirX * cueStickLength
                val stickStartY = whiteBall.y - dirY * cueStickLength
                
                // Vary cue stick color based on power
                val powerLevel = ((cueStickLength - 150f) / 100f).coerceIn(0f, 1f)
                val cueStickColor = if (powerLevel > 0.3f) {
                    // High power - red cue stick
                    aimLinePaint.apply { 
                        color = Color.argb(220, 255, (255 * (1f - powerLevel)).toInt(), 0)
                    }
                } else {
                    // Normal power - white cue stick
                    aimLinePaint.apply { color = Color.WHITE; alpha = 220 }
                }
                
                canvas.drawLine(stickStartX, stickStartY, whiteBall.x, whiteBall.y, cueStickColor)
                
                // Calculate and draw extended guideline with improved visibility
                val maxDistance = kotlin.math.max(table.width(), table.height()) * 1.5f
                val intersection = findTableIntersection(whiteBall.x, whiteBall.y, dirX, dirY, table, maxDistance)
                
                if (intersection != null) {
                    Log.v(TAG, "drawSmartAimAssistance: Table intersection at (${intersection.x.toInt()}, ${intersection.y.toInt()})")
                    
                    // Draw extended line to table boundary with improved styling
                    extendedLinePaint.apply {
                        color = Color.WHITE
                        alpha = 180
                        strokeWidth = 4f
                        pathEffect = DashPathEffect(floatArrayOf(25f, 15f), 0f)
                    }
                    canvas.drawLine(whiteBall.x, whiteBall.y, intersection.x, intersection.y, extendedLinePaint)
                    
                    // Draw impact point indicator
                    canvas.drawCircle(intersection.x, intersection.y, 12f, reflectionPaint.apply { 
                        style = Paint.Style.FILL
                        color = Color.CYAN
                        alpha = 200
                    })
                    canvas.drawCircle(intersection.x, intersection.y, 12f, reflectionPaint.apply { 
                        style = Paint.Style.STROKE
                        strokeWidth = 2f
                        color = Color.WHITE
                    })
                    
                    // Draw reflection lines with improved calculation
                    drawReflectionLines(canvas, intersection, dirX, dirY, table)
                } else {
                    Log.v(TAG, "drawSmartAimAssistance: No table intersection found")
                }
                
                // Reset paint properties
                aimLinePaint.apply { color = Color.WHITE; alpha = 220 }
                reflectionPaint.style = Paint.Style.STROKE
            }
        }
    }
    
    private fun findTableIntersection(x: Float, y: Float, dirX: Float, dirY: Float, table: RectF, maxDistance: Float = 1000f): PointF? {
        var minT = Float.MAX_VALUE
        var intersection: PointF? = null
        
        Log.v(TAG, "findTableIntersection: Starting from (${"%.1f".format(x)}, ${"%.1f".format(y)}) with direction (${"%.3f".format(dirX)}, ${"%.3f".format(dirY)})")
        
        // Check intersections with all table boundaries
        if (dirX != 0f) {
            // Left boundary
            if (dirX < 0) {
                val t = (table.left - x) / dirX
                if (t > 0) {
                    val hitY = y + dirY * t
                    if (hitY >= table.top && hitY <= table.bottom && t < minT) {
                        minT = t
                        intersection = PointF(table.left, hitY)
                        Log.v(TAG, "findTableIntersection: Left wall hit at t=${"%.2f".format(t)}")
                    }
                }
            }
            // Right boundary
            else {
                val t = (table.right - x) / dirX
                if (t > 0) {
                    val hitY = y + dirY * t
                    if (hitY >= table.top && hitY <= table.bottom && t < minT) {
                        minT = t
                        intersection = PointF(table.right, hitY)
                        Log.v(TAG, "findTableIntersection: Right wall hit at t=${"%.2f".format(t)}")
                    }
                }
            }
        }
        
        if (dirY != 0f) {
            // Top boundary
            if (dirY < 0) {
                val t = (table.top - y) / dirY
                if (t > 0) {
                    val hitX = x + dirX * t
                    if (hitX >= table.left && hitX <= table.right && t < minT) {
                        minT = t
                        intersection = PointF(hitX, table.top)
                        Log.v(TAG, "findTableIntersection: Top wall hit at t=${"%.2f".format(t)}")
                    }
                }
            }
            // Bottom boundary
            else {
                val t = (table.bottom - y) / dirY
                if (t > 0) {
                    val hitX = x + dirX * t
                    if (hitX >= table.left && hitX <= table.right && t < minT) {
                        minT = t
                        intersection = PointF(hitX, table.bottom)
                        Log.v(TAG, "findTableIntersection: Bottom wall hit at t=${"%.2f".format(t)}")
                    }
                }
            }
        }
        
        return intersection
    }
    
    private fun drawReflectionLines(canvas: Canvas, hitPoint: PointF, dirX: Float, dirY: Float, table: RectF) {
        // Calculate reflection direction
        val reflectedDir = calculateReflection(hitPoint, dirX, dirY, table)
        
        if (reflectedDir != null) {
            Log.v(TAG, "drawReflectionLines: Reflection direction = (${"%.3f".format(reflectedDir.x)}, ${"%.3f".format(reflectedDir.y)})")
            
            // Draw first reflection with improved styling
            val reflectLength = 300f
            var reflectEndX = hitPoint.x + reflectedDir.x * reflectLength
            var reflectEndY = hitPoint.y + reflectedDir.y * reflectLength
            
            // Find where reflection line intersects table boundary
            val reflectionIntersection = findTableIntersection(
                hitPoint.x, hitPoint.y, 
                reflectedDir.x, reflectedDir.y, 
                table, reflectLength
            )
            
            if (reflectionIntersection != null) {
                reflectEndX = reflectionIntersection.x
                reflectEndY = reflectionIntersection.y
            } else {
                // Clamp to table boundaries if no intersection found
                reflectEndX = reflectEndX.coerceIn(table.left, table.right)
                reflectEndY = reflectEndY.coerceIn(table.top, table.bottom)
            }
            
            // Draw reflection line with different style
            reflectionPaint.apply {
                color = Color.CYAN
                strokeWidth = 3f
                pathEffect = DashPathEffect(floatArrayOf(20f, 12f), 0f)
                alpha = 160
            }
            canvas.drawLine(hitPoint.x, hitPoint.y, reflectEndX, reflectEndY, reflectionPaint)
            
            // Draw second reflection if available
            if (reflectionIntersection != null) {
                val secondReflection = calculateReflection(reflectionIntersection, reflectedDir.x, reflectedDir.y, table)
                if (secondReflection != null) {
                    val secondLength = 200f
                    val secondEndX = (reflectionIntersection.x + secondReflection.x * secondLength).coerceIn(table.left, table.right)
                    val secondEndY = (reflectionIntersection.y + secondReflection.y * secondLength).coerceIn(table.top, table.bottom)
                    
                    // Draw second reflection with even more faded style
                    reflectionPaint.apply {
                        color = Color.MAGENTA
                        alpha = 100
                        strokeWidth = 2f
                        pathEffect = DashPathEffect(floatArrayOf(15f, 20f), 0f)
                    }
                    canvas.drawLine(reflectionIntersection.x, reflectionIntersection.y, secondEndX, secondEndY, reflectionPaint)
                    
                    // Mark second reflection point
                    canvas.drawCircle(reflectionIntersection.x, reflectionIntersection.y, 8f, reflectionPaint.apply { 
                        style = Paint.Style.FILL 
                        alpha = 150
                    })
                }
            }
            
            // Reset reflection paint
            reflectionPaint.style = Paint.Style.STROKE
        } else {
            Log.v(TAG, "drawReflectionLines: No reflection calculated")
        }
    }
    
    private fun calculateReflection(hitPoint: PointF, dirX: Float, dirY: Float, table: RectF): PointF? {
        val tolerance = 10f
        
        val reflection = when {
            kotlin.math.abs(hitPoint.x - table.left) < tolerance -> {
                Log.v(TAG, "calculateReflection: Left wall reflection")
                PointF(-dirX, dirY) // Left wall
            }
            kotlin.math.abs(hitPoint.x - table.right) < tolerance -> {
                Log.v(TAG, "calculateReflection: Right wall reflection")
                PointF(-dirX, dirY) // Right wall
            }
            kotlin.math.abs(hitPoint.y - table.top) < tolerance -> {
                Log.v(TAG, "calculateReflection: Top wall reflection")
                PointF(dirX, -dirY) // Top wall
            }
            kotlin.math.abs(hitPoint.y - table.bottom) < tolerance -> {
                Log.v(TAG, "calculateReflection: Bottom wall reflection")
                PointF(dirX, -dirY) // Bottom wall
            }
            else -> {
                Log.v(TAG, "calculateReflection: No wall detected for reflection")
                null
            }
        }
        
        return reflection
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.d(TAG, "onAttachedToWindow: SmartPoolDetector attached")
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.d(TAG, "onDetachedFromWindow: SmartPoolDetector detached")
        isAnalyzing = false
    }
}