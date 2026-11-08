package com.board36.app

import android.content.Context
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.board36.app.engine.KeyGeometry
import com.board36.app.ime.Codes
import com.board36.app.ime.Key
import com.board36.app.ime.KeyboardTheme
import com.board36.app.ime.KeyboardView
import com.board36.app.ime.Layouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** Vérifie le vrai parcours tactile : frappe, appui long, répétition, swipe, deux doigts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TouchTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var kb: KeyboardView
    private val events = ArrayList<String>()
    private var accentsShown: List<String>? = null
    private var gestureDone = false

    private val listener = object : KeyboardView.Listener {
        override fun onKey(code: Int, output: String?) { events.add("key:$code:$output") }
        override fun onKeyPressFeedback(code: Int) {}
        override fun onDeleteSwipeStart() { events.add("del-start") }
        override fun onDeleteSwipe(steps: Int, toEdge: Boolean) {
            events.add("del:$steps:${if (toEdge) "bord" else "-"}")
        }
        override fun onDeleteSwipeEnd(commit: Boolean) { events.add("del-end:$commit") }
        override fun onCursorDrag(steps: Int) { events.add("cursor:$steps") }
        override fun onGestureStarted() { events.add("gesture-start") }
        override fun onGestureFinished(px: FloatArray, py: FloatArray, count: Int, geo: KeyGeometry) {
            gestureDone = true
            events.add("gesture-end:$count")
        }
        override fun onGestureCancelled() { events.add("gesture-cancel") }
        override fun onShowPreview(key: Key, top: Float) {}
        override fun onHidePreview() {}
        override fun onShowAccents(key: Key, choices: List<String>, top: Float) {
            accentsShown = choices
            events.add("accents:${choices.joinToString("")}")
        }
        override fun onMoveAccents(x: Float, y: Float) {}
        override fun onHideAccents(commit: Boolean): String? =
            if (commit) accentsShown?.getOrNull(accentIndex) else null
    }

    private var accentIndex = 0

    @Before
    fun setUp() {
        kb = KeyboardView(context)
        kb.theme = KeyboardTheme.DARK
        kb.listener = listener
        val layout = Layouts.letters(numberRow = true)
        kb.desiredHeight = ROW * layout.rows.size
        kb.setLayout(layout)
        kb.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(ROW * 5, View.MeasureSpec.EXACTLY)
        )
        kb.layout(0, 0, WIDTH, ROW * 5)
        events.clear()
    }

    private fun key(label: String): Key = kb.currentLayout()!!.keys.first { it.label == label }

    private fun send(action: Int, x: Float, y: Float) {
        val e = MotionEvent.obtain(0L, time, action, x, y, 0)
        kb.dispatchTouchEvent(e)
        e.recycle()
    }

    private var time = 0L

    private fun tap(label: String) {
        val k = key(label)
        send(MotionEvent.ACTION_DOWN, k.centerX(), k.centerY())
        send(MotionEvent.ACTION_UP, k.centerX(), k.centerY())
    }

    @Test
    fun frappeSimple() {
        tap("a")
        tap("m")
        assertEquals(listOf("key:97:a", "key:109:m"), events)
    }

    @Test
    fun majusculeAvecShift() {
        val shift = kb.currentLayout()!!.keys.first { it.code == Codes.SHIFT }
        send(MotionEvent.ACTION_DOWN, shift.centerX(), shift.centerY())
        send(MotionEvent.ACTION_UP, shift.centerX(), shift.centerY())
        assertEquals(1, kb.shiftState)
        events.clear()
        tap("a")
        assertEquals(listOf("key:97:A"), events)
    }

    @Test
    fun appuiLongOuvreLesAccents() {
        val e = key("e")
        send(MotionEvent.ACTION_DOWN, e.centerX(), e.centerY())
        shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS)
        assertTrue("accents non ouverts : $events", accentsShown != null)
        assertTrue(accentsShown!!.contains("é"))
        accentIndex = accentsShown!!.indexOf("é")
        send(MotionEvent.ACTION_UP, e.centerX(), e.centerY())
        assertTrue("évènements : $events", events.contains("key:0:é"))
    }

    @Test
    fun maintienDeLaSuppressionSeRepete() {
        val del = kb.currentLayout()!!.keys.first { it.code == Codes.DELETE }
        send(MotionEvent.ACTION_DOWN, del.centerX(), del.centerY())
        shadowOf(Looper.getMainLooper()).idleFor(600, TimeUnit.MILLISECONDS)
        val repeats = events.count { it == "key:${Codes.DELETE}:null" }
        assertTrue("répétitions : $repeats", repeats >= 3)
        send(MotionEvent.ACTION_UP, del.centerX(), del.centerY())
        // pas de suppression supplémentaire au relâchement
        assertEquals(repeats, events.count { it == "key:${Codes.DELETE}:null" })
    }

    @Test
    fun balayageSurSuppressionEffaceDePlusEnPlus() {
        val del = kb.currentLayout()!!.keys.first { it.code == Codes.DELETE }
        val x0 = del.centerX()
        val y = del.centerY()
        send(MotionEvent.ACTION_DOWN, x0, y)
        send(MotionEvent.ACTION_MOVE, x0 - 100f, y)
        send(MotionEvent.ACTION_MOVE, x0 - 300f, y)
        send(MotionEvent.ACTION_MOVE, x0 - 600f, y)
        send(MotionEvent.ACTION_UP, x0 - 600f, y)

        assertTrue("évènements : $events", events.contains("del-start"))
        val crans = events.filter { it.startsWith("del:") }
            .map { it.split(":")[1].toInt() }
        assertTrue("les crans doivent croître : $crans", crans.size >= 2 && crans.last() > crans.first())
        assertTrue("évènements : $events", events.contains("del-end:true"))
        assertTrue("aucune lettre ne doit être saisie", events.none { it.startsWith("key:") })
    }

    @Test
    fun balayageJusquAuBordSignaleLExtremite() {
        val del = kb.currentLayout()!!.keys.first { it.code == Codes.DELETE }
        val y = del.centerY()
        send(MotionEvent.ACTION_DOWN, del.centerX(), y)
        send(MotionEvent.ACTION_MOVE, del.centerX() - 200f, y)
        send(MotionEvent.ACTION_MOVE, 20f, y)
        send(MotionEvent.ACTION_UP, 20f, y)
        assertTrue("le bord doit être signalé : $events", events.any { it.endsWith(":bord") })
    }

    @Test
    fun glissementSurEspaceDeplaceLeCurseur() {
        val space = kb.currentLayout()!!.keys.first { it.code == Codes.SPACE }
        send(MotionEvent.ACTION_DOWN, space.centerX(), space.centerY())
        send(MotionEvent.ACTION_MOVE, space.centerX() + 200f, space.centerY())
        send(MotionEvent.ACTION_UP, space.centerX() + 200f, space.centerY())
        assertTrue("évènements : $events", events.any { it.startsWith("cursor:") })
        assertTrue("aucune espace ne doit être saisie", events.none { it.startsWith("key:32") })
    }

    @Test
    fun leSwipeEstDetecteEtTermine() {
        val b = key("b")
        val o = key("o")
        send(MotionEvent.ACTION_DOWN, b.centerX(), b.centerY())
        val steps = 12
        for (i in 1..steps) {
            val f = i.toFloat() / steps
            send(MotionEvent.ACTION_MOVE,
                b.centerX() + (o.centerX() - b.centerX()) * f,
                b.centerY() + (o.centerY() - b.centerY()) * f)
        }
        send(MotionEvent.ACTION_UP, o.centerX(), o.centerY())
        assertTrue("évènements : $events", events.contains("gesture-start"))
        assertTrue("le geste doit se terminer : $events", gestureDone)
        assertTrue("aucune lettre ne doit être tapée", events.none { it.startsWith("key:") })
    }

    @Test
    fun deuxDoigtsEnRoulement() {
        val a = key("a")
        val z = key("z")
        val props = arrayOf(pointer(0), pointer(1))
        val c1 = coords(a.centerX(), a.centerY())
        val c2 = coords(z.centerX(), z.centerY())

        var e = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1,
            arrayOf(props[0]), arrayOf(c1), 0, 0, 1f, 1f, 0, 0, 0, 0)
        kb.dispatchTouchEvent(e); e.recycle()

        e = MotionEvent.obtain(0, 1,
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, props, arrayOf(c1, c2), 0, 0, 1f, 1f, 0, 0, 0, 0)
        kb.dispatchTouchEvent(e); e.recycle()

        e = MotionEvent.obtain(0, 2,
            MotionEvent.ACTION_POINTER_UP or (0 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, props, arrayOf(c1, c2), 0, 0, 1f, 1f, 0, 0, 0, 0)
        kb.dispatchTouchEvent(e); e.recycle()

        e = MotionEvent.obtain(0, 3, MotionEvent.ACTION_UP, 1,
            arrayOf(props[1]), arrayOf(c2), 0, 0, 1f, 1f, 0, 0, 0, 0)
        kb.dispatchTouchEvent(e); e.recycle()

        assertEquals(listOf("key:97:a", "key:122:z"), events)
    }

    private fun pointer(id: Int) = MotionEvent.PointerProperties().apply {
        this.id = id
        toolType = MotionEvent.TOOL_TYPE_FINGER
    }

    private fun coords(x: Float, y: Float) = MotionEvent.PointerCoords().apply {
        this.x = x
        this.y = y
        pressure = 1f
        size = 1f
    }

    companion object {
        const val WIDTH = 1080
        const val ROW = 150
    }
}
