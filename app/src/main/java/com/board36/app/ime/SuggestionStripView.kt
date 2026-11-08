package com.board36.app.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * Bande au-dessus du clavier : les propositions au centre, et de part et d'autre les
 * deux seuls boutons du clavier — les emoji à gauche, les réglages à droite.
 *
 * Ils sont posés aux extrémités plutôt que dans un sous-menu : l'emoji sert à chaque
 * message, il doit rester à un doigt. Couper, copier et coller ne sont plus là — Android
 * les propose déjà par un appui long dans le texte, à l'endroit exact où l'on édite.
 */
class SuggestionStripView(context: Context) : View(context) {

    interface Listener {
        fun onPickSuggestion(index: Int, word: String)
        fun onToolbarAction(action: Int)
        /** L'utilisateur a demandé que ce mot ne soit plus jamais proposé. */
        fun onBlockWord(word: String) {}
    }

    object Tool {
        const val EMOJI = 1
        const val SETTINGS = 2
    }

    var listener: Listener? = null
    var theme: KeyboardTheme = KeyboardTheme.DARK
        set(v) { field = v; invalidate() }

    private var suggestions: List<String> = emptyList()
    /** Indice de la proposition qui sera appliquée automatiquement (mise en avant). */
    private var highlighted = -1

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val density = context.resources.displayMetrics.density
    private var pressedSlot = -1

    /** Mot dont on propose le blocage (appui long sur une proposition). */
    private var blockPrompt: String? = null
    private val yesRect = RectF()
    private val noRect = RectF()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var longPress: Runnable? = null
    private var downX = 0f
    /**
     * Le doigt qui a déclenché l'appui long est encore posé : son relâchement ne doit pas
     * refermer la question qu'il vient d'ouvrir — sauf s'il se relève sur un des boutons.
     */
    private var promptPending = false
    private var promptTimeout: Runnable? = null

    fun setSuggestions(words: List<String>, highlight: Int) {
        suggestions = words
        highlighted = highlight
        invalidate()
    }

    /** Propositions actuellement affichées. */
    fun suggestions(): List<String> = suggestions

    fun clear() {
        suggestions = emptyList()
        highlighted = -1
        invalidate()
    }

    /** Affiche « Ne plus proposer ce mot ? » à la place des propositions. */
    fun showBlockPrompt(word: String) {
        blockPrompt = word
        layoutPromptButtons()
        armPromptTimeout()
        invalidate()
    }

    /** La question ne reste pas indéfiniment en travers de la bande. */
    private fun armPromptTimeout() {
        cancelPromptTimeout()
        val r = Runnable { hideBlockPrompt() }
        promptTimeout = r
        handler.postDelayed(r, PROMPT_TIMEOUT_MS)
    }

    private fun cancelPromptTimeout() {
        promptTimeout?.let { handler.removeCallbacks(it) }
        promptTimeout = null
    }

    /**
     * Position des deux boutons de la demande. Calculée ici et non au moment du dessin :
     * un appui doit être reconnu même si la vue n'a pas encore été redessinée.
     */
    private fun layoutPromptButtons() {
        val h = height.toFloat()
        val size = h * 0.62f
        noRect.set(width - size - 8f * density, (h - size) / 2f,
            width - 8f * density, (h + size) / 2f)
        yesRect.set(noRect.left - size - 8f * density, noRect.top,
            noRect.left - 8f * density, noRect.bottom)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (blockPrompt != null) layoutPromptButtons()
    }

    fun hideBlockPrompt() {
        promptPending = false
        cancelPromptTimeout()
        if (blockPrompt != null) { blockPrompt = null; invalidate() }
    }

    fun blockPromptWord(): String? = blockPrompt

    /** Largeur d'un bouton latéral : un carré, à chaque extrémité de la bande. */
    private fun sideWidth(): Float = height * 0.9f

    // ---------------------------------------------------------------- dessin

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        val h = height.toFloat()
        val cyPrompt = h / 2f

        blockPrompt?.let { word ->
            layoutPromptButtons()
            val size = yesRect.height()

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = theme.text
            textPaint.isFakeBoldText = false
            textPaint.textSize = h * 0.30f
            val maxW = yesRect.left - 12f * density
            val label = promptLabel(word, maxW) { textPaint.measureText(it) }
            canvas.drawText(label, 10f * density, cyPrompt + textPaint.textSize * 0.36f, textPaint)
            textPaint.textAlign = Paint.Align.CENTER

            bgPaint.color = theme.accent
            canvas.drawRoundRect(yesRect, size / 2f, size / 2f, bgPaint)
            iconPaint.color = theme.actionText
            iconPaint.strokeWidth = 2.2f * density
            val s = size * 0.22f
            canvas.drawLine(yesRect.centerX() - s, yesRect.centerY(),
                yesRect.centerX() - s * 0.15f, yesRect.centerY() + s * 0.8f, iconPaint)
            canvas.drawLine(yesRect.centerX() - s * 0.15f, yesRect.centerY() + s * 0.8f,
                yesRect.centerX() + s, yesRect.centerY() - s * 0.7f, iconPaint)

            bgPaint.color = theme.functionKey
            canvas.drawRoundRect(noRect, size / 2f, size / 2f, bgPaint)
            iconPaint.color = theme.text
            canvas.drawLine(noRect.centerX() - s, noRect.centerY() - s,
                noRect.centerX() + s, noRect.centerY() + s, iconPaint)
            canvas.drawLine(noRect.centerX() + s, noRect.centerY() - s,
                noRect.centerX() - s, noRect.centerY() + s, iconPaint)
            return
        }

        val side = sideWidth()
        val cy = h / 2f

        drawSideButton(canvas, 0f, side, Tool.EMOJI, EMOJI_SLOT, cy, h)
        drawSideButton(canvas, width - side, side, Tool.SETTINGS, SETTINGS_SLOT, cy, h)

        if (suggestions.isEmpty()) return

        val w = (width - 2f * side) / suggestions.size
        textPaint.textSize = h * 0.40f
        for (i in suggestions.indices) {
            val left = side + i * w
            if (pressedSlot == i) {
                bgPaint.color = theme.keyPressed
                rect.set(left + 2f, h * 0.10f, left + w - 2f, h * 0.90f)
                canvas.drawRoundRect(rect, 8f * density, 8f * density, bgPaint)
            }
            if (i > 0) {
                linePaint.color = if (theme.dark) 0x22FFFFFF else 0x22000000
                canvas.drawLine(left, h * 0.25f, left, h * 0.75f, linePaint)
            }
            textPaint.color = if (i == highlighted) theme.accent else theme.text
            textPaint.isFakeBoldText = i == highlighted
            var label = suggestions[i]
            val maxW = w - 12f * density
            while (textPaint.measureText(label) > maxW && label.length > 3) {
                label = label.substring(0, label.length - 2) + "…"
            }
            canvas.drawText(label, left + w / 2f, cy + textPaint.textSize * 0.36f, textPaint)
        }
    }

    private fun drawSideButton(
        canvas: Canvas, left: Float, w: Float, tool: Int, slot: Int, cy: Float, h: Float
    ) {
        if (pressedSlot == slot) {
            bgPaint.color = theme.keyPressed
            rect.set(left + 2f, h * 0.10f, left + w - 2f, h * 0.90f)
            canvas.drawRoundRect(rect, 8f * density, 8f * density, bgPaint)
        }
        drawToolIcon(canvas, tool, left + w / 2f, cy, h * 0.24f)
        linePaint.color = if (theme.dark) 0x22FFFFFF else 0x22000000
        val x = if (left == 0f) w else left
        canvas.drawLine(x, h * 0.25f, x, h * 0.75f, linePaint)
    }

    /** Icônes dessinées au trait : pas de ressource à charger, elles suivent le thème. */
    private fun drawToolIcon(canvas: Canvas, tool: Int, cx: Float, cy: Float, s: Float) {
        iconPaint.color = theme.text
        iconPaint.strokeWidth = 1.9f * density
        iconPaint.style = Paint.Style.STROKE
        fillPaint.color = theme.text

        when (tool) {
            Tool.EMOJI -> {
                // frimousse : le visage, deux yeux, un sourire
                canvas.drawCircle(cx, cy, s * 0.92f, iconPaint)
                canvas.drawCircle(cx - s * 0.34f, cy - s * 0.24f, s * 0.13f, fillPaint)
                canvas.drawCircle(cx + s * 0.34f, cy - s * 0.24f, s * 0.13f, fillPaint)
                rect.set(cx - s * 0.5f, cy - s * 0.35f, cx + s * 0.5f, cy + s * 0.5f)
                canvas.drawArc(rect, 20f, 140f, false, iconPaint)
            }
            Tool.SETTINGS -> {
                // réglages : deux curseurs
                canvas.drawLine(cx - s * 0.85f, cy - s * 0.4f, cx + s * 0.85f, cy - s * 0.4f, iconPaint)
                canvas.drawLine(cx - s * 0.85f, cy + s * 0.4f, cx + s * 0.85f, cy + s * 0.4f, iconPaint)
                canvas.drawCircle(cx - s * 0.25f, cy - s * 0.4f, s * 0.26f, fillPaint)
                canvas.drawCircle(cx + s * 0.3f, cy + s * 0.4f, s * 0.26f, fillPaint)
            }
        }
    }

    // ---------------------------------------------------------------- touches

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (blockPrompt != null) return true
                downX = event.x
                pressedSlot = slotAt(event.x)
                startLongPress(pressedSlot)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.abs(event.x - downX) > 12f * density) cancelBlockLongPress()
            }
            MotionEvent.ACTION_UP -> {
                cancelBlockLongPress()
                blockPrompt?.let { word ->
                    val x = event.x
                    val y = event.y
                    val surOui = yesRect.contains(x, y)
                    val surNon = noRect.contains(x, y)
                    // le relâchement de l'appui long lui-même laisse la question ouverte
                    if (promptPending && !surOui && !surNon) {
                        promptPending = false
                        return true
                    }
                    promptPending = false
                    cancelPromptTimeout()
                    blockPrompt = null
                    invalidate()
                    if (surOui) listener?.onBlockWord(word)
                    return true
                }
                val slot = slotAt(event.x)
                if (slot == pressedSlot && slot != -1) {
                    when (slot) {
                        EMOJI_SLOT -> listener?.onToolbarAction(Tool.EMOJI)
                        SETTINGS_SLOT -> listener?.onToolbarAction(Tool.SETTINGS)
                        else -> listener?.onPickSuggestion(slot, suggestions[slot])
                    }
                }
                pressedSlot = -1
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelBlockLongPress()
                // un geste annulé ne referme pas la question déjà ouverte
                promptPending = false
                pressedSlot = -1
                invalidate()
            }
        }
        return true
    }

    /** Un appui long sur une proposition ouvre la demande de blocage. */
    private fun startLongPress(slot: Int) {
        cancelBlockLongPress()
        if (slot < 0 || slot >= suggestions.size) return
        val word = suggestions[slot]
        val r = Runnable {
            pressedSlot = -1
            promptPending = true
            showBlockPrompt(word)
        }
        longPress = r
        handler.postDelayed(r, LONG_PRESS_MS)
    }

    private fun cancelBlockLongPress() {
        longPress?.let { handler.removeCallbacks(it) }
        longPress = null
    }

    private fun slotAt(x: Float): Int {
        val side = sideWidth()
        if (x < side) return EMOJI_SLOT
        if (x > width - side) return SETTINGS_SLOT
        if (suggestions.isEmpty()) return -1
        val w = (width - 2f * side) / suggestions.size
        return ((x - side) / w).toInt().coerceIn(0, suggestions.size - 1)
    }

    companion object {
        private const val PROMPT_HEAD = "Ne plus proposer « "
        private const val PROMPT_TAIL = " » ?"
        private const val ELLIPSIS = "…"
        /** Au-delà, un mot ne s'affiche de toute façon pas en entier. */
        private const val PROMPT_MAX_WORD = 200

        /**
         * Libellé de la demande de blocage, raccourci pour tenir dans [maxWidth].
         *
         * On retire des lettres **du mot**, jamais de la fin du libellé : l'ancienne boucle
         * remplaçait « … ? » par « … ? », ne raccourcissait plus rien et figeait le clavier
         * (ANR) dès qu'un mot long ne tenait pas après le premier tour. Ici le nombre de
         * tours est borné par la longueur du mot.
         */
        fun promptLabel(word: String, maxWidth: Float, measure: (String) -> Float): String {
            val w = word.take(PROMPT_MAX_WORD)
            val full = PROMPT_HEAD + w + PROMPT_TAIL
            if (w.length == word.length && measure(full) <= maxWidth) return full
            var n = w.length
            while (n > 1) {
                n--
                val candidate = PROMPT_HEAD + w.take(n) + ELLIPSIS + PROMPT_TAIL
                if (measure(candidate) <= maxWidth) return candidate
            }
            return PROMPT_HEAD + w.take(1) + ELLIPSIS + PROMPT_TAIL
        }

        /** Cases des deux boutons latéraux, distinctes des indices de propositions. */
        const val EMOJI_SLOT = -2
        const val SETTINGS_SLOT = -3
        const val LONG_PRESS_MS = 450L
        const val PROMPT_TIMEOUT_MS = 8000L
    }
}
