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
import android.view.MotionEvent
import android.graphics.*
import android.content.Context
import kotlin.math.*

class MainActivity: FlutterActivity() {
    private val CHANNEL = "pool_overlay/system"
    private var overlayView: View? = null
    
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "hasOverlayPermission" -> {
                    result.success(hasOverlayPermission())
                }
                "requestOverlayPermission" -> {
                    requestOverlayPermission()
                    result.success(null)
                }
                "showOverlay" -> {
                    showSmartAimAssistant()
                    result.success(null)
                }
                "hideOverlay" -> {
                    hideSmartAimAssistant()
                    result.success(null)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }
    
    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }
    
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }
    
    private fun showSmartAimAssistant() {
        if (!hasOverlayPermission()) return
        
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Create invisible full-screen overlay for smart aim detection
        overlayView = SmartAimAssistant(this)
        
        val layoutParams = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            x = 0
            y = 0
            
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                   WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                   WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            
            format = PixelFormat.TRANSLUCENT
        }
        
        try {
            windowManager.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun hideSmartAimAssistant() {
        overlayView?.let { view ->
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            try {
                windowManager.removeView(view)
                overlayView = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

// Smart Aim Assistant - Seperti di gambar referensi
class SmartAimAssistant(context: Context) : View(context) {
    
    // Table detection
    private var tableLeft = 0f
    private var tableTop = 0f
    private var tableRight = 0f
    private var tableBottom = 0f
    
    // Aiming state
    private var currentAimStart: PointF? = null
    private var currentAimEnd: PointF? = null
    private var isDetectingAim = false
    
    // Paint untuk extended guidelines
    private val extendedGuidelinePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
        alpha = 220
    }
    
    // Paint untuk cushion shot guidelines
    private val cushionGuidelinePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        alpha = 180
    }
    
    // Paint untuk cue ball path prediction
    private val cueBallPredictionPaint = Paint().apply {
        color = Color.argb(150, 255, 255, 255)
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }
    
    init {
        // Detect table boundaries otomatis
        post {
            detectPoolTableBoundaries()
        }
        
        // Start aim detection
        startAimDetection()
    }
    
    private fun detectPoolTableBoundaries() {
        // Auto-detect berdasarkan layout umum 8 Ball Pool
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()
        
        // Margin standar untuk 8 Ball Pool di berbagai device
        tableLeft = screenWidth * 0.08f
        tableRight = screenWidth * 0.92f
        tableTop = screenHeight * 0.16f
        tableBottom = screenHeight * 0.84f
    }
    
    private fun startAimDetection() {
        // Simulasi deteksi aim line dari game
        // Dalam implementasi nyata, ini akan menggunakan image recognition
        simulateAimDetection()
    }
    
    private fun simulateAimDetection() {
        // Simulate continuous aim detection
        postDelayed({
            if (isAttachedToWindow) {
                // Deteksi aim line yang ada di game (simulasi)
                detectGameAimLine()
                invalidate()
                simulateAimDetection()
            }
        }, 50) // 20 FPS detection
    }
    
    private fun detectGameAimLine() {
        // Simulasi deteksi aim line dari 8 Ball Pool
        // Dalam implementasi nyata, ini menggunakan computer vision
        
        // Contoh: deteksi cue ball position dan aim direction
        val cueBallX = tableLeft + (tableRight - tableLeft) * 0.3f
        val cueBallY = tableTop + (tableBottom - tableTop) * 0.6f
        
        // Simulate aim direction berdasarkan input game
        val aimAngle = (System.currentTimeMillis() / 100.0) % (2 * PI)
        val aimLength = 200f
        
        val targetX = cueBallX + cos(aimAngle).toFloat() * aimLength
        val targetY = cueBallY + sin(aimAngle).toFloat() * aimLength
        
        currentAimStart = PointF(cueBallX, cueBallY)
        currentAimEnd = PointF(targetX, targetY)
        isDetectingAim = true
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw smart aim assistance jika detecting
        if (isDetectingAim && currentAimStart != null && currentAimEnd != null) {
            drawSmartAimAssistance(canvas)
        }
    }
    
    private fun drawSmartAimAssistance(canvas: Canvas) {
        val start = currentAimStart!!
        val end = currentAimEnd!!
        
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = sqrt(dx * dx + dy * dy)
        
        if (length < 10f) return
        
        val dirX = dx / length
        val dirY = dy / length
        
        // 1. Auto Extended Guideline
        drawAutoExtendedGuideline(canvas, start, dirX, dirY)
        
        // 2. Cushion Shot Guidelines  
        drawCushionShotGuidelines(canvas, start, dirX, dirY)
        
        // 3. Cue Ball Path Prediction
        drawCueBallPathPrediction(canvas, start, dirX, dirY)
        
        // 4. 3-Line Guidelines (premium feature simulation)
        draw3LineGuidelines(canvas, start, dirX, dirY)
    }
    
    private fun drawAutoExtendedGuideline(canvas: Canvas, start: PointF, dirX: Float, dirY: Float) {
        // Extended guideline yang panjang seperti di gambar referensi
        val extendedLength = 800f
        val endX = start.x + dirX * extendedLength
        val endY = start.y + dirY * extendedLength
        
        // Clamp ke table boundaries
        val clampedEnd = clampToTableBoundaries(start, PointF(endX, endY))
        
        canvas.drawLine(start.x, start.y, clampedEnd.x, clampedEnd.y, extendedGuidelinePaint)
    }
    
    private fun drawCushionShotGuidelines(canvas: Canvas, start: PointF, dirX: Float, dirY: Float) {
        // Calculate cushion shot reflections
        val reflections = calculateCushionReflections(start, dirX, dirY, 3)
        
        for (i in reflections.indices) {
            val segment = reflections[i]
            canvas.drawLine(
                segment.first.x, segment.first.y,
                segment.second.x, segment.second.y,
                cushionGuidelinePaint
            )
            
            // Draw reflection point
            if (i < reflections.size - 1) {
                canvas.drawCircle(segment.second.x, segment.second.y, 6f, cushionGuidelinePaint.apply {
                    style = Paint.Style.FILL
                })
                cushionGuidelinePaint.style = Paint.Style.STROKE
            }
        }
    }
    
    private fun drawCueBallPathPrediction(canvas: Canvas, start: PointF, dirX: Float, dirY: Float) {
        // Predict cue ball movement setelah impact
        val impactPoint = findFirstBallImpact(start, dirX, dirY)
        
        impactPoint?.let { impact ->
            // Simulate cue ball deflection setelah impact
            val deflectionAngle = PI / 6 // 30 degrees deflection
            val newDirX = cos(atan2(dirY.toDouble(), dirX.toDouble()) + deflectionAngle).toFloat()
            val newDirY = sin(atan2(dirY.toDouble(), dirX.toDouble()) + deflectionAngle).toFloat()
            
            val cueBallPathLength = 150f
            val cueBallEndX = impact.x + newDirX * cueBallPathLength
            val cueBallEndY = impact.y + newDirY * cueBallPathLength
            
            canvas.drawLine(impact.x, impact.y, cueBallEndX, cueBallEndY, cueBallPredictionPaint)
        }
    }
    
    private fun draw3LineGuidelines(canvas: Canvas, start: PointF, dirX: Float, dirY: Float) {
        // Premium 3-line guidelines untuk tembakan kompleks
        val lineSpacing = 30f
        
        // Main line (sudah digambar di extended guideline)
        // Parallel line 1
        val perpDirX = -dirY
        val perpDirY = dirX
        
        val line1StartX = start.x + perpDirX * lineSpacing
        val line1StartY = start.y + perpDirY * lineSpacing
        val line1EndX = line1StartX + dirX * 400f
        val line1EndY = line1StartY + dirY * 400f
        
        val thinLinePaint = Paint(extendedGuidelinePaint).apply {
            strokeWidth = 1f
            alpha = 100
        }
        
        canvas.drawLine(line1StartX, line1StartY, line1EndX, line1EndY, thinLinePaint)
        
        // Parallel line 2
        val line2StartX = start.x - perpDirX * lineSpacing
        val line2StartY = start.y - perpDirY * lineSpacing
        val line2EndX = line2StartX + dirX * 400f
        val line2EndY = line2StartY + dirY * 400f
        
        canvas.drawLine(line2StartX, line2StartY, line2EndX, line2EndY, thinLinePaint)
    }
    
    private fun calculateCushionReflections(start: PointF, dirX: Float, dirY: Float, maxReflections: Int): List<Pair<PointF, PointF>> {
        val segments = mutableListOf<Pair<PointF, PointF>>()
        
        var currentStart = start
        var currentDirX = dirX  
        var currentDirY = dirY
        
        for (i in 0 until maxReflections) {
            val wallHit = findWallIntersection(currentStart, currentDirX, currentDirY)
            
            if (wallHit != null) {
                segments.add(Pair(currentStart, wallHit.first))
                
                // Update untuk reflection berikutnya
                currentStart = wallHit.first
                val reflectedDir = wallHit.second
                currentDirX = reflectedDir.x
                currentDirY = reflectedDir.y
            } else {
                // No wall hit, extend ke infinity
                val farPoint = PointF(
                    currentStart.x + currentDirX * 500f,
                    currentStart.y + currentDirY * 500f
                )
                segments.add(Pair(currentStart, farPoint))
                break
            }
        }
        
        return segments
    }
    
    private fun findWallIntersection(start: PointF, dirX: Float, dirY: Float): Pair<PointF, PointF>? {
        var minT = Float.MAX_VALUE
        var hitPoint: PointF? = null
        var reflectedDir: PointF? = null
        
        // Check setiap dinding table
        if (dirX != 0f) {
            // Left wall
            if (dirX < 0) {
                val t = (tableLeft - start.x) / dirX
                if (t > 0) {
                    val y = start.y + dirY * t
                    if (y >= tableTop && y <= tableBottom && t < minT) {
                        minT = t
                        hitPoint = PointF(tableLeft, y)
                        reflectedDir = PointF(-dirX, dirY) // Reflect X
                    }
                }
            }
            // Right wall
            else {
                val t = (tableRight - start.x) / dirX
                if (t > 0) {
                    val y = start.y + dirY * t
                    if (y >= tableTop && y <= tableBottom && t < minT) {
                        minT = t
                        hitPoint = PointF(tableRight, y)
                        reflectedDir = PointF(-dirX, dirY) // Reflect X
                    }
                }
            }
        }
        
        if (dirY != 0f) {
            // Top wall
            if (dirY < 0) {
                val t = (tableTop - start.y) / dirY
                if (t > 0) {
                    val x = start.x + dirX * t
                    if (x >= tableLeft && x <= tableRight && t < minT) {
                        minT = t
                        hitPoint = PointF(x, tableTop)
                        reflectedDir = PointF(dirX, -dirY) // Reflect Y
                    }
                }
            }
            // Bottom wall  
            else {
                val t = (tableBottom - start.y) / dirY
                if (t > 0) {
                    val x = start.x + dirX * t
                    if (x >= tableLeft && x <= tableRight && t < minT) {
                        minT = t
                        hitPoint = PointF(x, tableBottom)
                        reflectedDir = PointF(dirX, -dirY) // Reflect Y
                    }
                }
            }
        }
        
        return if (hitPoint != null && reflectedDir != null) {
            Pair(hitPoint, reflectedDir)
        } else null
    }
    
    private fun clampToTableBoundaries(start: PointF, end: PointF): PointF {
        // Clamp end point ke dalam table boundaries
        val clampedX = end.x.coerceIn(tableLeft, tableRight)
        val clampedY = end.y.coerceIn(tableTop, tableBottom)
        return PointF(clampedX, clampedY)
    }
    
    private fun findFirstBallImpact(start: PointF, dirX: Float, dirY: Float): PointF? {
        // Simulasi impact dengan bola target
        // Dalam implementasi nyata, ini akan detect bola-bola di screen
        
        val impactDistance = 200f + (Math.random() * 100).toFloat()
        return PointF(
            start.x + dirX * impactDistance,
            start.y + dirY * impactDistance
        )
    }
}