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
        private const val TAG = "PoolOverlay_Working"
        private const val PREFS_NAME = "pool_settings"
    }
    
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    
    // Table properties
    private var tableRect: RectF = RectF()
    private var isResizeMode = true // Start in resize mode
    private var isTableLocked = false
    
    // Touch handling
    private var isDragging = false
    private var dragType = 0 // 0=none, 1=corner, 2=move, 3=aim
    private var dragCorner = -1
    private var lastX = 0f
    private var lastY = 0f
    
    // Aiming
    private var aimStart: PointF? = null
    private var aimEnd: PointF? = null
    
    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Paints - simplified
    private val tablePaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    
    private val resizePaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 5f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(20f, 20f), 0f)
    }
    
    private val handlePaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 16f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    
    private val buttonPaint = Paint().apply {
        color = Color.parseColor("#CC000000")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val linePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    
    init {
        Log.d(TAG, "AdvancedPoolOverlay created")
        loadTable()
    }
    
    fun setWindowManager(wm: WindowManager, params: WindowManager.LayoutParams) {
        windowManager = wm
        layoutParams = params
        updateWindowFlags()
        Log.d(TAG, "Window manager set")
    }
    
    private fun updateWindowFlags() {
        layoutParams?.let { params ->
            windowManager?.let { wm ->
                // CRITICAL FIX: Use correct flags for overlay touch handling
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                              WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                              WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                
                // DO NOT use FLAG_NOT_TOUCHABLE - this blocks all touch!
                
                try {
                    wm.updateViewLayout(this, params)
                    Log.d(TAG, "Window flags updated - touch should work now")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating window flags", e)
                }
            }
        }
    }
    
    // Public interface for Flutter
    fun toggleResizeModeFromFlutter() {
        isResizeMode = !isResizeMode
        Log.d(TAG, "Resize mode toggled: $isResizeMode")
        updateWindowFlags() // Update window flags when mode changes
        invalidate()
    }
    
    fun toggleTableLockFromFlutter() {
        isTableLocked = !isTableLocked
        if (isTableLocked) {
            isResizeMode = false
            saveTable()
        }
        Log.d(TAG, "Table lock toggled: $isTableLocked")
        updateWindowFlags() // Update window flags when mode changes
        invalidate()
    }
    
    fun isInResizeMode(): Boolean = isResizeMode
    fun isTableLocked(): Boolean = isTableLocked
    fun getShotPower(): Float = 0.5f
    fun getSpinX(): Float = 0f
    fun getSpinY(): Float = 0f
    
    fun getTableRect(): Map<String, Float> {
        return mapOf(
            "left" to tableRect.left,
            "top" to tableRect.top,
            "right" to tableRect.right,
            "bottom" to tableRect.bottom,
            "width" to tableRect.width(),
            "height" to tableRect.height()
        )
    }
    
    // Compatibility methods
    fun setShotPower(power: Float) {}
    fun setSpin(x: Float, y: Float) {}
    fun toggleCushionShots() {}
    fun togglePowerIndicator() {}
    
    private fun loadTable() {
        val left = sharedPrefs.getFloat("left", -1f)
        val top = sharedPrefs.getFloat("top", -1f)
        val right = sharedPrefs.getFloat("right", -1f)
        val bottom = sharedPrefs.getFloat("bottom", -1f)
        
        if (left > 0 && top > 0 && right > left && bottom > top) {
            tableRect.set(left, top, right, bottom)
            isTableLocked = sharedPrefs.getBoolean("locked", false)
            isResizeMode = !isTableLocked
            Log.d(TAG, "Loaded saved table: $tableRect")
        } else {
            // Create default table when view is ready
            post {
                createDefaultTable()
            }
        }
    }
    
    private fun createDefaultTable() {
        if (width > 0 && height > 0) {
            val w = width * 0.6f
            val h = w * 0.4f
            val cx = width / 2f
            val cy = height / 2f
            
            tableRect.set(cx - w/2, cy - h/2, cx + w/2, cy + h/2)
            isResizeMode = true
            isTableLocked = false
            
            Log.d(TAG, "Created default table: ${tableRect.width()}x${tableRect.height()}")
            invalidate()
        }
    }
    
    private fun saveTable() {
        sharedPrefs.edit().apply {
            putFloat("left", tableRect.left)
            putFloat("top", tableRect.top)
            putFloat("right", tableRect.right)
            putFloat("bottom", tableRect.bottom)
            putBoolean("locked", isTableLocked)
            apply()
        }
        Log.d(TAG, "Table saved")
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        
        // CRITICAL: First check if touch is in any of our interactive areas
        val isInOurArea = isInInteractiveArea(x, y)
        
        Log.d(TAG, "Touch: ${getActionName(event.action)} at ($x, $y) - InOurArea: $isInOurArea")
        
        // If touch is NOT in our area, immediately pass through
        if (!isInOurArea && event.action == MotionEvent.ACTION_DOWN) {
            Log.d(TAG, "Touch outside our areas - passing through to game")
            return false
        }
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Only handle if in our interactive areas
                
                // Check buttons first
                if (checkButtonTouch(x, y)) {
                    Log.d(TAG, "Button touched - handled by overlay")
                    return true
                }
                
                // Handle resize touches ONLY if in resize mode
                if (isResizeMode) {
                    if (handleResizeTouch(x, y)) {
                        Log.d(TAG, "Resize handle touched - handled by overlay")
                        return true
                    }
                }
                
                // Handle aim touches ONLY if table is locked and not resizing
                if (isTableLocked && !isResizeMode) {
                    if (handleAimTouch(x, y)) {
                        Log.d(TAG, "Aim started - handled by overlay")
                        return true
                    }
                }
                
                // Even if in our area but not a specific control, pass through
                Log.d(TAG, "In our area but not specific control - passing through")
                return false
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    Log.d(TAG, "Dragging - handled by overlay")
                    return handleDragMove(x, y)
                }
                return false
            }
            
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                    dragType = 0
                    dragCorner = -1
                    if (isResizeMode) {
                        saveTable()
                    }
                    Log.d(TAG, "Drag ended - handled by overlay")
                    return true
                }
                return false
            }
        }
        
        return false
    }
    
    private fun isInInteractiveArea(x: Float, y: Float): Boolean {
        // Check if touch is in any of our interactive areas
        
        // Button areas
        val btnW = 120f
        val btnH = 50f
        val margin = 20f
        
        val resizeBtn = RectF(
            width - btnW - margin,
            height - btnH * 2 - margin * 2,
            width - margin,
            height - btnH - margin * 2
        )
        
        val lockBtn = RectF(
            width - btnW - margin,
            height - btnH - margin,
            width - margin,
            height - margin
        )
        
        if (resizeBtn.contains(x, y) || lockBtn.contains(x, y)) {
            return true
        }
        
        // Resize handle areas (only if in resize mode)
        if (isResizeMode) {
            val corners = arrayOf(
                PointF(tableRect.left, tableRect.top),
                PointF(tableRect.right, tableRect.top),
                PointF(tableRect.left, tableRect.bottom),
                PointF(tableRect.right, tableRect.bottom)
            )
            
            // Check corner handles
            for (corner in corners) {
                val dist = sqrt((x - corner.x).pow(2) + (y - corner.y).pow(2))
                if (dist <= 100f) {
                    return true
                }
            }
            
            // Check center handle
            val centerDist = sqrt((x - tableRect.centerX()).pow(2) + (y - tableRect.centerY()).pow(2))
            if (centerDist <= 80f) {
                return true
            }
        }
        
        // Aim area (only if locked and not resizing)
        if (isTableLocked && !isResizeMode) {
            if (tableRect.contains(x, y)) {
                return true
            }
        }
        
        return false
    }
    
    private fun checkButtonTouch(x: Float, y: Float): Boolean {
        val btnW = 120f
        val btnH = 50f
        val margin = 20f
        
        // Resize button
        val resizeBtn = RectF(
            width - btnW - margin,
            height - btnH * 2 - margin * 2,
            width - margin,
            height - btnH - margin * 2
        )
        
        // Lock button
        val lockBtn = RectF(
            width - btnW - margin,
            height - btnH - margin,
            width - margin,
            height - margin
        )
        
        if (resizeBtn.contains(x, y)) {
            toggleResizeModeFromFlutter()
            return true
        }
        
        if (lockBtn.contains(x, y)) {
            toggleTableLockFromFlutter()
            return true
        }
        
        return false
    }
    
    private fun handleResizeTouch(x: Float, y: Float): Boolean {
        // Check corners first (with precise touch area)
        val corners = arrayOf(
            PointF(tableRect.left, tableRect.top),      // 0
            PointF(tableRect.right, tableRect.top),     // 1
            PointF(tableRect.left, tableRect.bottom),   // 2
            PointF(tableRect.right, tableRect.bottom)   // 3
        )
        
        // Check each corner handle
        for (i in corners.indices) {
            val dist = sqrt((x - corners[i].x).pow(2) + (y - corners[i].y).pow(2))
            Log.d(TAG, "Corner $i distance: $dist (threshold: 100)")
            if (dist <= 100f) { // Touch area radius
                isDragging = true
                dragType = 1
                dragCorner = i
                lastX = x
                lastY = y
                Log.d(TAG, "Started dragging corner $i")
                return true
            }
        }
        
        // Check center handle (move whole table)
        val centerX = tableRect.centerX()
        val centerY = tableRect.centerY()
        val centerDist = sqrt((x - centerX).pow(2) + (y - centerY).pow(2))
        Log.d(TAG, "Center distance: $centerDist (threshold: 80)")
        
        if (centerDist <= 80f) {
            isDragging = true
            dragType = 2
            lastX = x
            lastY = y
            Log.d(TAG, "Started moving table")
            return true
        }
        
        // Touch is not on any handle
        Log.d(TAG, "Touch not on any resize handle")
        return false
    }
    
    private fun handleAimTouch(x: Float, y: Float): Boolean {
        if (tableRect.contains(x, y)) {
            aimStart = PointF(x, y)
            aimEnd = PointF(x, y)
            isDragging = true
            dragType = 3
            Log.d(TAG, "Started aiming")
            return true
        }
        return false
    }
    
    private fun handleDragMove(x: Float, y: Float): Boolean {
        val dx = x - lastX
        val dy = y - lastY
        
        when (dragType) {
            1 -> { // Corner resize
                val newRect = RectF(tableRect)
                when (dragCorner) {
                    0 -> { newRect.left += dx; newRect.top += dy }
                    1 -> { newRect.right += dx; newRect.top += dy }
                    2 -> { newRect.left += dx; newRect.bottom += dy }
                    3 -> { newRect.right += dx; newRect.bottom += dy }
                }
                
                // Validate new size
                if (newRect.width() > 100 && newRect.height() > 50 &&
                    newRect.left >= 0 && newRect.top >= 0 &&
                    newRect.right <= width && newRect.bottom <= height) {
                    tableRect.set(newRect)
                    invalidate()
                }
            }
            
            2 -> { // Move table
                val newRect = RectF(tableRect)
                newRect.offset(dx, dy)
                
                if (newRect.left >= 0 && newRect.top >= 0 &&
                    newRect.right <= width && newRect.bottom <= height) {
                    tableRect.set(newRect)
                    invalidate()
                }
            }
            
            3 -> { // Aiming
                aimEnd = PointF(x, y)
                invalidate()
            }
        }
        
        lastX = x
        lastY = y
        return true
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Create table if needed
        if (tableRect.isEmpty && width > 0 && height > 0) {
            createDefaultTable()
            return
        }
        
        // Draw debug background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
            color = Color.parseColor("#11FF0000")
            style = Paint.Style.FILL
        })
        
        // Draw table
        if (isResizeMode) {
            canvas.drawRect(tableRect, resizePaint)
            drawResizeHandles(canvas)
        } else {
            canvas.drawRect(tableRect, tablePaint)
        }
        
        // Draw guideline
        if (!isResizeMode && isTableLocked) {
            drawGuideline(canvas)
        }
        
        // Draw buttons
        drawButtons(canvas)
        
        // Draw status
        drawStatus(canvas)
    }
    
    private fun drawResizeHandles(canvas: Canvas) {
        val corners = arrayOf(
            PointF(tableRect.left, tableRect.top),
            PointF(tableRect.right, tableRect.top),
            PointF(tableRect.left, tableRect.bottom),
            PointF(tableRect.right, tableRect.bottom)
        )
        
        // Draw touch areas (light)
        corners.forEachIndexed { i, corner ->
            canvas.drawCircle(corner.x, corner.y, 100f, Paint().apply {
                color = Color.parseColor("#33FF0000")
                style = Paint.Style.FILL
            })
        }
        
        // Draw handles
        corners.forEachIndexed { i, corner ->
            canvas.drawCircle(corner.x, corner.y, 25f, handlePaint)
            canvas.drawCircle(corner.x, corner.y, 25f, Paint().apply {
                color = Color.WHITE
                strokeWidth = 3f
                style = Paint.Style.STROKE
                isAntiAlias = true
            })
            
            // Draw corner number
            canvas.drawText(i.toString(), corner.x - 5f, corner.y + 5f, textPaint)
        }
        
        // Draw center handle
        canvas.drawCircle(tableRect.centerX(), tableRect.centerY(), 80f, Paint().apply {
            color = Color.parseColor("#330000FF")
            style = Paint.Style.FILL
        })
        
        canvas.drawCircle(tableRect.centerX(), tableRect.centerY(), 20f, Paint().apply {
            color = Color.BLUE
            style = Paint.Style.FILL
            isAntiAlias = true
        })
        
        canvas.drawText("MOVE", tableRect.centerX() - 25f, tableRect.centerY() + 35f, textPaint)
    }
    
    private fun drawGuideline(canvas: Canvas) {
        val start = aimStart
        val end = aimEnd
        
        if (start != null && end != null) {
            val dist = sqrt((end.x - start.x).pow(2) + (end.y - start.y).pow(2))
            if (dist > 20f) {
                // Draw cue ball
                canvas.drawCircle(start.x, start.y, 15f, Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                    isAntiAlias = true
                })
                
                // Draw line
                canvas.drawLine(start.x, start.y, end.x, end.y, linePaint)
                
                // Draw target
                canvas.drawCircle(end.x, end.y, 8f, Paint().apply {
                    color = Color.YELLOW
                    style = Paint.Style.FILL
                    isAntiAlias = true
                })
            }
        }
    }
    
    private fun drawButtons(canvas: Canvas) {
        val btnW = 120f
        val btnH = 50f
        val margin = 20f
        
        // Resize button
        val resizeBtn = RectF(
            width - btnW - margin,
            height - btnH * 2 - margin * 2,
            width - margin,
            height - btnH - margin * 2
        )
        
        canvas.drawRoundRect(resizeBtn, 10f, 10f, buttonPaint)
        canvas.drawText(
            if (isResizeMode) "EXIT" else "RESIZE",
            resizeBtn.centerX() - 25f,
            resizeBtn.centerY() + 5f,
            textPaint
        )
        
        // Lock button
        val lockBtn = RectF(
            width - btnW - margin,
            height - btnH - margin,
            width - margin,
            height - margin
        )
        
        val lockColor = if (isTableLocked) Color.parseColor("#CC00AA00") else Color.parseColor("#CCAA0000")
        canvas.drawRoundRect(lockBtn, 10f, 10f, Paint().apply {
            color = lockColor
            style = Paint.Style.FILL
            isAntiAlias = true
        })
        
        canvas.drawText(
            if (isTableLocked) "LOCKED" else "SETUP",
            lockBtn.centerX() - 30f,
            lockBtn.centerY() + 5f,
            textPaint
        )
    }
    
    private fun drawStatus(canvas: Canvas) {
        val status = when {
            isResizeMode -> "RESIZE MODE - Drag handles to adjust"
            !isTableLocked -> "SETUP MODE - Lock when ready"
            else -> "READY - Tap & drag to aim"
        }
        
        canvas.drawText(status, 20f, 50f, textPaint)
        canvas.drawText("Table: ${tableRect.width().toInt()}x${tableRect.height().toInt()}", 20f, 80f, textPaint)
        canvas.drawText("Touch Enabled - Overlay Working", 20f, height - 30f, textPaint.apply {
            color = Color.GREEN
        })
        textPaint.color = Color.WHITE
    }
    
    private fun getActionName(action: Int): String {
        return when (action) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_MOVE -> "MOVE"
            MotionEvent.ACTION_UP -> "UP"
            else -> "OTHER"
        }
    }
}