package com.board36.app.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * Calque dessiné par-dessus le clavier : aperçu de la touche pressée et
 * sélecteur d'accents (appui long).
 */
class PopupOverlayView(context: Context) : View(context) {

    var theme: KeyboardTheme = KeyboardTheme.DARK

    private var previewLabel: String? = null
    private var previewRect = RectF()

    private var accentChoices: List<String> = emptyList()
    private var accentRect = RectF()
    private var accentCellW = 0f
    private var accentSelection = 0

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = context.resources.displayMetrics.density

    init {
        isClickable = false
        isFocusable = false
    }

    fun showPreview(key: Key, shift: Boolean, offsetY: Float) {
        val label = if (key.isLetter && shift) key.label.uppercase() else key.label
        previewLabel = label
        val w = key.w * 1.35f
        val h = key.h * 1.25f
        // Sur une touche plus large que la vue, les deux bornes s'inversent et `coerceIn`
        // lève une exception : on borne à la main, en gardant l'aperçu dans l'écran.
        val maxLeft = width - w - 2f
        var left = key.centerX() - w / 2f
        left = if (maxLeft <= 2f) 2f else left.coerceIn(2f, maxLeft)
        val top = key.y + offsetY - h * 0.95f
        previewRect.set(left, top, left + w, top + h)
        invalidate()
    }

    fun hidePreview() {
        if (previewLabel != null) { previewLabel = null; invalidate() }
    }

    fun showAccents(key: Key, choices: List<String>, offsetY: Float) {
        accentChoices = choices
        accentSelection = 0
        val cell = key.w.coerceAtLeast(38f * density)
        accentCellW = cell
        val w = cell * choices.size
        var left = key.centerX() - cell / 2f
        // on aligne la première case sur la touche, puis on rentre dans l'écran
        if (left + w > width - 4f) left = (width - w - 4f)
        if (left < 4f) left = 4f
        val h = key.h * 1.05f
        val top = (key.y + offsetY - h - 4f * density).coerceAtLeast(2f)
        accentRect.set(left, top, left + w, top + h)
        invalidate()
    }

    fun moveAccents(x: Float) {
        if (accentChoices.isEmpty()) return
        val i = ((x - accentRect.left) / accentCellW).toInt().coerceIn(0, accentChoices.size - 1)
        if (i != accentSelection) { accentSelection = i; invalidate() }
    }

    /** Ferme le sélecteur et retourne le caractère choisi. */
    fun hideAccents(commit: Boolean): String? {
        if (accentChoices.isEmpty()) return null
        val chosen = if (commit) accentChoices.getOrNull(accentSelection) else null
        accentChoices = emptyList()
        invalidate()
        return chosen
    }

    fun isAccentsVisible() = accentChoices.isNotEmpty()

    override fun onDraw(canvas: Canvas) {
        val r = 10f * density
        previewLabel?.let { label ->
            shadow.color = 0x33000000
            canvas.drawRoundRect(
                previewRect.left + 1.5f * density, previewRect.top + 2f * density,
                previewRect.right + 1.5f * density, previewRect.bottom + 2f * density, r, r, shadow
            )
            bgPaint.color = theme.popupBackground
            canvas.drawRoundRect(previewRect, r, r, bgPaint)
            textPaint.color = theme.text
            textPaint.textSize = previewRect.height() * 0.52f
            canvas.drawText(label, previewRect.centerX(),
                previewRect.centerY() + textPaint.textSize * 0.36f, textPaint)
        }

        if (accentChoices.isNotEmpty()) {
            shadow.color = 0x40000000
            canvas.drawRoundRect(
                accentRect.left + 1.5f * density, accentRect.top + 2f * density,
                accentRect.right + 1.5f * density, accentRect.bottom + 2f * density, r, r, shadow
            )
            bgPaint.color = theme.popupBackground
            canvas.drawRoundRect(accentRect, r, r, bgPaint)
            textPaint.textSize = accentRect.height() * 0.44f
            for (i in accentChoices.indices) {
                val left = accentRect.left + i * accentCellW
                if (i == accentSelection) {
                    bgPaint.color = theme.accent
                    val pad = 3f * density
                    canvas.drawRoundRect(left + pad, accentRect.top + pad,
                        left + accentCellW - pad, accentRect.bottom - pad, r * 0.7f, r * 0.7f, bgPaint)
                    textPaint.color = if (theme.dark) 0xFF101214.toInt() else 0xFFFFFFFF.toInt()
                } else {
                    textPaint.color = theme.text
                }
                canvas.drawText(accentChoices[i], left + accentCellW / 2f,
                    accentRect.centerY() + textPaint.textSize * 0.36f, textPaint)
            }
        }
    }
}
