package com.github.efeegbevwie.jsonsmith.services

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndSelectAll
import androidx.compose.runtime.snapshotFlow
import com.efe.jsonSmith.languageParsers.ParsedType
import com.efe.jsonSmith.languageParsers.parseJsonToGoStruct
import com.efe.jsonSmith.languageParsers.parseJsonToJavaClass
import com.efe.jsonSmith.languageParsers.parseJsonToKotlinClass
import com.efe.jsonSmith.targetLanguages.TargetLanguage
import com.efe.jsonSmith.targetLanguages.TargetLanguageConfig
import com.github.efeegbevwie.jsonsmith.models.JsonSmithEvent
import com.github.efeegbevwie.jsonsmith.models.JsonTreeItem
import com.github.efeegbevwie.jsonsmith.services.fileSavers.SaveFileResult
import com.github.efeegbevwie.jsonsmith.services.fileSavers.saveGeneratedTypesToFiles
import com.github.efeegbevwie.jsonsmith.util.toClassNameCamelCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import kotlin.time.Duration.Companion.seconds


@Service(Service.Level.PROJECT)
class MyProjectService(val project: Project, private val serviceCoroutineScope: CoroutineScope) {

    private val json = Json { prettyPrint = true; }

    private val targetLanguageFlow = MutableStateFlow<TargetLanguage>(TargetLanguage.Kotlin())
    val targetLanguage: StateFlow<TargetLanguage> = targetLanguageFlow.asStateFlow()

    private val generatedTypeFlow = MutableStateFlow<ParsedType?>(null)
    val generatedType = generatedTypeFlow.asStateFlow()

    // For JSON structure
    private val jsonElementFlow = MutableStateFlow<JsonElement?>(null)
    val jsonElement: StateFlow<JsonElement?> = jsonElementFlow.asStateFlow()

    // For JSON tree expanded state
    private val expandedNodesFlow = MutableStateFlow<Set<String>>(emptySet())

    var jsonInput = TextFieldState()
    val jsonStructureInput = TextFieldState()
    val classNameInput = TextFieldState()

    // For JSON parsing
    private val jsonParsingEventsFlow = MutableStateFlow<JsonSmithEvent?>(null)
    val jsonParsingEvents: Flow<JsonSmithEvent?> = jsonParsingEventsFlow.asStateFlow()

    private val jsonStructureParsingEventsFlow = MutableStateFlow<JsonSmithEvent?>(null)
    val jsonStructureParsingEvents: StateFlow<JsonSmithEvent?> = jsonStructureParsingEventsFlow.asStateFlow()

    private val flattenedJsonItemsFlow = MutableStateFlow< List<JsonTreeItem>>(emptyList())
    val flattenedJsonItems = flattenedJsonItemsFlow.asStateFlow()


    init {
        with(serviceCoroutineScope) {
            launch { observeClassTitleText() }
            launch { observeTargetLanguageChanges() }
            launch {
                jsonParsingEvents.collect { event ->
                    if (event == null) return@collect
                    if (event.timeOut.inWholeSeconds > 0.seconds.inWholeSeconds) {
                        timeOutEvent(event = event, eventsFlow = jsonParsingEventsFlow)
                    }
                }
            }
            launch {
                jsonStructureParsingEvents.collect { event ->
                    if (event == null) return@collect
                    if (event.timeOut.inWholeSeconds > 0.seconds.inWholeSeconds) {
                        timeOutEvent(event = event, eventsFlow = jsonStructureParsingEventsFlow)
                    }
                }
            }
        }
    }

    fun getRandomNumber() = (1..100).random()

    fun setTargetLanguage(language: TargetLanguage) {
        targetLanguageFlow.update { language }
        if (generatedTypeFlow.value?.stringRepresentation?.isNotEmpty() == true) {
            generateTypeFromJson(json = jsonInput.text.toString())
        }
    }

    fun generateTypeFromJson(json: String) {
        jsonParsingEventsFlow.removeErrorParsingState()
        val generatedType = when (val targetLanguage = targetLanguageFlow.value) {
            is TargetLanguage.Java -> parseJsonToJavaClass(
                className = targetLanguage.targetLanguageConfig.className.ifEmpty { "JsonClass" },
                json = json,
                javaConfig = targetLanguage.targetLanguageConfig as TargetLanguage.Java.JavaConfigOptions
            )

            is TargetLanguage.Kotlin -> parseJsonToKotlinClass(
                className = targetLanguage.targetLanguageConfig.className.ifEmpty { "JsonClass" },
                json = json,
                kotlinConfig = targetLanguage.targetLanguageConfig as TargetLanguage.Kotlin.KotlinConfigOptions
            )

            is TargetLanguage.Go -> parseJsonToGoStruct(
                json = json,
                structName = targetLanguage.targetLanguageConfig.className.ifEmpty { "JsonStruct" },
                goConfig = targetLanguage.targetLanguageConfig as TargetLanguage.Go.GoConfigOptions
            )
        }

        if (generatedType == null) {
            serviceCoroutineScope.launch {
                jsonParsingEventsFlow.sendParsingError()
            }
        } else {
            generatedTypeFlow.update { generatedType }
        }
    }
    fun copyToClipboard(s: String) {
        val selection = StringSelection(s)
        runCatching {
            ApplicationManager.getApplication().invokeLater {
                val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(selection, selection)
            }
        }.onSuccess {
            serviceCoroutineScope.launch {
                jsonParsingEventsFlow.sendContentCopiedEvent()
            }
        }
    }
    fun saveGeneratedType(project: Project) {
        generatedTypeFlow.value?.let { parsedType ->
            val filesSaved: SaveFileResult = runCatching {
                saveGeneratedTypesToFiles(
                    parsedType = parsedType,
                    targetLanguage = targetLanguageFlow.value,
                    project = project
                )
            }.getOrElse {
                serviceCoroutineScope.launch {
                    jsonParsingEventsFlow.sendFileSavedError()
                }
                SaveFileResult.Failure
            }
            when (filesSaved) {
                SaveFileResult.Success -> {
                    serviceCoroutineScope.launch {
                        jsonParsingEventsFlow.sendFileSavedEvent()
                    }
                }

                SaveFileResult.Failure -> {
                    serviceCoroutineScope.launch {
                        jsonParsingEventsFlow.sendFileSavedError()
                    }
                }

                SaveFileResult.Cancelled -> {}
            }
        }
    }

    fun formatJson(jsonContent: TextFieldState) {
        try {
            if (jsonContent.text.toString().isBlank()) return
            val jsonElement = json.parseToJsonElement(jsonContent.text.toString())
            val formattedJson = json.encodeToString(JsonElement.serializer(), jsonElement).trim()
            jsonContent.setTextAndSelectAll(formattedJson)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



    fun updateTargetLanguageConfig(newConfig: TargetLanguageConfig) {
        targetLanguageFlow.update { currentTargetLanguage ->
            when (currentTargetLanguage) {
                is TargetLanguage.Java -> TargetLanguage.Java(newConfig)
                is TargetLanguage.Kotlin -> TargetLanguage.Kotlin(newConfig)
                is TargetLanguage.Go -> TargetLanguage.Go(newConfig)
            }
        }
        if (generatedTypeFlow.value?.stringRepresentation?.isNotEmpty() == true) {
            generateTypeFromJson(json = jsonInput.text.toString())
        }
    }


    fun parseJsonStructure(json: String = jsonStructureInput.text.toString()) {
        try {
            jsonStructureParsingEventsFlow.removeErrorParsingState()
            val element = Json.parseToJsonElement(json)
            jsonElementFlow.update { element }
            // Clear expanded nodes when parsing a new JSON structure
            expandedNodesFlow.update { emptySet() }
            updateFlattenedJson(jsonElement = element)
        } catch (e: Exception) {
            serviceCoroutineScope.launch {
                jsonStructureParsingEventsFlow.sendParsingError()
            }
            e.printStackTrace()
        }
    }

    /**
     * Toggle the expanded state of a JSON node
     * @param nodePath The path to the node in the JSON tree
     */
    fun toggleNodeExpanded(nodePath: String) {
        expandedNodesFlow.update { currentExpandedNodes ->
            if (currentExpandedNodes.contains(nodePath)) {
                // When collapsing a node, also collapse all its children
                val childrenToRemove = currentExpandedNodes.filter { childPath ->
                    childPath != nodePath && (
                        childPath.startsWith("$nodePath.") || // Object children
                        childPath.startsWith("$nodePath[")     // Array children
                    )
                }
                currentExpandedNodes - nodePath - childrenToRemove
            } else {
                // When expanding a node, just add it to the expanded set
                // (don't automatically expand children)
                currentExpandedNodes + nodePath
            }
        }
        updateFlattenedJson(jsonElement = jsonElementFlow.value)
    }

    private fun updateFlattenedJson(jsonElement: JsonElement?){
        if (jsonElement == null) return
        val newFlattenedJson = flattenJsonTree(jsonElement = jsonElement, expandedNodes = expandedNodesFlow.value)
        flattenedJsonItemsFlow.update { newFlattenedJson }
    }


    /**
     * Flattens a hierarchical JSON tree structure into a list of `JsonTreeItem` instances. The method processes
     * JSON objects, arrays, and primitives, mapping each element to a flat representation that includes its path,
     * depth level, key (if present), and expansion state for UI usage.
     *
     * @param jsonElement The root `JsonElement` to flatten. This can be a `JsonObject`, `JsonArray`, or `JsonPrimitive`.
     * @param expandedNodes A set of node paths that are marked as expanded. Determines whether nested elements should be included.
     * @param nodePath The current node path in the JSON tree. Defaults to "root" for the top-level element.
     * @param level The depth level of the current node within the JSON tree structure. Defaults to 0 for the root.
     * @param key The key associated with the current node, if it is part of an object. Defaults to null.
     * @param fromArray A flag indicating whether the current node originated from within an array. Defaults to false.
     * @param arrayIndex The index of the current node if it is a part of an array. Defaults to null.
     * @return A list of `JsonTreeItem` instances representing the flattened JSON tree structure.
     */
    private fun flattenJsonTree(
        jsonElement: JsonElement,
        expandedNodes: Set<String>,
        nodePath: String = "root",
        level: Int = 0,
        key: String? = null,
        fromArray: Boolean = false,
        arrayIndex: Int? = null
    ): List<JsonTreeItem> {
        val result = mutableListOf<JsonTreeItem>()

        when (jsonElement) {
            is JsonObject -> {
                // Add the object item itself
                val isExpanded = expandedNodes.contains(nodePath)
                result.add(
                    JsonTreeItem.ObjectItem(
                        nodePath = nodePath,
                        level = level,
                        expanded = isExpanded,
                        jsonObject = jsonElement,
                        key = key,
                        fromArray = fromArray,
                        arrayIndex = arrayIndex
                    )
                )

                // If this node is expanded, add its children
                if (expandedNodes.contains(nodePath)) {
                    jsonElement.entries.forEachIndexed { index, entry ->
                        val childPath = "$nodePath.${entry.key}"
                        result.addAll(
                            flattenJsonTree(
                                jsonElement = entry.value,
                                expandedNodes = expandedNodes,
                                nodePath = childPath,
                                level = level + 1,
                                key = entry.key
                            )
                        )
                    }
                }
            }

            is JsonArray -> {
                // Add the array item itself
                val isExpanded = expandedNodes.contains(nodePath)
                result.add(
                    JsonTreeItem.ArrayItem(
                        nodePath = nodePath,
                        level = level,
                        expanded = isExpanded,
                        jsonArray = jsonElement,
                        key = key,
                        fromArray = fromArray,
                        arrayIndex = arrayIndex
                    )
                )

                // If this node is expanded, add its children
                if (expandedNodes.contains(nodePath)) {
                    jsonElement.forEachIndexed { index, element ->
                        val childPath = "$nodePath[$index]"
                        result.addAll(
                            flattenJsonTree(
                                jsonElement = element,
                                expandedNodes = expandedNodes,
                                nodePath = childPath,
                                level = level + 1,
                                fromArray = true,
                                arrayIndex = index
                            )
                        )
                    }
                }
            }

            is JsonPrimitive -> {
                // Add the primitive item
                result.add(
                    JsonTreeItem.PrimitiveItem(
                        nodePath = nodePath,
                        level = level,
                        jsonPrimitive = jsonElement,
                        key = key ?: arrayIndex.toString(),
                        fromArray = fromArray,
                        arrayIndex = arrayIndex
                    )
                )
            }
        }

        return result
    }



    private fun timeOutEvent(event: JsonSmithEvent, eventsFlow: MutableStateFlow<JsonSmithEvent?>) {
        CoroutineScope(Dispatchers.Default).launch {
            delay(event.timeOut)
            if (eventsFlow.value?.equals(event) == true) {
                eventsFlow.emit(null)
            }
        }
    }

    private suspend fun observeClassTitleText() {
        snapshotFlow { classNameInput.text }.collect { newClassName ->
            jsonParsingEventsFlow.removeErrorParsingState()
            val formattedClassName: String = newClassName.toString().toClassNameCamelCase()
            val newLanguageConfig = when (val currentLanguageConfig = targetLanguageFlow.value) {
                is TargetLanguage.Java -> {
                    val newJavaConfig =
                        (currentLanguageConfig.targetLanguageConfig as TargetLanguage.Java.JavaConfigOptions).copy(
                            className = formattedClassName
                        )

                    currentLanguageConfig.copy(targetLanguageConfig = newJavaConfig)
                }

                is TargetLanguage.Kotlin -> {
                    val newKotlinConfig =
                        (currentLanguageConfig.targetLanguageConfig as TargetLanguage.Kotlin.KotlinConfigOptions).copy(
                            className = formattedClassName
                        )
                    currentLanguageConfig.copy(targetLanguageConfig = newKotlinConfig)
                }

                is TargetLanguage.Go -> {
                    val newGoConfig =
                        (currentLanguageConfig.targetLanguageConfig as TargetLanguage.Go.GoConfigOptions).copy(
                            className = formattedClassName
                        )
                    currentLanguageConfig.copy(targetLanguageConfig = newGoConfig)
                }
            }
            targetLanguageFlow.update { newLanguageConfig }
        }
    }

    private fun MutableStateFlow<JsonSmithEvent?>.removeErrorParsingState() {
        if (this.value is JsonSmithEvent.JsonParsingFailed) {
            this.update { null }
        }
    }

    private suspend fun observeTargetLanguageChanges() {
        targetLanguageFlow.collect {
            val jsonHasBeenParsedAtLeastOnce = generatedTypeFlow.value != null
            if (jsonHasBeenParsedAtLeastOnce) generateTypeFromJson(json = jsonInput.text.toString())
        }
    }

    private suspend fun MutableStateFlow<JsonSmithEvent?>.sendParsingError() {
        emit(JsonSmithEvent.JsonParsingFailed())
    }

    private suspend fun MutableStateFlow<JsonSmithEvent?>.sendContentCopiedEvent() {
        emit(JsonSmithEvent.ContentCopied())
    }

    private suspend fun MutableStateFlow<JsonSmithEvent?>.sendFileSavedEvent() {
        emit(JsonSmithEvent.FileSaved())
    }

    private suspend fun MutableStateFlow<JsonSmithEvent?>.sendFileSavedError() {
        emit(JsonSmithEvent.FileSavedError())
    }
}

