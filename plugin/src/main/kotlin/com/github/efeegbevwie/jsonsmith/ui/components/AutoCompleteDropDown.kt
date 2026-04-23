package com.github.efeegbevwie.jsonsmith.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.DropdownState
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.styling.DropdownStyle
import org.jetbrains.jewel.ui.focusOutline
import org.jetbrains.jewel.ui.outline
import org.jetbrains.jewel.ui.theme.dropdownStyle
import org.jetbrains.jewel.ui.util.thenIf

@Composable
fun AutoCompleteTextField(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    expanded: Boolean = false,
    selectedSuggestionIndex:Int,
    menuModifier: Modifier = Modifier,
    outline: Outline = Outline.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: DropdownStyle = JewelTheme.dropdownStyle,
    menuContent: MenuScope.(selectedSuggestionIndex : Int) -> Unit,
    onDismissPopup:() -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    textFieldContent: @Composable BoxScope.() -> Unit,
) {
    var dropdownState by remember(interactionSource) { mutableStateOf(DropdownState.of(enabled = enabled)) }

    remember(enabled) { dropdownState = dropdownState.copy(enabled = enabled) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> dropdownState = dropdownState.copy(pressed = true)
                is PressInteraction.Cancel,
                is PressInteraction.Release -> dropdownState = dropdownState.copy(pressed = false)
                is HoverInteraction.Enter -> dropdownState = dropdownState.copy(hovered = true)
                is HoverInteraction.Exit -> dropdownState = dropdownState.copy(hovered = false)
                is FocusInteraction.Focus -> dropdownState = dropdownState.copy(focused = true)
                is FocusInteraction.Unfocus -> dropdownState = dropdownState.copy(focused = false)
            }
        }
    }

    val colors = style.colors
    val metrics = style.metrics
    val shape = RoundedCornerShape(style.metrics.cornerSize)
    val minSize = metrics.minSize
    val arrowMinSize = style.metrics.arrowMinSize
    val borderColor by colors.borderFor(dropdownState)
    val hasNoOutline = outline == Outline.None

    var componentWidth by remember { mutableIntStateOf(-1) }
    Box(
        modifier =
            modifier
                .background(colors.backgroundFor(dropdownState).value, shape)
                .thenIf(hasNoOutline) { border(Stroke.Alignment.Center, style.metrics.borderWidth, borderColor, shape) }
                .thenIf(outline == Outline.None) { focusOutline(dropdownState, shape) }
                .outline(dropdownState, outline, shape)
                .width(IntrinsicSize.Max)
                .defaultMinSize(minSize.width, minSize.height.coerceAtLeast(arrowMinSize.height))
                .onSizeChanged { componentWidth = it.width },
        contentAlignment = Alignment.CenterStart,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides colors.contentFor(dropdownState).value,
            LocalTextStyle provides LocalTextStyle.current.copy(color = colors.contentFor(dropdownState).value),
        ) {
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .onKeyEvent(onKeyEvent)
                ,
                contentAlignment = Alignment.CenterStart,
                content = textFieldContent,
            )
        }

        if (expanded) {
            val density = LocalDensity.current
            PopupMenu(
                popupProperties = PopupProperties(focusable = false, dismissOnClickOutside = true),
                onDismissRequest = {
                    onDismissPopup()
                    true
                },
                modifier =
                    menuModifier
                        .focusProperties { canFocus = false }
                        .defaultMinSize(minWidth = with(density) { componentWidth.toDp() }),
                style = style.menuStyle,
                horizontalAlignment = Alignment.Start,
                content = {
                    menuContent(selectedSuggestionIndex)
                },
            )
        }
    }
}

