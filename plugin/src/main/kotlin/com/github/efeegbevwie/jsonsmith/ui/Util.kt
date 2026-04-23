package com.github.efeegbevwie.jsonsmith.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent

fun Modifier.onKeyboardEnterPressed(
    action: () -> Unit
): Modifier {
    return this.onKeyEvent { event ->
        if (event.key == Key.Enter) {
            action()
            true
        } else {
            false
        }
    }
}


fun Modifier.onKeyboardUpOrDownPressed(
    onUp: () -> Unit,
    onDown: () -> Unit
): Modifier {
    return this.onKeyEvent { event ->
        when (event.key) {
            Key.DirectionUp -> {
                onUp()
                true
            }

            Key.DirectionDown -> {
                onDown()
                true
            }

            else -> false
        }
    }
}