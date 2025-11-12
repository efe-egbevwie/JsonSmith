package com.github.efeegbevwie.jsonsmith.services

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.setTextAndSelectAll
import androidx.compose.runtime.snapshotFlow
import com.efe.jsonSmith.parser.languageParsers.ParsedType
import com.efe.jsonSmith.parser.languageParsers.parseJsonToGoStruct
import com.efe.jsonSmith.parser.languageParsers.parseJsonToJavaClass
import com.efe.jsonSmith.parser.languageParsers.parseJsonToKotlinClass
import com.efe.jsonSmith.parser.targetLanguages.TargetLanguage
import com.efe.jsonSmith.parser.targetLanguages.TargetLanguage.*
import com.efe.jsonSmith.parser.targetLanguages.TargetLanguageConfig
import com.github.efeegbevwie.jsonsmith.models.JsonSmithEvent
import com.github.efeegbevwie.jsonsmith.models.JsonTreeItem
import com.github.efeegbevwie.jsonsmith.models.SearchState
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
import kotlinx.serialization.json.*
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import kotlin.time.Duration.Companion.seconds


@Service(Service.Level.PROJECT)
class MyProjectService(val project: Project, private val serviceCoroutineScope: CoroutineScope) {

    private val json = Json { prettyPrint = true; }

    private val targetLanguageFlow = MutableStateFlow<TargetLanguage>(Kotlin())
    val targetLanguage: StateFlow<TargetLanguage> = targetLanguageFlow.asStateFlow()

    private val generatedTypeFlow = MutableStateFlow<ParsedType?>(null)
    val generatedType = generatedTypeFlow.asStateFlow()

    // For JSON structure
    private val jsonElementFlow = MutableStateFlow<JsonElement?>(null)

    // For JSON tree expanded state
    private val expandedNodesFlow = MutableStateFlow<Set<String>>(emptySet())

    var jsonInput = TextFieldState()
    val classNameInput = TextFieldState()

    // For JSON parsing
    private val jsonParsingEventsFlow = MutableStateFlow<JsonSmithEvent?>(null)
    val jsonParsingEvents: Flow<JsonSmithEvent?> = jsonParsingEventsFlow.asStateFlow()

    private val jsonStructureParsingEventsFlow = MutableStateFlow<JsonSmithEvent?>(null)
    val jsonStructureParsingEvents: StateFlow<JsonSmithEvent?> = jsonStructureParsingEventsFlow.asStateFlow()

    private val flattenedJsonItemsFlow = MutableStateFlow<List<JsonTreeItem>>(emptyList())
    val flattenedJsonItems = flattenedJsonItemsFlow.asStateFlow()

    // Search functionality
    private val searchStateFlow = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = searchStateFlow.asStateFlow()

    val jsonSearchQueryState = TextFieldState()
    val jsonTreeLazyListState = LazyListState()


    init {
        with(serviceCoroutineScope) {
            launch { observeClassTitleText() }
            launch { observeTargetLanguageChanges() }
            launch { observeSearchQueryChanges() }
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


    fun setTargetLanguage(language: TargetLanguage) {
        targetLanguageFlow.update { language }
        if (generatedTypeFlow.value?.stringRepresentation?.isNotEmpty() == true) {
            generateTypeFromJson(json = jsonInput.text.toString())
        }
    }

    fun generateTypeFromJson(json: String) {
        jsonParsingEventsFlow.removeErrorParsingState()
        val generatedType = when (val targetLanguage = targetLanguageFlow.value) {
            is Java -> parseJsonToJavaClass(
                className = targetLanguage.targetLanguageConfig.className.ifEmpty { "JsonClass" },
                json = json,
                javaConfig = targetLanguage.targetLanguageConfig as Java.JavaConfigOptions
            )

            is Kotlin -> parseJsonToKotlinClass(
                className = targetLanguage.targetLanguageConfig.className.ifEmpty { "JsonClass" },
                json = json,
                kotlinConfig = targetLanguage.targetLanguageConfig as Kotlin.KotlinConfigOptions
            )

            is Go -> parseJsonToGoStruct(
                json = json,
                structName = targetLanguage.targetLanguageConfig.className.ifEmpty { "JsonStruct" },
                goConfig = targetLanguage.targetLanguageConfig as Go.GoConfigOptions
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
                is Java -> Java(newConfig)
                is Kotlin -> Kotlin(newConfig)
                is Go -> Go(newConfig)
            }
        }
        if (generatedTypeFlow.value?.stringRepresentation?.isNotEmpty() == true) {
            generateTypeFromJson(json = jsonInput.text.toString())
        }
    }


    fun parseJsonStructure(json: String = jsonInput.text.toString()) {
        try {
            jsonStructureParsingEventsFlow.removeErrorParsingState()
            val element = Json.parseToJsonElement(json)
            jsonElementFlow.update { element }

            expandedNodesFlow.update { emptySet() }
            updateFlattenedJson(jsonElement = element)
            resetSearchData()
        } catch (e: Exception) {
            serviceCoroutineScope.launch {
                jsonStructureParsingEventsFlow.sendParsingError()
            }
            e.printStackTrace()
        }
    }

    fun toggleNodeExpanded(nodePath: String) {
        expandedNodesFlow.update { currentExpandedNodes ->
            if (currentExpandedNodes.contains(nodePath)) {
                // When collapsing a node, also collapse all its children
                val childrenToRemove = currentExpandedNodes.filter { childPath ->
                    childPath != nodePath && (
                            childPath.startsWith("$nodePath.") ||
                                    childPath.startsWith("$nodePath[")
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

    fun clearSearchQuery() {
        jsonSearchQueryState.setTextAndPlaceCursorAtEnd("")
        resetSearchData()
    }

    private fun updateFlattenedJson(jsonElement: JsonElement?) {
        if (jsonElement == null) return
        val newFlattenedJson = flattenJsonTree(jsonElement = jsonElement, expandedNodes = expandedNodesFlow.value)
        flattenedJsonItemsFlow.update { newFlattenedJson }
    }


    /**
     * Flattens a hierarchical JSON tree structure into a list of `JsonTreeItem` instances. The method processes
     * JSON objects, arrays, and primitives, mapping each element to a flat representation that includes its path,
     * depth level, key (if present), and expansion state for UI usage.
     */
    private fun flattenJsonTree(
        jsonElement: JsonElement,
        expandAll: Boolean = false,
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
                val isExpanded: Boolean = expandedNodes.contains(nodePath) || expandAll
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
                if (isExpanded) {
                    jsonElement.entries.forEachIndexed { index, entry ->
                        val childPath = "$nodePath.${entry.key}"
                        result.addAll(
                            flattenJsonTree(
                                expandAll = expandAll,
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
                val isExpanded = expandedNodes.contains(nodePath) || expandAll
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
                if (isExpanded) {
                    jsonElement.forEachIndexed { index, element ->
                        val childPath = "$nodePath[$index]"
                        result.addAll(
                            flattenJsonTree(
                                expandAll = expandAll,
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
                is Java -> {
                    val newJavaConfig =
                        (currentLanguageConfig.targetLanguageConfig as Java.JavaConfigOptions).copy(
                            className = formattedClassName
                        )

                    currentLanguageConfig.copy(targetLanguageConfig = newJavaConfig)
                }

                is Kotlin -> {
                    val newKotlinConfig =
                        (currentLanguageConfig.targetLanguageConfig as Kotlin.KotlinConfigOptions).copy(
                            className = formattedClassName
                        )
                    currentLanguageConfig.copy(targetLanguageConfig = newKotlinConfig)
                }

                is Go -> {
                    val newGoConfig =
                        (currentLanguageConfig.targetLanguageConfig as Go.GoConfigOptions).copy(
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


    private suspend fun observeSearchQueryChanges() {
        snapshotFlow { jsonSearchQueryState.text }.collect {
            delay(timeMillis = 500)
            val query: String = it.toString()
            if (query.isEmpty()) {
                if (flattenedJsonItems.value.any { jsonItem -> jsonItem.expanded }) {
                    return@collect
                } else {
                    collapseAllJsonItems()
                    resetSearchData()
                    return@collect
                }
            }
            expandAllJsonItems()
            val flattenedJson: List<JsonTreeItem> = flattenedJsonItems.value
            val matchedItemIndexes: List<Int> = flattenedJson.mapIndexed { index, item ->
                val matched = itemMatchesSearch(item = item, query = query)
                if (matched) index else null
            }.filterNotNull()

            val matchedItemIndex = if (matchedItemIndexes.isNotEmpty()) matchedItemIndexes.first() else null

            searchStateFlow.update { currentState ->
                currentState.copy(
                    matchedItemsIndices = matchedItemIndexes,
                    currentMatchIndex = 0,
                    matchedItemIndex = matchedItemIndex
                )
            }
        }
    }

    private fun expandAllJsonItems() {
        val jsonElement = jsonElementFlow.value ?: return
        val newFlattenedJson =
            flattenJsonTree(jsonElement = jsonElement, expandedNodes = expandedNodesFlow.value, expandAll = true)
        flattenedJsonItemsFlow.update { newFlattenedJson }
    }

    private fun collapseAllJsonItems() {
        val jsonElement = jsonElementFlow.value ?: return
        val newFlattenedJson =
            flattenJsonTree(jsonElement = jsonElement, expandedNodes = expandedNodesFlow.value, expandAll = false)
        flattenedJsonItemsFlow.update { newFlattenedJson }
    }

    private fun resetSearchData() {
        searchStateFlow.update {
            it.copy(
                matchedItemsIndices = emptyList(),
                currentMatchIndex = 0,
                matchedItemIndex = null
            )
        }
    }


    fun navigateToNextMatch(): Boolean {
        val currentState: SearchState = searchStateFlow.value
        val matchedIndices: List<Int> = currentState.matchedItemsIndices
        val currentIndex: Int = currentState.currentMatchIndex

        if (matchedIndices.isEmpty() || currentIndex >= matchedIndices.size - 1) {
            return false
        }

        val nextIndex = currentIndex + 1
        searchStateFlow.update {
            it.copy(
                currentMatchIndex = nextIndex,
                matchedItemIndex = matchedIndices[nextIndex]
            )
        }
        return true
    }


    fun navigateToPreviousMatch(): Boolean {
        val currentState = searchStateFlow.value
        val matchedIndices = currentState.matchedItemsIndices
        val currentIndex = currentState.currentMatchIndex

        if (matchedIndices.isEmpty() || currentIndex <= 0) {
            return false
        }

        val previousIndex = currentIndex - 1
        searchStateFlow.update {
            it.copy(
                currentMatchIndex = previousIndex,
                matchedItemIndex = matchedIndices[previousIndex]
            )
        }
        return true
    }


    fun itemMatchesSearch(item: JsonTreeItem, query: String): Boolean {
        if (query.isBlank()) return false

        val lowercaseQuery = query.lowercase()

        return when (item) {
            is JsonTreeItem.ObjectItem -> {
                (item.key?.lowercase()?.contains(lowercaseQuery) ?: false)
            }

            is JsonTreeItem.ArrayItem -> {
                (item.key?.lowercase()?.contains(lowercaseQuery) ?: false)
            }

            is JsonTreeItem.PrimitiveItem -> {
                val keyMatches = item.key.lowercase().contains(lowercaseQuery)
                val valueMatches = item.jsonPrimitive.content.lowercase().contains(lowercaseQuery)
                keyMatches || valueMatches
            }
        }
    }
}
