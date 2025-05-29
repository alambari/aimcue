package com.example.aimcue

import android.content.Context
import android.view.View
import android.graphics.*
import android.view.WindowManager
import android.util.Log
import android.view.MotionEvent
import android.content.SharedPreferences
import kotlin.math.*

class AdvancedPoolOverlay(context: Context) : View(context) {
    
    companion object {
        private const val TAG = "PoolOverlay_Advanced"
        private const val PREFS_NAME = "advanced_pool_settings"
        private const val KEY_TABLE_LEFT = "table_left"
        private const val KEY_TABLE_TOP = "table_top"
        private const val KEY_TABLE_RIGHT = "table_right"
        private const val KEY_TABLE_BOTTOM = "table_bottom"
        private const val KEY_IS_LOCKED = "is_locked"
        private const val KEY_SHOT_POWER = "shot_power"
        private const val KEY_SPIN_X = "spin_x"
        private const val KEY_SPIN_Y = "spin_y"
    }
    
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    
    // Table properties
    private var tableRect: RectF? = null
    private var pockets = mutableListOf<PointF>()
    
    // Manual resize properties
    private var isResizeMode = false
    private var isTableLocked = true
    private var isDragging = false
    private var dragHandle = -1
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val handleRadius = 40f
    private val cornerHandleRadius = 30f
    
    // Advanced aiming properties
    private var aimStartPoint: PointF? = null
    private var aimEndPoint: PointF? = null
    private var isAiming = false
    private var shotPower = 0.5f
    private var spinX = 0f
    private var spinY = 0f
    
    // Display options
    private var showCushionShots = true
    private var showPowerIndicator = true
    private var showSpinIndicator = true
    
    // Cushion calculation
    private var cushionPath = mutableListOf<PointF>()
    
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
    
    private val trajectoryPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
        alpha = 220
    }
    
    private val cushionPaint1 = Paint().apply {
        color = Color.CYAN
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(20f, 12f), 0f)
        alpha = 160
    }
    
    private val cushionPaint2 = Paint().apply {
        color = Color.MAGENTA
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(15f, 20f), 0f)
        alpha = 120
    }
    
    private val impactPointPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
        isAntiAlias = true
        alpha = 200
    }
    
    private val powerBarBackgroundPaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.FILL
        isAntiAlias = true
        alpha = 150
    }
    
    private val powerBarFillPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        isAntiAlias = true
        alpha = 200
    }
    
    private val cueBallPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
        alpha = 200
    }
    
    private val spinDotPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val infoTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 16f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    
    private val infoBackgroundPaint = Paint().apply {
        color = Color.parseColor("#88000000")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val buttonPaint = Paint().apply {
        color = Color.parseColor("#88000000")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val buttonTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 18f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    
    init {
        Log.d(TAG, "AdvancedPoolOverlay initialized")
        loadSettings()
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
    fun getShotPower(): Float = shotPower
    fun getSpinX(): Float = spinX
    fun getSpinY(): Float = spinY
    
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
    
    fun setShotPower(power: Float) {
        shotPower = power.coerceIn(0f, 1f)
        saveSettings()
        invalidate()
    }
    
    fun setSpin(x: Float, y: Float) {
        spinX = x.coerceIn(-1f, 1f)
        spinY = y.coerceIn(-1f, 1f)
        saveSettings()
        invalidate()
    }
    
    fun toggleCushionShots() {
        showCushionShots = !showCushionShots
        invalidate()
    }
    
    fun togglePowerIndicator() {
        showPowerIndicator = !showPowerIndicator
        invalidate()
    }
    
    private fun loadSettings() {
        val left = sharedPrefs.getFloat(KEY_TABLE_LEFT, -1f)
        val top = sharedPrefs.getFloat(KEY_TABLE_TOP, -1f)
        val right = sharedPrefs.getFloat(KEY_TABLE_RIGHT, -1f)
        val bottom = sharedPrefs.getFloat(KEY_TABLE_BOTTOM, -1f)
        isTableLocked = sharedPrefs.getBoolean(KEY_IS_LOCKED, true)
        shotPower = sharedPrefs.getFloat(KEY_SHOT_POWER, 0.5f)
        spinX = sharedPrefs.getFloat(KEY_SPIN_X, 0f)
        spinY = sharedPrefs.getFloat(KEY_SPIN_Y, 0f)
        
        if (left >= 0 && top >= 0 && right > left && bottom > top) {
            tableRect = RectF(left, top, right, bottom)
            analyzePockets()
            Log.d(TAG, "loadSettings: Loaded saved table settings - $tableRect")
        } else {
            Log.d(TAG, "loadSettings: No saved table settings, will use auto-detection")
        }
    }
    
    private fun saveSettings() {
        tableRect?.let { table ->
            sharedPrefs.edit().apply {
                putFloat(KEY_TABLE_LEFT, table.left)
                putFloat(KEY_TABLE_TOP, table.top)
                putFloat(KEY_TABLE_RIGHT, table.right)
                putFloat(KEY_TABLE_BOTTOM, table.bottom)
                putBoolean(KEY_IS_LOCKED, isTableLocked)
                putFloat(KEY_SHOT_POWER, shotPower)
                putFloat(KEY_SPIN_X, spinX)
                putFloat(KEY_SPIN_Y, spinY)
                apply()
            }
            Log.d(TAG, "saveSettings: Saved settings")
        }
    }
    
    private fun updateTouchability() {
        layoutParams?.let { params ->
            windowManager?.let { wm ->
                val needsTouch = isResizeMode || !isTableLocked || isAiming
                
                val oldFlags = params.flags
                if (needsTouch) {
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                   WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    Log.d(TAG, "updateTouchability: Touch ENABLED")
                } else {
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                   WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                   WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    Log.d(TAG, "updateTouchability: Touch DISABLED")
                }
                
                if (oldFlags != params.flags) {
                    try {
                        wm.updateViewLayout(this, params)
                        Log.d(TAG, "updateTouchability: Layout updated successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "updateTouchability: Error updating layout", e)
                    }
                }
            }
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.d(TAG, "onTouchEvent: Touch down at ($x, $y)")
                
                if (isTouchOnButton(x, y, "resize")) {
                    toggleResizeMode()
                    return true
                }
                
                if (isTouchOnButton(x, y, "lock")) {
                    toggleTableLock()
                    return true
                }
                
                if (isResizeMode && !isTableLocked) {
                    tableRect?.let { table ->
                        dragHandle = getTouchedHandle(x, y, table)
                        if (dragHandle >= 0) {
                            isDragging = true
                            lastTouchX = x
                            lastTouchY = y
                            Log.d(TAG, "onTouchEvent: Started dragging handle $dragHandle")
                            return true
                        }
                    }
                } else if (isTableLocked && !isResizeMode) {
                    tableRect?.let { table ->
                        if (table.contains(x, y)) {
                            aimStartPoint = PointF(x, y)
                            aimEndPoint = PointF(x, y)
                            isAiming = true
                            updateTouchability()
                            Log.d(TAG, "onTouchEvent: Started aiming from ($x, $y)")
                            return true
                        }
                    }
                }
                
                return false
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && dragHandle >= 0) {
                    val deltaX = x - lastTouchX
                    val deltaY = y - lastTouchY
                    
                    tableRect?.let { table ->
                        val newTable = RectF(table)
                        
                        when (dragHandle) {
                            0 -> { newTable.left += deltaX; newTable.top += deltaY }
                            1 -> { newTable.right += deltaX; newTable.top += deltaY }
                            2 -> { newTable.left += deltaX; newTable.bottom += deltaY }
                            3 -> { newTable.right += deltaX; newTable.bottom += deltaY }
                            4 -> { newTable.offset(deltaX, deltaY) }
                        }
                        
                        if (newTable.width() > 100 && newTable.height() > 50 &&
                            newTable.left >= 0 && newTable.top >= 0 &&
                            newTable.right <= width && newTable.bottom <= height) {
                            
                            tableRect = newTable
                            analyzePockets()
                            invalidate()
                        }
                    }
                    
                    lastTouchX = x
                    lastTouchY = y
                    return true
                } else if (isAiming && aimStartPoint != null) {
                    aimEndPoint = PointF(x, y)
                    calculateCushionPath()
                    invalidate()
                    return true
                }
            }
            
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                    dragHandle = -1
                    saveSettings()
                    Log.d(TAG, "onTouchEvent: Resize completed")
                    return true
                } else if (isAiming) {
                    isAiming = false
                    updateTouchability()
                    Log.d(TAG, "onTouchEvent: Aiming completed")
                    return true
                }
            }
        }
        
        return false
    }
    
    private fun calculateCushionPath() {
        cushionPath.clear()
        
        val start = aimStartPoint ?: return
        val end = aimEndPoint ?: return
        val table = tableRect ?: return
        
        val distance = sqrt((end.x - start.x).pow(2) + (end.y - start.y).pow(2))
        if (distance < 10f) return
        
        val dirX = (end.x - start.x) / distance
        val dirY = (end.y - start.y) / distance
        
        var currentX = end.x
        var currentY = end.y
        var currentDirX = dirX
        var currentDirY = dirY
        
        // Calculate up to 3 cushion reflections
        repeat(3) { iteration ->
            val intersection = findTableIntersection(currentX, currentY, currentDirX, currentDirY, table)
            if (intersection != null) {
                cushionPath.add(intersection)
                
                val reflection = calculateReflection(intersection, currentDirX, currentDirY, table)
                reflection?.let {
                    currentX = intersection.x
                    currentY = intersection.y
                    currentDirX = it.x
                    currentDirY = it.y
                }
            } else {
                return@repeat
            }
        }
    }
    
    private fun findTableIntersection(x: Float, y: Float, dirX: Float, dirY: Float, table: RectF): PointF? {
        var minT = Float.MAX_VALUE
        var intersection: PointF? = null
        
        if (dirX != 0f) {
            if (dirX < 0) {
                val t = (table.left - x) / dirX
            } else {
                val t = (table.right - x) / dirX
                if (t > 0) {
                    val hitY = y + dirY * t
                    if (hitY >= table.top && hitY <= table.bottom && t < minT) {
                        minT = t
                        intersection = PointF(table.right, hitY)
                    }
                }
            }
        }
        
        if (dirY != 0f) {
            if (dirY < 0) {
                val t = (table.top - y) / dirY
                if (t > 0) {
                    val hitX = x + dirX * t
                    if (hitX >= table.left && hitX <= table.right && t < minT) {
                        minT = t
                        intersection = PointF(hitX, table.top)
                    }
                }
            } else {
                val t = (table.bottom - y) / dirY
                if (t > 0) {
                    val hitX = x + dirX * t
                    if (hitX >= table.left && hitX <= table.right && t < minT) {
                        minT = t
                        intersection = PointF(hitX, table.bottom)
                    }
                }
            }
        }
        
        return intersection
    }
    
    private fun calculateReflection(hitPoint: PointF, dirX: Float, dirY: Float, table: RectF): PointF? {
        val tolerance = 10f
        
        return when {
            abs(hitPoint.x - table.left) < tolerance -> PointF(-dirX, dirY)
            abs(hitPoint.x - table.right) < tolerance -> PointF(-dirX, dirY)
            abs(hitPoint.y - table.top) < tolerance -> PointF(dirX, -dirY)
            abs(hitPoint.y - table.bottom) < tolerance -> PointF(dirX, -dirY)
            else -> null
        }
    }
    
    private fun analyzePockets() {
        tableRect?.let { table ->
            pockets.clear()
            
            val pocketRadius = 25f
            
            pockets.addAll(listOf(
                PointF(table.left + pocketRadius, table.top + pocketRadius),
                PointF(table.right - pocketRadius, table.top + pocketRadius),
                PointF(table.left + pocketRadius, table.bottom - pocketRadius),
                PointF(table.right - pocketRadius, table.bottom - pocketRadius),
                PointF(table.centerX(), table.top + pocketRadius),
                PointF(table.centerX(), table.bottom - pocketRadius)
            ))
        }
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
            PointF(table.left, table.top),
            PointF(table.right, table.top),
            PointF(table.left, table.bottom),
            PointF(table.right, table.bottom)
        )
        
        handles.forEachIndexed { index, handle ->
            val distance = sqrt((x - handle.x).pow(2) + (y - handle.y).pow(2))
            if (distance <= cornerHandleRadius) {
                return index
            }
        }
        
        if (table.contains(x, y)) {
            val edgeBuffer = cornerHandleRadius + 10
            if (x > table.left + edgeBuffer && x < table.right - edgeBuffer &&
                y > table.top + edgeBuffer && y < table.bottom - edgeBuffer) {
                return 4
            }
        }
        
        return -1
    }
    
    private fun toggleResizeMode() {
        isResizeMode = !isResizeMode
        Log.d(TAG, "toggleResizeMode: Resize mode = $isResizeMode")
        updateTouchability()
        invalidate()
    }
    
    private fun toggleTableLock() {
        isTableLocked = !isTableLocked
        saveSettings()
        updateTouchability()
        Log.d(TAG, "toggleTableLock: Table locked = $isTableLocked")
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw table
        tableRect?.let { table ->
            if (isResizeMode && !isTableLocked) {
                canvas.drawRect(table, tableResizePaint)
                drawResizeHandles(canvas, table)
            } else {
                canvas.drawRect(table, tableBorderPaint)
            }
        }
        
        // Draw pockets
        pockets.forEach { pocket ->
            canvas.drawCircle(pocket.x, pocket.y, 15f, pocketPaint)
        }
        
        // Draw aiming system
        if (isTableLocked && !isResizeMode) {
            drawAimingSystem(canvas)
        }
        
        // Draw power and spin indicators
        if (showPowerIndicator) {
            drawPowerIndicator(canvas)
        }
        
        if (showSpinIndicator) {
            drawSpinIndicator(canvas)
        }
        
        // Draw shot info
        drawShotInfo(canvas)
        
        // Draw control buttons
        drawControlButtons(canvas)
        
        // Draw status
        drawStatus(canvas)
    }
    
    private fun drawResizeHandles(canvas: Canvas, table: RectF) {
        val handles = listOf(
            PointF(table.left, table.top),
            PointF(table.right, table.top),
            PointF(table.left, table.bottom),
            PointF(table.right, table.bottom)
        )
        
        handles.forEach { handle ->
            canvas.drawCircle(handle.x, handle.y, cornerHandleRadius, handlePaint)
            canvas.drawCircle(handle.x, handle.y, cornerHandleRadius, handleBorderPaint)
        }
        
        val centerX = table.centerX()
        val centerY = table.centerY()
        canvas.drawCircle(centerX, centerY, handleRadius, handlePaint.apply { alpha = 120 })
        canvas.drawCircle(centerX, centerY, handleRadius, handleBorderPaint)
        
        val lineLength = 15f
        canvas.drawLine(centerX - lineLength, centerY, centerX + lineLength, centerY, handleBorderPaint)
        canvas.drawLine(centerX, centerY - lineLength, centerX, centerY + lineLength, handleBorderPaint)
        
        handlePaint.alpha = 180
    }
    
    private fun drawAimingSystem(canvas: Canvas) {
        val start = aimStartPoint
        val end = aimEndPoint
        
        if (start != null && end != null) {
            val distance = sqrt((end.x - start.x).pow(2) + (end.y - start.y).pow(2))
            if (distance > 10f) {
                trajectoryPaint.strokeWidth = 4f + (shotPower * 4f)
                trajectoryPaint.alpha = (150 + shotPower * 70).toInt()
                
                canvas.drawLine(start.x, start.y, end.x, end.y, trajectoryPaint)
                
                if (showCushionShots && cushionPath.isNotEmpty()) {
                    var previousPoint: PointF = end
                    
                    cushionPath.forEachIndexed { index, point ->
                        val paint = if (index == 0) cushionPaint1 else cushionPaint2
                        canvas.drawLine(previousPoint.x, previousPoint.y, point.x, point.y, paint)
                        
                        canvas.drawCircle(point.x, point.y, 8f, impactPointPaint)
                        canvas.drawCircle(point.x, point.y, 8f, handleBorderPaint.apply { strokeWidth = 2f })
                        
                        previousPoint = point
                    }
                    handleBorderPaint.strokeWidth = 3f
                }
                
                canvas.drawCircle(start.x, start.y, 12f, impactPointPaint)
                canvas.drawCircle(start.x, start.y, 12f, handleBorderPaint.apply { strokeWidth = 2f })
                handleBorderPaint.strokeWidth = 3f
            }
        }
    }
    
    private fun drawPowerIndicator(canvas: Canvas) {
        val powerBarRect = RectF(50f, height - 150f, 250f, height - 120f)
        canvas.drawRoundRect(powerBarRect, 8f, 8f, powerBarBackgroundPaint)
        
        val powerFillWidth = powerBarRect.width() * shotPower
        val powerFillRect = RectF(
            powerBarRect.left, 
            powerBarRect.top,
            powerBarRect.left + powerFillWidth, 
            powerBarRect.bottom
        )
        
        val powerColor = when {
            shotPower < 0.3f -> Color.GREEN
            shotPower < 0.7f -> Color.YELLOW
            else -> Color.RED
        }
        powerBarFillPaint.color = powerColor
        
        canvas.drawRoundRect(powerFillRect, 8f, 8f, powerBarFillPaint)
        
        canvas.drawText(
            "Power: ${(shotPower * 100).toInt()}%",
            powerBarRect.centerX(),
            powerBarRect.bottom + 25f,
            infoTextPaint.apply { textAlign = Paint.Align.CENTER }
        )
        infoTextPaint.textAlign = Paint.Align.LEFT
    }
    
    private fun drawSpinIndicator(canvas: Canvas) {
        val spinCenterX = width - 100f
        val spinCenterY = height - 100f
        
        canvas.drawCircle(spinCenterX, spinCenterY, 40f, cueBallPaint)
        canvas.drawCircle(spinCenterX, spinCenterY, 40f, handleBorderPaint.apply { strokeWidth = 2f })
        
        val spinDotX = spinCenterX + spinX * 30f
        val spinDotY = spinCenterY + spinY * 30f
        canvas.drawCircle(spinDotX, spinDotY, 6f, spinDotPaint)
        
        canvas.drawText(
            "Spin",
            spinCenterX,
            spinCenterY + 60f,
            infoTextPaint.apply { textAlign = Paint.Align.CENTER }
        )
        
        handleBorderPaint.strokeWidth = 3f
        infoTextPaint.textAlign = Paint.Align.LEFT
    }
    
    private fun drawShotInfo(canvas: Canvas) {
        val start = aimStartPoint
        val end = aimEndPoint
        
        if (start != null && end != null) {
            val distance = sqrt((start.x - end.x).pow(2) + (start.y - end.y).pow(2))
            val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble()) * 180.0 / PI
            
            val infoText = "Distance: ${distance.toInt()}px\n" +
                          "Angle: ${angle.toInt()}°\n" +
                          "Power: ${(shotPower * 100).toInt()}%\n" +
                          "Spin: ${(spinX * 100).toInt()}, ${(spinY * 100).toInt()}"
            
            val lines = infoText.split("\n")
            val lineHeight = infoTextPaint.textSize + 4f
            val boxHeight = lines.size * lineHeight + 20f
            val boxWidth = 180f
            
            val infoRect = RectF(30f, 200f, 30f + boxWidth, 200f + boxHeight)
            canvas.drawRoundRect(infoRect, 8f, 8f, infoBackgroundPaint)
            
            lines.forEachIndexed { index, line ->
                canvas.drawText(
                    line,
                    40f,
                    220f + index * lineHeight,
                    infoTextPaint
                )
            }
        }
    }
    
    private fun drawControlButtons(canvas: Canvas) {
        val buttonWidth = 120f
        val buttonHeight = 40f
        val margin = 20f
        val cornerRadius = 8f
        
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
        
        buttonPaint.color = Color.parseColor("#88000000")
    }
    
    private fun drawStatus(canvas: Canvas) {
        var yPos = 60f
        
        canvas.drawText("🎯 Advanced Pool Assistant", 30f, yPos, infoTextPaint.apply { 
            textSize = 20f
            color = Color.WHITE
        })
        yPos += 30f
        
        if (tableRect != null) {
            val status = if (isTableLocked) "Locked" else "Setup"
            canvas.drawText("Table: $status (${tableRect!!.width().toInt()}x${tableRect!!.height().toInt()})", 30f, yPos, infoTextPaint.apply { 
                color = if (isTableLocked) Color.GREEN else Color.parseColor("#FFA500")
                textSize = 16f
            })
            yPos += 25f
        }
        
        canvas.drawText("Pockets: ${pockets.size}/6", 30f, yPos, infoTextPaint.apply { 
            color = Color.GREEN
            textSize = 16f
        })
        yPos += 25f
        
        if (showCushionShots) {
            canvas.drawText("✅ Cushion Shots", 30f, yPos, infoTextPaint.apply { 
                color = Color.CYAN
                textSize = 16f
            })
        } else {
            canvas.drawText("❌ Cushion Shots", 30f, yPos, infoTextPaint.apply { 
                color = Color.GRAY
                textSize = 16f
            })
        }
        yPos += 25f
        
        val touchStatus = when {
            isResizeMode -> "Resize Mode"
            !isTableLocked -> "Setup Mode"
            isAiming -> "Aiming"
            else -> "Ready"
        }
        
        canvas.drawText("Mode: $touchStatus", 30f, yPos, infoTextPaint.apply { 
            color = Color.WHITE
            textSize = 16f
        })
        
        infoTextPaint.apply {
            textSize = 16f
            color = Color.WHITE
        }
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.d(TAG, "onAttachedToWindow: AdvancedPoolOverlay attached")
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.d(TAG, "onDetachedFromWindow: AdvancedPoolOverlay detached")
    }
}