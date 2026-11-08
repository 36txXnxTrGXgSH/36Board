package com.board36.app.ime

import android.graphics.Color

data class KeyboardTheme(
    val background: Int,
    val key: Int,
    val keyPressed: Int,
    val functionKey: Int,
    val functionKeyPressed: Int,
    val actionKey: Int,
    val actionKeyPressed: Int,
    val text: Int,
    val secondaryText: Int,
    val actionText: Int,
    val accent: Int,
    val trail: Int,
    val popupBackground: Int,
    val dark: Boolean
) {
    companion object {
        val DARK = KeyboardTheme(
            background = Color.parseColor("#16181B"),
            key = Color.parseColor("#2A2D32"),
            keyPressed = Color.parseColor("#3C4048"),
            functionKey = Color.parseColor("#20242A"),
            functionKeyPressed = Color.parseColor("#333941"),
            actionKey = Color.parseColor("#2F6F65"),
            actionKeyPressed = Color.parseColor("#3E8B7F"),
            text = Color.parseColor("#ECEFF1"),
            secondaryText = Color.parseColor("#98A0A8"),
            actionText = Color.parseColor("#FFFFFF"),
            accent = Color.parseColor("#63C2B4"),
            trail = Color.parseColor("#63C2B4"),
            popupBackground = Color.parseColor("#33383F"),
            dark = true
        )

        val LIGHT = KeyboardTheme(
            background = Color.parseColor("#E3E6EB"),
            key = Color.parseColor("#FFFFFF"),
            keyPressed = Color.parseColor("#D5DAE1"),
            functionKey = Color.parseColor("#C9CFD8"),
            functionKeyPressed = Color.parseColor("#B3BAC4"),
            actionKey = Color.parseColor("#2F7F73"),
            actionKeyPressed = Color.parseColor("#256A60"),
            text = Color.parseColor("#1A1C1E"),
            secondaryText = Color.parseColor("#5F6368"),
            actionText = Color.parseColor("#FFFFFF"),
            accent = Color.parseColor("#2F7F73"),
            trail = Color.parseColor("#2F7F73"),
            popupBackground = Color.parseColor("#FFFFFF"),
            dark = false
        )
    }
}
