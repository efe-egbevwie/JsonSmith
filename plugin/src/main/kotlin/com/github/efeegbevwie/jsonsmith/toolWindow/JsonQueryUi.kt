package com.github.efeegbevwie.jsonsmith.toolWindow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.efe.jsonSmith.parser.structureParser.JsonArrayStructure
import com.github.efeegbevwie.jsonsmith.models.FilterOperation
import com.github.efeegbevwie.jsonsmith.models.JsonSmithEvent
import com.github.efeegbevwie.jsonsmith.models.JsonTreeItem
import com.github.efeegbevwie.jsonsmith.ui.components.AutoCompleteTextField
import com.github.efeegbevwie.jsonsmith.ui.onKeyboardEnterPressed
import com.github.efeegbevwie.jsonsmith.ui.onKeyboardUpOrDownPressed
import kotlinx.serialization.json.JsonArray
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun JsonQueryUi(
    jsonInput: TextFieldState,
    flattenedJson: List<JsonTreeItem>? = null,
    jsonArrayStructure: JsonArrayStructure? = null,
    filteredJsonArray: JsonArray? = null,
    filterKeyState: TextFieldState,
    filterValueState: TextFieldState,
    filterSecondValueState: TextFieldState,
    selectedFilterOperation: FilterOperation? = null,
    suggestions: List<String> = emptyList(),
    onSuggestionSelected: (suggestion: String) -> Unit,
    encodedJson:String? = null,
    isAutocompleteDropdownVisible: Boolean,
    availableOperations: List<FilterOperation> = emptyList(),
    event: JsonSmithEvent? = null,
    onParseJsonClicked: () -> Unit,
    onFilterOperationSelected: (FilterOperation) -> Unit = {},
    onApplyFilterClicked: () -> Unit = {},
    onClearFilerClicked: () -> Unit,
    modifier: Modifier = Modifier
        .fillMaxSize()
        .padding(10.dp)
) {
    Column(modifier = modifier) {
        val jsonTextAreaOutline = if (event is JsonSmithEvent.JsonParsingFailed) {
            Outline.Error
        } else {
            Outline.None
        }
        TextArea(
            outline = jsonTextAreaOutline,
            modifier = Modifier.height(200.dp).fillMaxWidth(),
            state = jsonInput,
            placeholder = {
                Text("Enter Json Array")
            }
        )
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                if (jsonInput.text.isNotBlank()) {
                    onParseJsonClicked()
                }
            }
        ) {
            Text("Parse")
        }

        Spacer(modifier = Modifier.height(20.dp))


        AnimatedVisibility(jsonArrayStructure != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Filter")
                    Spacer(modifier = Modifier.width(10.dp))
                    IconActionButton(
                        key = AllIconsKeys.Actions.Close,
                        contentDescription = "Copy",
                        onClick = {
                            onClearFilerClicked()
                        },
                        tooltip = {
                            Text(text = "Clear")
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var selectedSuggestionIndex by remember { mutableIntStateOf(-1) }
                    AutoCompleteTextField(
                        modifier = Modifier.weight(1f),
                        expanded = isAutocompleteDropdownVisible,
                        selectedSuggestionIndex = selectedSuggestionIndex,
                        onDismissPopup = {},
                        textFieldContent = {
                            TextField(
                                state = filterKeyState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onKeyboardUpOrDownPressed(
                                        onUp = {
                                            selectedSuggestionIndex =
                                                (selectedSuggestionIndex - 1).coerceAtLeast(0)
                                        },
                                        onDown = {
                                            selectedSuggestionIndex =
                                                (selectedSuggestionIndex + 1).coerceAtMost(suggestions.lastIndex)
                                        }
                                    )
                                    .onKeyboardEnterPressed{
                                        if (selectedSuggestionIndex != -1) {
                                            val selectedSuggestion: String =
                                                suggestions[selectedSuggestionIndex]

                                            onSuggestionSelected(selectedSuggestion)
                                            selectedSuggestionIndex = -1
                                        }
                                    }
                                ,
                                placeholder = {
                                    Text("WHERE")
                                }
                            )

                        },
                        menuContent = { currentSuggestionINdex ->
                            suggestions.forEachIndexed { index, suggestion ->
                                val isItemSelected: Boolean = index == currentSuggestionINdex

                                selectableItem(
                                    selected = isItemSelected,
                                    onClick = {
                                        onSuggestionSelected(suggestion)
                                    },
                                    content = {
                                        val interactionSource = remember { MutableInteractionSource() }
                                        val isHovered by interactionSource.collectIsHoveredAsState()
                                        val suggestionHighlighted = index == selectedSuggestionIndex
                                        val backgroundColor =
                                            if (suggestionHighlighted || isHovered) JewelTheme.globalColors.outlines.focused else JewelTheme.globalColors.panelBackground

                                        Box(
                                            modifier = Modifier
                                                .hoverable(interactionSource)
                                                .padding(vertical = 6.dp)
                                                .background(backgroundColor)

                                        ) {
                                            Text(
                                                text = suggestion,
                                                modifier = Modifier.fillMaxWidth()
                                                    .padding(horizontal = 6.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    )


                    Spacer(modifier = Modifier.width(8.dp))

                    Dropdown(
                        enabled = availableOperations.isNotEmpty(),
                        modifier = Modifier.width(150.dp),
                        menuContent = {
                            availableOperations.forEach { operation ->
                                selectableItem(
                                    selected = selectedFilterOperation == operation,
                                    onClick = { onFilterOperationSelected(operation) }
                                ) {
                                    Text(operation.displayName)
                                }
                            }
                        }
                    ) {
                        Text(selectedFilterOperation?.displayName ?: "Select operation")
                    }
                }



                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedFilterOperation is FilterOperation.NumberOperation.Between) {
                        TextField(
                            state = filterValueState,
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text("Min")
                            }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        TextField(
                            state = filterSecondValueState,
                            modifier = Modifier.weight(1f)
                                .onKeyboardEnterPressed(action = onApplyFilterClicked),
                            placeholder = {
                                Text("Max")
                            }
                        )
                    } else {
                        TextField(
                            state = filterValueState,
                            modifier = Modifier.weight(1f)
                                .onKeyboardEnterPressed(action = onApplyFilterClicked),
                            placeholder = {
                                Text("Value")
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        modifier = Modifier,
                        onClick = onApplyFilterClicked,
                        enabled = filterKeyState.text.isNotBlank() &&
                                selectedFilterOperation != null &&
                                (filterValueState.text.isNotBlank() ||
                                        (selectedFilterOperation is FilterOperation.NumberOperation.Between &&
                                                filterSecondValueState.text.isNotBlank()))
                    ) {
                        Text("Go")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        AnimatedVisibility(jsonArrayStructure != null) {
            VerticallyScrollableContainer {
                SelectionContainer {
                    Text(
                        text = encodedJson ?: "",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )
                }

            }
        }
    }
}

@Composable
fun DropDownContent(
    suggestions: List<String>,
    selectedSuggestionIndex: Int,
    onSuggestionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
        ) {
            suggestions.forEachIndexed { index, suggestion ->
                val interactionSource = remember { MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()
                val suggestionHighlighted = index == selectedSuggestionIndex
                val backgroundColor =
                    if (suggestionHighlighted || isHovered) JewelTheme.globalColors.outlines.focused else JewelTheme.globalColors.panelBackground

                Box(
                    modifier = Modifier
                        .hoverable(interactionSource)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            onSuggestionSelected(suggestion)
                        }
                        .padding(vertical = 6.dp)
                        .background(backgroundColor)

                ) {
                    Text(
                        text = suggestion,
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 6.dp)
                    )
                }
            }
        }
    }
}

