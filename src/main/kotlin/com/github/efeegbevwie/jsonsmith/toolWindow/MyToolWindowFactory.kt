package com.github.efeegbevwie.jsonsmith.toolWindow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.efe.jsonSmith.languageParsers.ParsedType
import com.efe.jsonSmith.targetLanguages.TargetLanguage
import com.efe.jsonSmith.targetLanguages.TargetLanguageConfig
import com.efe.jsonSmith.targetLanguages.displayName
import com.efe.jsonSmith.targetLanguages.enabledTargetLanguages
import com.github.efeegbevwie.jsonsmith.jsonsmith.generated.resources.Res
import com.github.efeegbevwie.jsonsmith.jsonsmith.generated.resources.boolType
import com.github.efeegbevwie.jsonsmith.jsonsmith.generated.resources.numeric
import com.github.efeegbevwie.jsonsmith.jsonsmith.generated.resources.string
import com.github.efeegbevwie.jsonsmith.services.MyProjectService
import com.github.efeegbevwie.jsonsmith.services.MyProjectService.JsonSmithEvent
import com.intellij.json.psi.JsonLiteral
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import kotlinx.serialization.json.*
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys


class MyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val service = toolWindow.project.service<MyProjectService>()

        toolWindow.addComposeTab(tabDisplayName = "Parse") {
            val generatedType: ParsedType? by service.generatedType.collectAsState()
            val targetLanguage: TargetLanguage by service.targetLanguage.collectAsState()
            val event: JsonSmithEvent? by service.jsonParsingEvents.collectAsState(initial = null)

            SwingBridgeTheme {
                JsonSmithToolWindowContent(
                    jsonInput = service.jsonInput,
                    className = service.classNameInput,
                    onFormatJsonClicked = { jsonInput ->
                        service.formatJson(jsonContent = jsonInput)
                    },
                    onParseJsonClicked = { json ->
                        service.generateTypeFromJson(json = json)
                    },
                    onCopyGeneratedTypeClicked = {
                        service.copyToClipboard(it)
                    },
                    onSaveGeneratedTypesClicked = {
                        service.saveGeneratedType(project)
                    },
                    event = event,
                    generatedType = generatedType,
                    targetLanguage = targetLanguage,
                    onTargetLanguageClicked = { service.setTargetLanguage(it) },
                    onLanguageConfigChanged = { service.updateTargetLanguageConfig(it) },
                )
            }
        }


        toolWindow.addComposeTab(tabDisplayName = "View Structure") {
            val jsonElement: JsonElement? by service.jsonElement.collectAsState()
            val jsonStructureEvents: JsonSmithEvent? by service.jsonStructureParsingEvents.collectAsState(initial = null)

            SwingBridgeTheme {
                JsonStructureContent(
                    jsonInput = service.jsonStructureInput,
                    jsonElement = jsonElement,
                    event = jsonStructureEvents,
                    onFormatJsonClicked = { service.formatJson(it) },
                    onParseJsonStructureClicked = { service.getJsonElement() }
                )
            }
        }
    }

    override fun shouldBeAvailable(project: Project) = true
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JsonSmithToolWindowContent(
    jsonInput: TextFieldState,
    className: TextFieldState,
    generatedType: ParsedType? = null,
    targetLanguage: TargetLanguage,
    onFormatJsonClicked: (jsonInput: TextFieldState) -> Unit,
    onParseJsonClicked: (json: String) -> Unit,
    onCopyGeneratedTypeClicked: (type: String) -> Unit,
    onSaveGeneratedTypesClicked: () -> Unit,
    onTargetLanguageClicked: (TargetLanguage) -> Unit,
    onLanguageConfigChanged: (TargetLanguageConfig) -> Unit,
    event: JsonSmithEvent? = null,
    modifier: Modifier = Modifier
        .fillMaxSize()
        .padding(10.dp)
) {
    Column(modifier = modifier) {
        TextField(
            modifier = Modifier.fillMaxWidth(),
            state = className,
            placeholder = {
                Text("Class name")
            }
        )
        Spacer(modifier = Modifier.height(10.dp))
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

        Dropdown(
            menuContent = {
                enabledTargetLanguages.forEach { language ->
                    selectableItem(
                        selected = targetLanguage == language,
                        onClick = { onTargetLanguageClicked(language) }
                    ) {
                        Text(text = language.displayName())
                    }
                }

            }
        ) {
            Text(text = targetLanguage.displayName())
        }

        Spacer(modifier = Modifier.height(16.dp))

        LanguageConfig(
            targetLanguage = targetLanguage,
            config = targetLanguage.targetLanguageConfig,
            onConfigChanged = onLanguageConfigChanged
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)){
            OutlinedButton(
                onClick = {
                    if (jsonInput.text.isNotBlank()) {
                        onParseJsonClicked(jsonInput.text.toString())
                    }
                }
            ) {
                Text("Parse")
            }

            OutlinedButton(
                onClick = {
                    if (jsonInput.text.isNotBlank()) {
                        onFormatJsonClicked(jsonInput)
                    }
                }
            ) {
                Text("Format")
            }
        }


        Spacer(modifier = Modifier.height(20.dp))

        AnimatedVisibility(visible = generatedType?.stringRepresentation?.isNotEmpty() == true) {
            Column(
                modifier = Modifier
                    .padding(bottom = 20.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    IconActionButton(
                        key = AllIconsKeys.Actions.Copy,
                        contentDescription = "Copy",
                        onClick = {
                            if (generatedType?.stringRepresentation?.isNotBlank() == true) {
                                onCopyGeneratedTypeClicked(generatedType.stringRepresentation)
                            }
                        },
                        tooltip = {
                            Text(text = "Copy")
                        }
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    IconActionButton(
                        key = AllIconsKeys.Actions.MenuSaveall,
                        contentDescription = "Save",
                        onClick = { onSaveGeneratedTypesClicked() },
                        tooltip = {
                            Text(text = "Save")
                        }
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Column(
                    modifier = Modifier
                ) {
                    VerticallyScrollableContainer {
                        generatedType ?: return@VerticallyScrollableContainer
                        Column {
                            if (generatedType.imports?.isNotBlank() == true) {
                                Text(generatedType.imports.orEmpty())
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            val showCopyIcon = generatedType?.parsedClasses?.size?.let { it > 1 } ?: false
                            generatedType?.parsedClasses?.forEach { parsedType ->
                                TypeItem(
                                    typeContent = parsedType.classBody,
                                    showCopyIcon = showCopyIcon,
                                    onCopyGeneratedTypeClicked = onCopyGeneratedTypeClicked
                                )
                            }
                        }

                    }
                }
            }
        }
    }
}

@Composable
fun JsonStructureContent(
    jsonInput: TextFieldState,
    jsonElement: JsonElement? = null,
    event: JsonSmithEvent? = null,
    onParseJsonStructureClicked: () -> Unit,
    onFormatJsonClicked: (jsonInput: TextFieldState) -> Unit,
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)){
            OutlinedButton(
                onClick = {
                    if (jsonInput.text.isNotBlank()) {
                        onParseJsonStructureClicked()
                    }
                }
            ) {
                Text("Parse")
            }

            OutlinedButton(
                onClick = {
                    if (jsonInput.text.isNotBlank()) {
                        onFormatJsonClicked(jsonInput)
                    }
                }
            ) {
                Text("Format")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        AnimatedVisibility(visible = jsonElement != null) {
            VerticallyScrollableContainer {
                jsonElement?.let {
                    JsonStructure(jsonElement = it)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TypeItem(
    typeContent: String,
    showCopyIcon: Boolean,
    onCopyGeneratedTypeClicked: (type: String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
        .padding(top = 8.dp, bottom = 8.dp)
) {
    Column(modifier = modifier) {
        if (showCopyIcon) {
            IconActionButton(
                key = AllIconsKeys.Actions.Copy,
                contentDescription = "Copy",
                onClick = {
                    if (typeContent.isNotBlank()) {
                        onCopyGeneratedTypeClicked(typeContent)
                    }
                },
                tooltip = {
                    Text(text = "Copy")
                }
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        SelectionContainer {
            Text(
                text = typeContent.trimIndent(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun JsonStructure(jsonElement: JsonElement, modifier: Modifier = Modifier.padding(10.dp)) {
    JsonHeader(jsonElement = jsonElement, modifier = modifier)
}

@Composable
private fun JsonHeader(jsonElement: JsonElement, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                key = if (expanded) AllIconsKeys.Profiler.CollapseNode else AllIconsKeys.Profiler.ExpandNode,
                contentDescription = "Toggle",
                modifier = Modifier.size(16.dp)
            )

            val jsonTypeIconKey = when (jsonElement) {
                is JsonObject -> AllIconsKeys.Json.Object
                is JsonArray -> AllIconsKeys.Json.Array
                else -> null
            }


            jsonTypeIconKey?.let {
                Icon(key = it, contentDescription = "Type")
            }

            val node = buildString {
                when (jsonElement) {
                    is JsonArray -> append("Array {${jsonElement.size} items}")
                    is JsonObject -> append("Object {${jsonElement.entries.size} items}")
                    else -> {}
                }

            }
            SelectionContainer {
                Text(
                    text = node,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        AnimatedVisibility(visible = expanded, modifier = Modifier.padding(start = 12.dp)) {
            when (jsonElement) {
                is JsonObject -> JsonObjectTree(jsonObject = jsonElement)
                is JsonArray -> JsonArrayTree(jsonArray = jsonElement)
                else -> {}
            }
        }
    }
}

@Composable
private fun JsonObjectTree(jsonObject: JsonObject, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        jsonObject.entries.forEachIndexed { index: Int, entry: Map.Entry<String, JsonElement> ->
            JsonObjectItem(key = entry.key, type = getJsonType(entry.value), jsonElement = entry.value, index = index)
        }
    }
}

@Composable
private fun JsonArrayTree(jsonArray: JsonArray, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        jsonArray.forEachIndexed { index, jsonElement ->
            when (jsonElement) {
                is JsonObject -> JsonObjectItem(
                    type = getJsonType(jsonElement),
                    index = index,
                    jsonElement = jsonElement,
                    fromJsonArray = true
                )

                is JsonLiteral -> JsonLeaf(
                    key = index.toString(),
                    value = jsonElement.jsonPrimitive.content,
                    jsonPrimitive = jsonElement.jsonPrimitive
                )

                is JsonPrimitive -> JsonLeaf(
                    key = index.toString(),
                    value = jsonElement.jsonPrimitive.content,
                    jsonPrimitive = jsonElement.jsonPrimitive
                )

                is JsonArray -> JsonArrayTree(jsonArray = jsonElement)
            }
        }
    }
}

@Composable
private fun JsonObjectItem(
    index: Int,
    key: String? = null,
    type: String,
    jsonElement: JsonElement,
    fromJsonArray: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isNestedJson: Boolean = jsonElement is JsonObject || jsonElement is JsonArray

    Column(modifier = modifier) {
        val clickableModifier = if (isNestedJson) Modifier.clickable { expanded = !expanded } else Modifier
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(clickableModifier)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isNestedJson) {
                Icon(
                    key = if (expanded) AllIconsKeys.Profiler.CollapseNode else AllIconsKeys.Profiler.ExpandNode,
                    contentDescription = "Toggle",
                    modifier = Modifier.size(16.dp)
                )
            }

            val jsonTypeIconKey = when (jsonElement) {
                is JsonArray -> AllIconsKeys.Json.Array
                is JsonObject -> AllIconsKeys.Json.Object
                else -> null
            }

            jsonTypeIconKey?.let {
                Icon(key = it, contentDescription = "Type")
            }

            val itemCount: Int? = when (jsonElement) {
                is JsonArray -> jsonElement.size
                is JsonObject -> jsonElement.entries.size
                else -> null
            }

            if (isNestedJson) {
                val node = buildString {
                    if (fromJsonArray) {
                        append("[$index] ")
                    }
                    key?.let {
                        append(it)
                    }
                    append(" ($type)")
                    itemCount?.let { count ->
                        append(" ($count items) ")
                    }
                }
                SelectionContainer {
                    Text(
                        text = node,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                JsonLeaf(
                    key = key.orEmpty(),
                    value = jsonElement.jsonPrimitive.content,
                    jsonPrimitive = jsonElement.jsonPrimitive
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                when (jsonElement) {
                    is JsonObject -> jsonElement.entries.forEachIndexed { index: Int, entry: Map.Entry<String, JsonElement> ->
                        JsonObjectItem(
                            key = entry.key,
                            type = getJsonType(jsonElement),
                            jsonElement = entry.value,
                            index = index
                        )
                    }

                    is JsonArray -> jsonElement.forEachIndexed { index, value ->
                        if (value is JsonObject) {
                            JsonObjectItem(
                                key = key,
                                type = getJsonType(value),
                                jsonElement = value,
                                index = index,
                                fromJsonArray = true
                            )
                        }
                    }

                    is JsonLiteral -> {
                        JsonLeaf(
                            key = key.orEmpty(),
                            value = jsonElement.jsonPrimitive.content,
                            jsonElement.jsonPrimitive
                        )
                    }

                    else -> {}
                }
            }
        }

    }
}


@Composable
private fun JsonLeaf(
    key: String,
    value: String,
    jsonPrimitive: JsonPrimitive,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .padding(2.dp)
) {

    val leafContent = buildString {
        append("$key: $value (${getJsonType(jsonPrimitive)})")
    }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                painter = org.jetbrains.compose.resources.painterResource(iconRes),
                contentDescription = "Icon",
                modifier = Modifier.size(16.dp)
            )
        }

        SelectionContainer {
            Text(
                text = leafContent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier
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


