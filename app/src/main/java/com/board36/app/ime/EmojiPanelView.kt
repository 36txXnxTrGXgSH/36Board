package com.board36.app.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import com.board36.app.engine.Emoji

/**
 * Panneau des emoji : une rangée d'onglets, une grille qui défile, et une rangée basse
 * pour revenir aux lettres, insérer une espace et effacer.
 *
 * Comme le reste du clavier, tout est dessiné sur un [Canvas] : aucune vue enfant,
 * aucune ressource image, et les couleurs suivent le thème courant.
 */
class EmojiPanelView(context: Context) : View(context) {

    interface Listener {
        /** Un emoji a été choisi. */
        fun onEmoji(emoji: String)

        /** Touche de service de la rangée basse : [Codes.LETTERS], SPACE ou DELETE. */
        fun onEmojiKey(code: Int)

        /** Retour tactile (vibration, son), comme sur le clavier. */
        fun onEmojiFeedback(code: Int) {}
    }

    var listener: Listener? = null

    var theme: KeyboardTheme = KeyboardTheme.DARK
        set(v) { field = v; invalidate() }

    var desiredHeight = 0
        set(v) { field = v; requestLayout() }

    private val density = context.resources.displayMetrics.density
    private val tabHeight = 42f * density
    private val bottomHeight = 48f * density
    private val minCell = 44f * density

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()
    private val path = Path()

    /** Catalogue effectivement affichable, filtré une fois par la police du système. */
    private val filtered = HashMap<Int, List<String>>()

    private var category = 1
    private var scroll = 0f
    private var recents: List<String> = emptyList()

    private val scroller = OverScroller(context)
    private var velocity: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downY = 0f
    private var dragging = false
    private var pressedTab = -1
    private var pressedCell = -1
    private var pressedKey = 0

    private val handler = Handler(Looper.getMainLooper())
    private var repeat: Runnable? = null

    // ------------------------------------------------------------ contenu

    /** Emoji récemment utilisés, du plus récent au plus ancien. */
    fun setRecents(list: List<String>) {
        recents = list
        if (category == RECENTS_TAB) scroll = 0f
        invalidate()
    }

    fun recents(): List<String> = recents

    /** Catégorie affichée (0 = récents). */
    fun category(): Int = category

    fun setCategory(index: Int) {
        if (index !in Emoji.CATEGORIES.indices || index == category) return
        category = index
        scroll = 0f
        scroller.forceFinished(true)
        invalidate()
    }

    /** Emoji de la catégorie courante, dans l'ordre d'affichage. */
    fun items(): List<String> {
        if (category == RECENTS_TAB) return recents
        return filtered.getOrPut(category) {
            Emoji.availableIn(textPaint, Emoji.CATEGORIES[category].items)
        }
    }

    // ------------------------------------------------------------ géométrie

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = if (desiredHeight > 0) desiredHeight else MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    private fun columns(): Int = maxOf(6, (width / minCell).toInt())

    private fun cellSize(): Float = width / columns().toFloat()

    private fun gridTop(): Float = tabHeight

    private fun gridHeight(): Float = (height - tabHeight - bottomHeight).coerceAtLeast(0f)

    private fun contentHeight(): Float {
        val n = items().size
        if (n == 0) return 0f
        val rows = (n + columns() - 1) / columns()
        return rows * cellSize()
    }

    private fun maxScroll(): Float = (contentHeight() - gridHeight()).coerceAtLeast(0f)

    /** Indice de l'emoji sous le doigt, ou -1. */
    fun cellAt(x: Float, y: Float): Int {
        val top = gridTop()
        if (y < top || y >= top + gridHeight()) return -1
        val cell = cellSize()
        val col = (x / cell).toInt()
        if (col < 0 || col >= columns()) return -1
        val row = ((y - top + scroll) / cell).toInt()
        val index = row * columns() + col
        return if (index in items().indices) index else -1
    }

    private fun tabAt(x: Float, y: Float): Int {
        if (y >= tabHeight) return -1
        val w = width / Emoji.CATEGORIES.size.toFloat()
        return (x / w).toInt().coerceIn(0, Emoji.CATEGORIES.size - 1)
    }

    /** Rangée basse : ABC à gauche, espace au centre, effacement à droite. */
    private fun bottomKeyAt(x: Float, y: Float): Int {
        if (y < height - bottomHeight) return Codes.NONE
        val unit = width / 6f
        return when {
            x < unit * 1.5f -> Codes.LETTERS
            x > width - unit * 1.5f -> Codes.DELETE
            else -> Codes.SPACE
        }
    }

    /** Centre de l'onglet [index], pour les tests. */
    fun tabCenterX(index: Int): Float = width / Emoji.CATEGORIES.size.toFloat() * (index + 0.5f)

    /** Centre de la case [index] de la grille, pour les tests. */
    fun cellCenter(index: Int): Pair<Float, Float> {
        val cell = cellSize()
        val cols = columns()
        return (index % cols) * cell + cell / 2f to
            gridTop() + (index / cols) * cell - scroll + cell / 2f
    }

    // ------------------------------------------------------------ dessin

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        drawTabs(canvas)
        drawGrid(canvas)
        drawBottom(canvas)
    }

    private fun drawTabs(canvas: Canvas) {
        val w = width / Emoji.CATEGORIES.size.toFloat()
        textPaint.textSize = tabHeight * 0.46f
        textPaint.color = theme.text
        val cy = tabHeight / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        for (i in Emoji.CATEGORIES.indices) {
            val left = i * w
            if (i == pressedTab) {
                bgPaint.color = theme.keyPressed
                rect.set(left + 2f, 3f, left + w - 2f, tabHeight - 3f)
                canvas.drawRoundRect(rect, 6f * density, 6f * density, bgPaint)
            }
            canvas.drawText(Emoji.CATEGORIES[i].icon, left + w / 2f, cy, textPaint)
            if (i == category) {
                bgPaint.color = theme.accent
                rect.set(left + w * 0.22f, tabHeight - 3f * density,
                    left + w * 0.78f, tabHeight - 0.5f * density)
                canvas.drawRoundRect(rect, 2f * density, 2f * density, bgPaint)
            }
        }
        linePaint.color = if (theme.dark) 0x22FFFFFF else 0x22000000
        canvas.drawRect(0f, tabHeight, width.toFloat(), tabHeight + 1f, linePaint)
    }

    private fun drawGrid(canvas: Canvas) {
        val list = items()
        val top = gridTop()
        val h = gridHeight()

        if (list.isEmpty()) {
            textPaint.textSize = 13f * density
            textPaint.color = theme.secondaryText
            val message = if (category == RECENTS_TAB) {
                "Les emoji utilisés apparaîtront ici."
            } else {
                "Rien à afficher."
            }
            canvas.drawText(message, width / 2f, top + h / 2f, textPaint)
            return
        }

        val cell = cellSize()
        val cols = columns()
        textPaint.textSize = cell * 0.62f
        textPaint.color = theme.text
        val baseline = cell / 2f - (textPaint.ascent() + textPaint.descent()) / 2f

        canvas.save()
        canvas.clipRect(0f, top, width.toFloat(), top + h)
        // seules les rangées visibles sont dessinées : la grille compte plus de mille cases
        val first = ((scroll / cell).toInt() * cols).coerceIn(0, list.size - 1)
        val last = (first + ((h / cell).toInt() + 2) * cols).coerceAtMost(list.size - 1)
        for (i in first..last) {
            val x = (i % cols) * cell
            val y = top + (i / cols) * cell - scroll
            if (i == pressedCell) {
                bgPaint.color = theme.keyPressed
                rect.set(x + 2f, y + 2f, x + cell - 2f, y + cell - 2f)
                canvas.drawRoundRect(rect, 6f * density, 6f * density, bgPaint)
            }
            canvas.drawText(list[i], x + cell / 2f, y + baseline, textPaint)
        }
        canvas.restore()
    }

    private fun drawBottom(canvas: Canvas) {
        val top = height - bottomHeight
        linePaint.color = if (theme.dark) 0x22FFFFFF else 0x22000000
        canvas.drawRect(0f, top, width.toFloat(), top + 1f, linePaint)

        val unit = width / 6f
        val cy = top + bottomHeight / 2f

        drawKeyBackground(canvas, 0f, top, unit * 1.5f, Codes.LETTERS)
        textPaint.textSize = bottomHeight * 0.34f
        textPaint.color = theme.text
        canvas.drawText("ABC", unit * 0.75f,
            cy - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)

        drawKeyBackground(canvas, unit * 1.5f, top, width - unit * 1.5f, Codes.SPACE)
        linePaint.color = theme.secondaryText
        canvas.drawRect(width / 2f - unit * 0.6f, cy - 1f * density,
            width / 2f + unit * 0.6f, cy + 1f * density, linePaint)

        drawKeyBackground(canvas, width - unit * 1.5f, top, width.toFloat(), Codes.DELETE)
        drawDelete(canvas, width - unit * 0.75f, cy, bottomHeight * 0.22f)
    }

    private fun drawKeyBackground(canvas: Canvas, left: Float, top: Float, right: Float, code: Int) {
        if (pressedKey != code) return
        bgPaint.color = theme.keyPressed
        rect.set(left + 3f, top + 4f, right - 3f, height - 4f)
        canvas.drawRoundRect(rect, 8f * density, 8f * density, bgPaint)
    }

    /** Flèche d'effacement, dessinée comme celle du clavier. */
    private fun drawDelete(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        iconPaint.color = theme.text
        iconPaint.strokeWidth = 1.9f * density
        val left = cx - s * 1.5f
        val right = cx + s * 1.2f
        path.reset()
        path.moveTo(left, cy)
        path.lineTo(left + s, cy - s)
        path.lineTo(right, cy - s)
        path.lineTo(right, cy + s)
        path.lineTo(left + s, cy + s)
        path.close()
        canvas.drawPath(path, iconPaint)
        val c = s * 0.42f
        val mx = cx + s * 0.15f
        canvas.drawLine(mx - c, cy - c, mx + c, cy + c, iconPaint)
        canvas.drawLine(mx + c, cy - c, mx - c, cy + c, iconPaint)
    }

    // ------------------------------------------------------------ touches

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(event) }
                downY = event.y
                dragging = false
                pressedTab = tabAt(event.x, event.y)
                pressedCell = if (pressedTab < 0) cellAt(event.x, event.y) else -1
                pressedKey = if (pressedTab < 0 && pressedCell < 0) {
                    bottomKeyAt(event.x, event.y)
                } else {
                    Codes.NONE
                }
                if (pressedTab >= 0 || pressedCell >= 0 || pressedKey != Codes.NONE) {
                    listener?.onEmojiFeedback(pressedKey)
                }
                // l'effacement part tout de suite et se répète : c'est la touche qu'on
                // maintient enfoncée pour effacer plusieurs emoji d'affilée
                if (pressedKey == Codes.DELETE) startRepeat()
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(event)
                if (!dragging && pressedTab < 0 && pressedKey == Codes.NONE &&
                    kotlin.math.abs(event.y - downY) > touchSlop
                ) {
                    dragging = true
                    pressedCell = -1
                }
                if (dragging) {
                    scroll = (scroll - (event.y - downY)).coerceIn(0f, maxScroll())
                    downY = event.y
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                stopRepeat()
                if (dragging) {
                    velocity?.let {
                        it.addMovement(event)
                        it.computeCurrentVelocity(1000)
                        fling(-it.yVelocity)
                    }
                } else {
                    val tab = tabAt(event.x, event.y)
                    val cell = cellAt(event.x, event.y)
                    val key = bottomKeyAt(event.x, event.y)
                    when {
                        tab >= 0 && tab == pressedTab -> setCategory(tab)
                        cell >= 0 && cell == pressedCell -> listener?.onEmoji(items()[cell])
                        // l'effacement a déjà eu lieu à l'appui
                        key == pressedKey && key != Codes.NONE && key != Codes.DELETE ->
                            listener?.onEmojiKey(key)
                    }
                }
                release()
            }

            MotionEvent.ACTION_CANCEL -> {
                stopRepeat()
                release()
            }
        }
        return true
    }

    private fun release() {
        pressedTab = -1
        pressedCell = -1
        pressedKey = Codes.NONE
        dragging = false
        velocity?.recycle()
        velocity = null
        invalidate()
    }

    private fun fling(velocityY: Float) {
        val max = maxScroll().toInt()
        if (max <= 0 || kotlin.math.abs(velocityY) < MIN_FLING) return
        scroller.fling(0, scroll.toInt(), 0, velocityY.toInt(), 0, 0, 0, max)
        postInvalidateOnAnimation()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scroll = scroller.currY.toFloat().coerceIn(0f, maxScroll())
            postInvalidateOnAnimation()
        }
    }

    private fun startRepeat() {
        stopRepeat()
        listener?.onEmojiKey(Codes.DELETE)
        val r = object : Runnable {
            override fun run() {
                listener?.onEmojiKey(Codes.DELETE)
                listener?.onEmojiFeedback(Codes.DELETE)
                handler.postDelayed(this, REPEAT_MS)
            }
        }
        repeat = r
        handler.postDelayed(r, FIRST_REPEAT_MS)
    }

    private fun stopRepeat() {
        repeat?.let { handler.removeCallbacks(it) }
        repeat = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRepeat()
    }

    companion object {
        /** Onglet des emoji récemment utilisés. */
        const val RECENTS_TAB = 0
        private const val FIRST_REPEAT_MS = 350L
        private const val REPEAT_MS = 60L
        private const val MIN_FLING = 200f
    }
}
