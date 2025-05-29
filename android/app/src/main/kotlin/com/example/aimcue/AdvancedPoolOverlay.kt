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
        private const val TAG = "PoolOverlay_Simple"
        private const val PREFS_NAME = "simple_pool_settings"
        private const val KEY_TABLE_LEFT = "table_left"
        private const val KEY_TABLE_TOP = "table_top"
        private const val KEY_TABLE_RIGHT = "table_right"
        private const val KEY_TABLE_BOTTOM = "table_bottom"
        private const val KEY_IS_LOCKED = "is_locked"
    }
    
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    
    // Table properties
    private var tableRect: RectF? = null
    
    // Simple resize properties
    private var isResizeMode = false
    private var isTableLocked = true
    private var isDragging = false
    private var dragHandle = -1
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val cornerHandleRadius = 25f
    
    // Simple aiming properties
    private var aimStartPoint: PointF? = null
    private var aimEndPoint: PointF? = null
    private var isAiming = false
    
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Simple Paint objects
    private val tableBorderPaint = Paint().apply {
        color = Color.parseColor("#00FF00")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
        alpha = 120
    }
    
    private val tableResizePaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
        alpha = 180
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    
    private val handlePaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
        alpha = 200
    }
    
    private val handleBorderPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    
    // Guideline paint - Main trajectory
    private val guidelinePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
        alpha = 220
    }
    
    // Extended guideline paint - Longer projection
    private val extendedGuidelinePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
        alpha = 120
        pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
    }
    
    private val cueBallPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
        alpha = 180
    }
    
    private val cueBallBorderPaint = Paint().apply {
        color = Color.parseColor("#333333")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    
    private val buttonPaint = Paint().apply {
        color = Color.parseColor("#88000000")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val buttonTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 14f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    
    private val statusTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 16f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    
    init {
        Log.d(TAG, "Simple Pool Guideline initialized")
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
    fun getShotPower(): Float = 0.5f // Default value for compatibility
    fun getSpinX(): Float = 0f // Default value for compatibility
    fun getSpinY(): Float = 0f // Default value for compatibility
    
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
    
    // Compatibility methods - do nothing in simple version
    fun setShotPower(power: Float) { /* Not used in simple version */ }
    fun setSpin(x: Float, y: Float) { /* Not used in simple version */ }
    fun toggleCushionShots() { /* Not used in simple version */ }
    fun togglePowerIndicator() { /* Not used in simple version */ }
    
    private fun loadSettings() {
        val left = sharedPrefs.getFloat(KEY_TABLE_LEFT, -1f)
        val top = sharedPrefs.getFloat(KEY_TABLE_TOP, -1f)
        val right = sharedPrefs.getFloat(KEY_TABLE_RIGHT, -1f)
        val bottom = sharedPrefs.getFloat(KEY_TABLE_BOTTOM, -1f)
        isTableLocked = sharedPrefs.getBoolean(KEY_IS_LOCKED, true)
        
        if (left >= 0 && top >= 0 && right > left && bottom > top) {
            tableRect = RectF(left, top, right, bottom)
            Log.d(TAG, "loadSettings: Loaded saved table settings - $tableRect")
        } else {
            Log.d(TAG, "loadSettings: No saved table settings")
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
                apply()
            }
            Log.d(TAG, "saveSettings: Settings saved")
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
                
                // Check button touches
                if (isTouchOnButton(x, y, "resize")) {
                    toggleResizeMode()
                    return true
                }
                
                if (isTouchOnButton(x, y, "lock")) {
                    toggleTableLock()
                    return true
                }
                
                // Handle resize mode
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
                } 
                // Handle aiming mode
                else if (isTableLocked && !isResizeMode) {
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
                            invalidate()
                        }
                    }
                    
                    lastTouchX = x
                    lastTouchY = y
                    return true
                } else if (isAiming && aimStartPoint != null) {
                    aimEndPoint = PointF(x, y)
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
    
    private fun isTouchOnButton(x: Float, y: Float, buttonType: String): Boolean {
        val buttonWidth = 80f
        val buttonHeight = 30f
        val margin = 10f
        
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
        
        // Center handle for moving entire table
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
        
        // Draw table border
        tableRect?.let { table ->
            if (isResizeMode && !isTableLocked) {
                canvas.drawRect(table, tableResizePaint)
                drawResizeHandles(canvas, table)
            } else {
                canvas.drawRect(table, tableBorderPaint)
            }
        }
        
        // Draw simple guideline system
        if (isTableLocked && !isResizeMode) {
            drawSimpleGuideline(canvas)
        }
        
        // Draw simple control buttons
        drawSimpleButtons(canvas)
        
        // Draw simple status
        drawSimpleStatus(canvas)
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
        
        // Center handle
        val centerX = table.centerX()
        val centerY = table.centerY()
        canvas.drawCircle(centerX, centerY, 20f, handlePaint.apply { alpha = 120 })
        canvas.drawCircle(centerX, centerY, 20f, handleBorderPaint)
        
        handlePaint.alpha = 200
    }
    
    private fun drawSimpleGuideline(canvas: Canvas) {
        val start = aimStartPoint
        val end = aimEndPoint
        val table = tableRect
        
        if (start != null && end != null && table != null) {
            val distance = sqrt((end.x - start.x).pow(2) + (end.y - start.y).pow(2))
            if (distance > 20f) {
                
                // Draw cue ball
                canvas.drawCircle(start.x, start.y, 15f, cueBallPaint)
                canvas.drawCircle(start.x, start.y, 15f, cueBallBorderPaint)
                
                // Draw main guideline (like in 8 Ball Pool)
                canvas.drawLine(start.x, start.y, end.x, end.y, guidelinePaint)
                
                // Draw extended guideline (dashed line for longer projection)
                val dirX = (end.x - start.x) / distance
                val dirY = (end.y - start.y) / distance
                val extensionLength = 200f
                
                val extendedEndX = end.x + dirX * extensionLength
                val extendedEndY = end.y + dirY * extensionLength
                
                // Make sure extended line stays within screen bounds
                val clampedEndX = extendedEndX.coerceIn(0f, width.toFloat())
                val clampedEndY = extendedEndY.coerceIn(0f, height.toFloat())
                
                canvas.drawLine(end.x, end.y, clampedEndX, clampedEndY, extendedGuidelinePaint)
                
                // Draw small target circle at aim point
                canvas.drawCircle(end.x, end.y, 8f, guidelinePaint.apply { style = Paint.Style.FILL })
                canvas.drawCircle(end.x, end.y, 8f, cueBallBorderPaint.apply { style = Paint.Style.STROKE })
                
                // Reset paint styles
                guidelinePaint.style = Paint.Style.STROKE
                cueBallBorderPaint.style = Paint.Style.STROKE
            }
        }
    }
    
    private fun drawSimpleButtons(canvas: Canvas) {
        val buttonWidth = 80f
        val buttonHeight = 30f
        val margin = 10f
        val cornerRadius = 6f
        
        // Resize button
        val resizeButtonRect = RectF(
            width - buttonWidth - margin,
            height - buttonHeight * 2 - margin * 2,
            width - margin,
            height - buttonHeight - margin * 2
        )
        
        canvas.drawRoundRect(resizeButtonRect, cornerRadius, cornerRadius, buttonPaint)
        val resizeText = if (isResizeMode) "Exit" else "Resize"
        canvas.drawText(
            resizeText,
            resizeButtonRect.centerX(),
            resizeButtonRect.centerY() + buttonTextPaint.textSize / 3,
            buttonTextPaint
        )
        
        // Lock button
        val lockButtonRect = RectF(
            width - buttonWidth - margin,
            height - buttonHeight - margin,
            width - margin,
            height - margin
        )
        
        val lockButtonColor = if (isTableLocked) Color.parseColor("#AA00AA00") else Color.parseColor("#AAAA0000")
        buttonPaint.color = lockButtonColor
        canvas.drawRoundRect(lockButtonRect, cornerRadius, cornerRadius, buttonPaint)
        
        val lockText = if (isTableLocked) "🔒" else "🔓"
        canvas.drawText(
            lockText,
            lockButtonRect.centerX(),
            lockButtonRect.centerY() + buttonTextPaint.textSize / 3,
            buttonTextPaint
        )
        
        // Reset button color
        buttonPaint.color = Color.parseColor("#88000000")
    }
    
    private fun drawSimpleStatus(canvas: Canvas) {
        var yPos = 40f
        
        canvas.drawText("🎱 Pool Guideline", 20f, yPos, statusTextPaint.apply { 
            color = Color.WHITE
        })
        yPos += 25f
        
        if (tableRect != null) {
            val status = if (isTableLocked) "Ready" else "Setup"
            canvas.drawText("Status: $status", 20f, yPos, statusTextPaint.apply { 
                color = if (isTableLocked) Color.GREEN else Color.YELLOW
                textSize = 14f
            })
        }
        
        statusTextPaint.textSize = 16f
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.d(TAG, "onAttachedToWindow: Simple Pool Guideline attached")
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.d(TAG, "onDetachedFromWindow: Simple Pool Guideline detached")
    }
}