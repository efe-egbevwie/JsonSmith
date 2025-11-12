package com.github.efeegbevwie.jsonsmith.toolWindow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.efeegbevwie.jsonsmith.models.JsonTreeItem
import com.github.efeegbevwie.jsonsmith.models.JsonSmithEvent
import com.github.efeegbevwie.jsonsmith.models.SearchState
import com.github.efeegbevwie.jsonsmith.plugin.generated.resources.Res
import com.github.efeegbevwie.jsonsmith.plugin.generated.resources.boolType
import com.github.efeegbevwie.jsonsmith.plugin.generated.resources.numeric
import com.github.efeegbevwie.jsonsmith.plugin.generated.resources.string
import kotlinx.serialization.json.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys

interface JsonStructureUiEvents {
    fun onParseJsonStructureClicked()
    fun onFormatJsonClicked(jsonInput: TextFieldState)
    fun onNavigateToNextMatch(): Boolean
    fun onNavigateToPreviousMatch(): Boolean
    fun onNodeExpandedToggle(nodePath: String)

    fun onCancelSearch()
    fun jsonItemMatchesSearch(item: JsonTreeItem, query: String): Boolean
}

@Composable
fun JsonStructureContent(
    jsonInput: TextFieldState,
    flattenedJson: List<JsonTreeItem>,
    jsonSearchTextState: TextFieldState,
    lazyListState: LazyListState,
    event: JsonSmithEvent? = null,
    searchState: SearchState,
    events: JsonStructureUiEvents,
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
                Text("Enter Json Value")
            }
        )

        AnimatedVisibility(visible = event != null) {
            when (event?.errorEvent) {
                true -> ErrorBanner(text = event.message)
                false -> SuccessBanner(text = event.message)
                null -> {}
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (jsonInput.text.isNotBlank()) {
                        events.onParseJsonStructureClicked()
                    }
                }
            ) {
                Text("Parse")
            }

            OutlinedButton(
                onClick = {
                    if (jsonInput.text.isNotBlank()) {
                        events.onFormatJsonClicked(jsonInput)
                    }
                }
            ) {
                Text("Format")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        AnimatedVisibility(visible = flattenedJson.isNotEmpty()) {
            JsonTreeLazyColumn(
                flattenedJson = flattenedJson,
                onNodeExpandedToggle = {
                    events.onNodeExpandedToggle(nodePath = it)
                },
                searchState = searchState,
                lazyListState = lazyListState,
                jsonSearchTextState = jsonSearchTextState,
                onNavigateToNextMatch = { events.onNavigateToNextMatch() },
                onCancelSearch = { events.onCancelSearch() },
                onNavigateToPreviousMatch = { events.onNavigateToPreviousMatch() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JsonTreeLazyColumn(
    modifier: Modifier = Modifier,
    jsonSearchTextState: TextFieldState,
    lazyListState: LazyListState,
    onCancelSearch: () -> Unit,
    flattenedJson: List<JsonTreeItem>,
    onNodeExpandedToggle: (String) -> Unit,
    searchState: SearchState,
    onNavigateToNextMatch: () -> Boolean,
    onNavigateToPreviousMatch: () -> Boolean,
) {
    LaunchedEffect(searchState.matchedItemIndex, searchState.currentMatchIndex) {
        searchState.matchedItemIndex?.let { index ->
            if (index == searchState.matchedItemsIndices.first()) {
                lazyListState.scrollToItem(
                    index = index,
                    scrollOffset = 0
                )
            } else {
                lazyListState.scrollToItem(
                    index = index,
                    scrollOffset = 0
                )
            }
        }
    }

    Column(modifier = modifier) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            JsonSearchField(
                modifier = Modifier.weight(2f),
                jsonSearchTextState = jsonSearchTextState,
                onCancelSearch = onCancelSearch,
                onNavigateToNextMatch = onNavigateToNextMatch,
            )

            if (searchState.totalMatches > 0) {
                SearchMatchedItemsIndicator(
                    modifier = Modifier.weight(1f),
                    currentMatchNumber = searchState.currentMatchedIndex,
                    totalMatches = searchState.totalMatches,
                    hasPreviousMatch = searchState.hasPreviousMatch,
                    hasNextMatch = searchState.hasNextMatch,
                    onNavigateToNextMatch = onNavigateToNextMatch,
                    onNavigateToPreviousMatch = onNavigateToPreviousMatch,
                )
            }
        }

        Row {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth(0.95f)
            ) {
                itemsIndexed(
                    items = flattenedJson,
                    key = { index: Int, item: JsonTreeItem ->
                        item.nodePath.plus(item.level)
                    },
                    contentType = { index: Int, item: JsonTreeItem -> index }
                ) { index: Int, item: JsonTreeItem ->
                    val isMatched = searchState.matchedItemsIndices.contains(index)

                    val indentModifier = Modifier
                        .padding(start = (item.level * 16).dp)
                        .then(
                            if (isMatched) Modifier.background(Color.Yellow.copy(alpha = 0.3f))
                            else Modifier
                        )

                    when (item) {
                        is JsonTreeItem.ObjectItem -> {
                            JsonObjectItemRow(
                                item = item,
                                expanded = item.expanded,
                                onNodeExpandedToggle = onNodeExpandedToggle,
                                modifier = indentModifier
                            )
                        }

                        is JsonTreeItem.ArrayItem -> {
                            JsonArrayItemRow(
                                item = item,
                                expanded = item.expanded,
                                onNodeExpandedToggle = onNodeExpandedToggle,
                                modifier = indentModifier
                            )
                        }

                        is JsonTreeItem.PrimitiveItem -> {
                            AnimatedVisibility(visible = true) {
                                JsonPrimitiveItemRow(
                                    item = item,
                                    modifier = indentModifier
                                )
                            }
                        }
                    }
                }
            }

            VerticalScrollbar(
                scrollState = lazyListState,
            )
        }
    }
}

@Composable
private fun JsonSearchField(
    modifier: Modifier = Modifier,
    jsonSearchTextState: TextFieldState,
    onCancelSearch: () -> Unit = {},
    onNavigateToNextMatch: () -> Boolean,
) {
    TextField(
        state = jsonSearchTextState,
        modifier = modifier,
        placeholder = {
            Text("Search by key or value")
        },
        trailingIcon = {
            Icon(
                key = AllIconsKeys.Actions.Cancel,
                contentDescription = "Cancel",
                modifier = Modifier
                    .clickable(
                        enabled = jsonSearchTextState.text.isNotBlank()
                    ) {
                        onCancelSearch()
                    }
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        onKeyboardAction = {
            onNavigateToNextMatch()
        }
    )
}

@Composable
private fun SearchMatchedItemsIndicator(
    modifier: Modifier = Modifier,
    currentMatchNumber: Int,
    totalMatches: Int,
    hasPreviousMatch: Boolean,
    hasNextMatch: Boolean,
    onNavigateToNextMatch: () -> Boolean,
    onNavigateToPreviousMatch: () -> Boolean,
) {
    Row(modifier = modifier) {
        Text(
            text = "$currentMatchNumber/$totalMatches",
            modifier = Modifier.padding(horizontal = 8.dp)
        )


        IconButton(
            onClick = { onNavigateToPreviousMatch() },
            enabled = hasPreviousMatch
        ) {
            Icon(
                key = AllIconsKeys.Actions.MoveUp,
                contentDescription = "Previous match"
            )
        }

        IconButton(
            onClick = { onNavigateToNextMatch() },
            enabled = hasNextMatch
        ) {
            Icon(
                key = AllIconsKeys.Actions.MoveDown,
                contentDescription = "Next match"
            )
        }
    }
}

@Composable
private fun JsonObjectItemRow(
    item: JsonTreeItem.ObjectItem,
    expanded: Boolean,
    onNodeExpandedToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onNodeExpandedToggle(item.nodePath) }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            key = if (expanded) AllIconsKeys.Profiler.CollapseNode else AllIconsKeys.Profiler.ExpandNode,
            contentDescription = "Toggle",
            modifier = Modifier.size(16.dp)
        )

        Icon(
            key = AllIconsKeys.Json.Object,
            contentDescription = "Type"
        )

        val node = buildString {
            if (item.fromArray) {
                append("[${item.arrayIndex}] ")
            }
            item.key?.let {
                append(it)
                append(" ")
            }
            append("(Object)")
            append(" (${item.jsonObject.entries.size} items)")
        }

        Text(
            text = node,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun JsonArrayItemRow(
    item: JsonTreeItem.ArrayItem,
    expanded: Boolean,
    onNodeExpandedToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onNodeExpandedToggle(item.nodePath) }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            key = if (expanded) AllIconsKeys.Profiler.CollapseNode else AllIconsKeys.Profiler.ExpandNode,
            contentDescription = "Toggle",
            modifier = Modifier.size(16.dp)
        )

        Icon(
            key = AllIconsKeys.Json.Array,
            contentDescription = "Type"
        )

        val node = buildString {
            if (item.fromArray) {
                append("[${item.arrayIndex}] ")
            }
            item.key?.let {
                append(it)
                append(" ")
            }
            append("(Array)")
            append(" (${item.jsonArray.size} items)")
        }

        Text(
            text = node,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun JsonPrimitiveItemRow(
    item: JsonTreeItem.PrimitiveItem,
    modifier: Modifier = Modifier
) {
    val jsonPrimitive = item.jsonPrimitive
    val value = jsonPrimitive.content
    val key = if (item.fromArray) "[${item.arrayIndex}]" else item.key

    val leafContent = buildString {
        append("$key: $value (${getJsonType(jsonPrimitive)})")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val iconRes = when {
            jsonPrimitive.booleanOrNull != null -> Res.drawable.boolType
            jsonPrimitive.intOrNull != null -> Res.drawable.numeric
            jsonPrimitive.longOrNull != null -> Res.drawable.numeric
            jsonPrimitive.doubleOrNull != null -> Res.drawable.numeric
            jsonPrimitive.floatOrNull != null -> Res.drawable.numeric
            jsonPrimitive.isString -> Res.drawable.string
            else -> null
        }

        iconRes?.let {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = "Icon",
                modifier = Modifier.size(16.dp)
            )
        }

        SelectionContainer {
            Text(
                text = leafContent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Light
            )
        }
    }
}


private fun getJsonType(jsonElement: JsonElement): String {
    return when (jsonElement) {
        is JsonObject -> "Object"
        is JsonArray -> "Array"
        is JsonPrimitive -> when {
            jsonElement.booleanOrNull != null -> "Boolean"
            jsonElement.intOrNull != null -> "Int"
            jsonElement.longOrNull != null -> "Long"
            jsonElement.doubleOrNull != null -> "Double"
            jsonElement.floatOrNull != null -> "Float"
            jsonElement.isString -> "String"
            else -> "Unknown"
        }
    }
}
