package com.board36.app.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.board36.app.engine.KeyGeometry
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Vue du clavier : dessin des touches, gestion des appuis (dont appui long pour les
 * accents), glissement sur la barre d'espace et capture du tracé de saisie gestuelle.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onKey(code: Int, output: String?)
        fun onKeyPressFeedback(code: Int)
        /** Début d'un balayage vers la gauche depuis la touche Retour arrière. */
        fun onDeleteSwipeStart()
        /**
         * Progression du balayage d'effacement.
         * @param steps  crans parcourus vers la gauche
         * @param toEdge le doigt a atteint le bord gauche de l'écran
         */
        fun onDeleteSwipe(steps: Int, toEdge: Boolean)
        /** Fin du balayage : [commit] = effacer, sinon rétablir. */
        fun onDeleteSwipeEnd(commit: Boolean)
        fun onCursorDrag(steps: Int)
        fun onGestureStarted()
        fun onGestureFinished(px: FloatArray, py: FloatArray, count: Int, geo: KeyGeometry)
        fun onGestureCancelled()
        fun onShowPreview(key: Key, top: Float)
        fun onHidePreview()
        fun onShowAccents(key: Key, choices: List<String>, top: Float)
        fun onMoveAccents(x: Float, y: Float)
        /** @return le caractère choisi, ou null. */
        fun onHideAccents(commit: Boolean): String?
    }

    var listener: Listener? = null

    var theme: KeyboardTheme = KeyboardTheme.DARK
        set(v) { field = v; invalidate() }

    var gestureEnabled = true
    var previewEnabled = true
    var spaceCursorEnabled = true

    /** 0 = minuscules, 1 = majuscule ponctuelle, 2 = verrouillage majuscules. */
    var shiftState = 0
        set(v) { field = v; invalidate() }

    var spaceLabel: String = "français"

    /** Colorer les lettres qui composent le mot en cours d'écriture. */
    var highlightEnabled = true
    var highlightColor = 0xFFF2B84B.toInt()
        set(v) { field = v; invalidate() }

    /** Lettres (normalisées, a..z) du mot en cours. */
    var highlightedLetters: Set<Char> = emptySet()
        private set

    fun setHighlightedLetters(letters: Set<Char>) {
        if (letters != highlightedLetters) {
            highlightedLetters = letters
            invalidate()
        }
    }

    /**
     * Couleur du caractère d'une touche : une lettre déjà employée dans le mot en cours
     * se distingue des autres.
     */
    internal fun labelColor(k: Key): Int = when {
        k.style == KeyStyle.ACTION -> theme.actionText
        highlightEnabled && k.isLetter && k.label.length == 1 &&
            highlightedLetters.contains(k.label[0].lowercaseChar()) -> highlightColor
        else -> theme.text
    }

    private var layoutModel: KeyboardLayout? = null
    private val geometry = KeyGeometry()

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()
    private val path = Path()

    private val density = context.resources.displayMetrics.density
    private val cornerRadius = 8f * density
    private val hGap = 3f * density
    private val vGap = 5f * density

    private var pressedKey: Key? = null
    private var downX = 0f
    private var downY = 0f
    private var lastCursorX = 0f
    private var longPressFired = false
    private var accentsOpen = false
    private var spaceSliding = false
    private var deleteSwiping = false
    private var repeatFired = false
    private var activePointer = 0
    private var deleteSwipeSteps = -1
    private var deleteAtEdge = false

    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null
    private var longPressRunnable: Runnable? = null

    // Tracé de la saisie gestuelle
    private var gesturing = false
    private var gx = FloatArray(512)
    private var gy = FloatArray(512)
    private var gCount = 0

    private var lastShiftTap = 0L

    /** Hauteur imposée par le service (préférence de taille du clavier). */
    var desiredHeight = 0
        set(v) { field = v; requestLayout() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = if (desiredHeight > 0) desiredHeight else MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    fun setLayout(model: KeyboardLayout) {
        layoutModel = model
        requestLayout()
        applyGeometry()
        invalidate()
    }

    fun currentLayout(): KeyboardLayout? = layoutModel

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyGeometry()
    }

    private fun applyGeometry() {
        val model = layoutModel ?: return
        if (width == 0 || height == 0) return
        model.layout(width.toFloat(), height.toFloat(), hGap, vGap)
        geometry.clear()
        var kw = 0f
        var kh = 0f
        for (k in model.keys) {
            if (k.isLetter && k.label.length == 1) {
                geometry.set(k.label[0], k.centerX(), k.centerY())
                kw = k.w + hGap
                kh = k.h + vGap
            }
        }
        geometry.keyW = if (kw > 0f) kw else width / 10f
        geometry.keyH = if (kh > 0f) kh else height / 4f
    }

    fun geometry(): KeyGeometry = geometry

    // ---------------------------------------------------------------- dessin

    override fun onDraw(canvas: Canvas) {
        val model = layoutModel ?: return
        canvas.drawColor(theme.background)

        for (k in model.keys) {
            drawKey(canvas, k)
        }
        if (gesturing && gCount > 1) drawTrail(canvas)
    }

    private fun drawKey(canvas: Canvas, k: Key) {
        val pressed = k === pressedKey && !gesturing
        keyPaint.color = when (k.style) {
            KeyStyle.FUNCTION -> if (pressed) theme.functionKeyPressed else theme.functionKey
            KeyStyle.ACTION -> if (pressed) theme.actionKeyPressed else theme.actionKey
            KeyStyle.SPACE -> if (pressed) theme.keyPressed else theme.key
            KeyStyle.NORMAL -> if (pressed) theme.keyPressed else theme.key
        }
        if (k.code == Codes.SHIFT && shiftState > 0) {
            keyPaint.color = if (shiftState == 2) theme.accent else theme.functionKeyPressed
        }
        rect.set(k.x, k.y, k.x + k.w, k.y + k.h)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, keyPaint)

        val fg = if (k.style == KeyStyle.ACTION) theme.actionText else theme.text
        if (k.code == Codes.SHIFT && shiftState == 2) {
            // texte foncé sur fond accentué
            iconPaint.color = if (theme.dark) Color.BLACK else Color.WHITE
        } else {
            iconPaint.color = fg
        }

        if (k.icon != KeyIcon.NONE) {
            drawIcon(canvas, k)
            return
        }

        if (k.code == Codes.SPACE) {
            textPaint.color = theme.secondaryText
            textPaint.textSize = k.h * 0.26f
            textPaint.isFakeBoldText = false
            canvas.drawText(spaceLabel, k.centerX(), k.centerY() + textPaint.textSize / 3f, textPaint)
            return
        }

        var label = k.label
        if (k.isLetter && shiftState > 0) label = label.uppercase()
        textPaint.color = labelColor(k)
        textPaint.isFakeBoldText = false
        textPaint.textSize = when {
            k.style == KeyStyle.FUNCTION && label.length > 2 -> k.h * 0.28f
            label.length > 1 -> k.h * 0.32f
            k.isLetter -> k.h * 0.42f
            else -> k.h * 0.40f
        }
        canvas.drawText(label, k.centerX(), k.centerY() + textPaint.textSize * 0.36f, textPaint)

        if (k.hint.isNotEmpty()) {
            hintPaint.color = theme.secondaryText
            hintPaint.textSize = k.h * 0.22f
            canvas.drawText(k.hint, k.x + k.w * 0.78f, k.y + k.h * 0.30f, hintPaint)
        }
    }

    private fun drawIcon(canvas: Canvas, k: Key) {
        val cx = k.centerX()
        val cy = k.centerY()
        val s = minOf(k.w, k.h) * 0.30f
        iconPaint.strokeWidth = 2.1f * density
        iconPaint.style = Paint.Style.STROKE
        path.reset()
        when (k.icon) {
            KeyIcon.SHIFT -> {
                val filled = shiftState > 0
                path.moveTo(cx, cy - s)
                path.lineTo(cx + s, cy)
                path.lineTo(cx + s * 0.45f, cy)
                path.lineTo(cx + s * 0.45f, cy + s * 0.7f)
                path.lineTo(cx - s * 0.45f, cy + s * 0.7f)
                path.lineTo(cx - s * 0.45f, cy)
                path.lineTo(cx - s, cy)
                path.close()
                iconPaint.style = if (filled) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
                canvas.drawPath(path, iconPaint)
                if (shiftState == 2) {
                    canvas.drawLine(cx - s * 0.45f, cy + s * 0.95f, cx + s * 0.45f, cy + s * 0.95f, iconPaint)
                }
            }
            KeyIcon.DELETE -> {
                path.moveTo(cx - s * 1.15f, cy)
                path.lineTo(cx - s * 0.45f, cy - s * 0.75f)
                path.lineTo(cx + s * 1.05f, cy - s * 0.75f)
                path.lineTo(cx + s * 1.05f, cy + s * 0.75f)
                path.lineTo(cx - s * 0.45f, cy + s * 0.75f)
                path.close()
                canvas.drawPath(path, iconPaint)
                val d = s * 0.34f
                canvas.drawLine(cx + s * 0.05f, cy - d, cx + s * 0.65f, cy + d, iconPaint)
                canvas.drawLine(cx + s * 0.65f, cy - d, cx + s * 0.05f, cy + d, iconPaint)
            }
            KeyIcon.ENTER -> {
                path.moveTo(cx + s, cy - s * 0.8f)
                path.lineTo(cx + s, cy + s * 0.35f)
                path.lineTo(cx - s * 0.9f, cy + s * 0.35f)
                canvas.drawPath(path, iconPaint)
                path.reset()
                path.moveTo(cx - s * 0.3f, cy - s * 0.25f)
                path.lineTo(cx - s, cy + s * 0.35f)
                path.lineTo(cx - s * 0.3f, cy + s * 0.95f)
                canvas.drawPath(path, iconPaint)
            }
            else -> {}
        }
    }

    private fun drawTrail(canvas: Canvas) {
        val n = gCount
        val from = maxOf(1, n - TRAIL_POINTS)
        for (i in from until n) {
            val age = (i - from).toFloat() / maxOf(1, n - from)
            trailPaint.color = theme.trail
            trailPaint.alpha = (60 + 195 * age).toInt().coerceIn(0, 255)
            trailPaint.strokeWidth = (1.5f + 5.5f * age) * density
            canvas.drawLine(gx[i - 1], gy[i - 1], gx[i], gy[i], trailPaint)
        }
    }

    // ---------------------------------------------------------------- touches

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val model = layoutModel ?: return false
        val index = event.actionIndex

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointer = event.getPointerId(0)
                startPress(model, event.x, event.y)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // frappe rapide : un deuxième doigt valide la touche en cours
                if (gesturing || accentsOpen) return true
                pressedKey?.let { k ->
                    if (!longPressFired && !spaceSliding && !deleteSwiping && !repeatFired) emit(k)
                }
                cancelTimers()
                pressedKey = null
                activePointer = event.getPointerId(index)
                startPress(model, event.getX(index), event.getY(index))
            }

            MotionEvent.ACTION_MOVE -> {
                val i = event.findPointerIndex(activePointer)
                if (i < 0) return true
                handleMove(model, event.getX(i), event.getY(i))
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(index) != activePointer) return true
                finishPress(event.getX(index), event.getY(index))
                // le doigt encore posé reprend la main, sans re-valider de touche
                for (p in 0 until event.pointerCount) {
                    if (p != index) { activePointer = event.getPointerId(p); break }
                }
            }

            MotionEvent.ACTION_UP -> finishPress(event.x, event.y)

            MotionEvent.ACTION_CANCEL -> {
                cancelTimers()
                listener?.onHidePreview()
                if (deleteSwiping) { deleteSwiping = false; listener?.onDeleteSwipeEnd(false) }
                if (accentsOpen) { listener?.onHideAccents(false); accentsOpen = false }
                if (gesturing) { gesturing = false; listener?.onGestureCancelled() }
                pressedKey = null
                invalidate()
            }
        }
        return true
    }

    private fun startPress(model: KeyboardLayout, x: Float, y: Float) {
        cancelTimers()
        longPressFired = false
        spaceSliding = false
        deleteSwiping = false
        repeatFired = false
        downX = x; downY = y; lastCursorX = x
        val k = model.keyAt(x, y) ?: return
        pressedKey = k
        listener?.onKeyPressFeedback(k.code)
        if (previewEnabled && k.style == KeyStyle.NORMAL && k.icon == KeyIcon.NONE &&
            k.code != Codes.SPACE) {
            listener?.onShowPreview(k, top.toFloat())
        }
        if (k.repeatable) startRepeat(k)
        else if (k.popup.isNotEmpty()) startLongPress(k)
        invalidate()
    }

    private fun handleMove(model: KeyboardLayout, x: Float, y: Float) {
        if (accentsOpen) {
            listener?.onMoveAccents(x, y + top)
            return
        }
        if (gesturing) {
            addGesturePoint(x, y)
            invalidate()
            return
        }
        val k = pressedKey ?: return
        val dx = x - downX
        val dy = y - downY

        if (k.code == Codes.SPACE && spaceCursorEnabled && abs(dx) > SPACE_SLIDE_START * density) {
            spaceSliding = true
            cancelTimers()
            val step = geometry.keyW * 0.45f
            val steps = ((x - lastCursorX) / step).toInt()
            if (steps != 0) {
                listener?.onCursorDrag(steps)
                lastCursorX += steps * step
            }
            return
        }
        // Balayage vers la gauche depuis Retour arrière : plus le doigt va loin, plus on
        // efface ; au bord de l'écran, tout ce qui précède.
        if (k.code == Codes.DELETE && (deleteSwiping || dx < -geometry.keyW * 0.5f)) {
            if (!deleteSwiping) {
                deleteSwiping = true
                deleteSwipeSteps = -1
                deleteAtEdge = false
                cancelTimers()
                listener?.onDeleteSwipeStart()
            }
            val unit = geometry.keyW * 0.5f
            val steps = ((downX - x) / unit).toInt().coerceAtLeast(0)
            val edge = x < width * 0.07f
            if (steps != deleteSwipeSteps || edge != deleteAtEdge) {
                deleteSwipeSteps = steps
                deleteAtEdge = edge
                listener?.onDeleteSwipe(steps, edge)
            }
            return
        }
        if (deleteSwiping || spaceSliding) return

        if (gestureEnabled && k.isLetter && hypot(dx, dy) > geometry.keyW * 0.55f) {
            startGesture(downX, downY, x, y)
            return
        }
        // glissement d'une touche à l'autre
        val nk = model.keyAt(x, y)
        if (nk !== k && !longPressFired) {
            cancelTimers()
            pressedKey = nk
            if (nk != null) {
                if (previewEnabled && nk.style == KeyStyle.NORMAL && nk.icon == KeyIcon.NONE &&
                    nk.code != Codes.SPACE) listener?.onShowPreview(nk, top.toFloat())
                else listener?.onHidePreview()
                if (nk.repeatable) startRepeat(nk)
                else if (nk.popup.isNotEmpty()) startLongPress(nk)
            } else listener?.onHidePreview()
            invalidate()
        }
    }

    private fun finishPress(x: Float, y: Float) {
        cancelTimers()
        listener?.onHidePreview()
        if (deleteSwiping) {
            deleteSwiping = false
            pressedKey = null
            invalidate()
            listener?.onDeleteSwipeEnd(true)
            return
        }
        if (gesturing) {
            addGesturePoint(x, y)
            finishGesture()
            return
        }
        if (accentsOpen) {
            val chosen = listener?.onHideAccents(true)
            accentsOpen = false
            if (chosen != null) listener?.onKey(Codes.NONE, chosen)
            pressedKey = null
            invalidate()
            return
        }
        val k = pressedKey
        pressedKey = null
        invalidate()
        if (k == null || spaceSliding || deleteSwiping || longPressFired || repeatFired) return
        emit(k)
    }

    private fun emit(k: Key) {
        if (k.code == Codes.SHIFT) {
            val now = System.currentTimeMillis()
            shiftState = if (now - lastShiftTap < DOUBLE_TAP_MS) 2
            else if (shiftState == 0) 1 else 0
            lastShiftTap = now
            listener?.onKey(Codes.SHIFT, null)
            invalidate()
            return
        }
        if (k.code > 0) {
            var s = k.label
            if (k.isLetter && shiftState > 0) s = s.uppercase()
            listener?.onKey(k.code, s)
        } else {
            listener?.onKey(k.code, if (k.label.isNotEmpty()) k.label else null)
        }
    }

    private fun startLongPress(k: Key) {
        val r = Runnable {
            longPressFired = true
            val choices = k.popup.map { it.toString() }.toMutableList()
            val base = if (k.isLetter && shiftState > 0) k.label.uppercase() else k.label
            choices.add(0, base)
            val display = if (shiftState > 0 && k.isLetter) choices.map { it.uppercase() } else choices
            accentsOpen = true
            listener?.onHidePreview()
            listener?.onShowAccents(k, display, top.toFloat())
        }
        longPressRunnable = r
        handler.postDelayed(r, LONG_PRESS_MS)
    }

    private fun startRepeat(k: Key) {
        val r = object : Runnable {
            override fun run() {
                repeatFired = true
                listener?.onKey(k.code, null)
                handler.postDelayed(this, REPEAT_MS)
            }
        }
        repeatRunnable = r
        handler.postDelayed(r, REPEAT_START_MS)
    }

    private fun cancelTimers() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
        longPressRunnable = null
    }

    private fun startGesture(x0: Float, y0: Float, x: Float, y: Float) {
        cancelTimers()
        listener?.onHidePreview()
        gesturing = true
        gCount = 0
        addGesturePoint(x0, y0)
        addGesturePoint(x, y)
        listener?.onGestureStarted()
        invalidate()
    }

    private fun addGesturePoint(x: Float, y: Float) {
        if (gCount > 0) {
            val dx = x - gx[gCount - 1]
            val dy = y - gy[gCount - 1]
            if (dx * dx + dy * dy < MIN_POINT_DIST * MIN_POINT_DIST * density * density) return
        }
        if (gCount >= gx.size) {
            gx = gx.copyOf(gx.size * 2)
            gy = gy.copyOf(gy.size * 2)
        }
        gx[gCount] = x
        gy[gCount] = y
        gCount++
    }

    private fun finishGesture() {
        gesturing = false
        pressedKey = null
        val n = gCount
        invalidate()
        if (n >= 2) listener?.onGestureFinished(gx, gy, n, geometry)
        else listener?.onGestureCancelled()
        gCount = 0
    }

    fun cancelGesture() {
        if (gesturing) {
            gesturing = false
            gCount = 0
            invalidate()
        }
    }

    companion object {
        const val LONG_PRESS_MS = 330L
        const val REPEAT_START_MS = 380L
        const val REPEAT_MS = 52L
        const val DOUBLE_TAP_MS = 320L
        const val TRAIL_POINTS = 48
        const val MIN_POINT_DIST = 2.5f
        const val SPACE_SLIDE_START = 14f
    }
}
