package com.github.efeegbevwie.jsonsmith.services

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.setTextAndSelectAll
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import com.efe.jsonSmith.parser.languageParsers.ParsedType
import com.efe.jsonSmith.parser.languageParsers.parseJsonToGoStruct
import com.efe.jsonSmith.parser.languageParsers.parseJsonToJavaClass
import com.efe.jsonSmith.parser.languageParsers.parseJsonToKotlinClass
import com.efe.jsonSmith.parser.structureParser.JsonArrayItem
import com.efe.jsonSmith.parser.structureParser.JsonArrayStructure
import com.efe.jsonSmith.parser.structureParser.parseJsonArrayStructure
import com.efe.jsonSmith.parser.targetLanguages.TargetLanguage
import com.efe.jsonSmith.parser.targetLanguages.TargetLanguage.*
import com.efe.jsonSmith.parser.targetLanguages.TargetLanguageConfig
import com.github.efeegbevwie.jsonsmith.models.FilterOperation
import com.github.efeegbevwie.jsonsmith.models.JsonSmithEvent
import com.github.efeegbevwie.jsonsmith.models.JsonTreeItem
import com.github.efeegbevwie.jsonsmith.services.fileSavers.SaveFileResult
import com.github.efeegbevwie.jsonsmith.services.fileSavers.saveGeneratedTypesToFiles
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import dev.snipme.highlights.model.CodeHighlight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import kotlin.time.Duration.Companion.seconds

data class HighlightedJson(
    val highlights: List<CodeHighlight>,
    val textField: TextFieldValue,
    val annotatedString: AnnotatedString
)

data class JsonTypeGeneratorState(
    val typeName: String? = null,
    val targetLanguage: TargetLanguage = Kotlin(),
    val generatedType: ParsedType? = null,
)

data class JsonStructureSearchResults(
    val matchedItemsIndices: List<Int> = emptyList(),
    val currentMatchIndex: Int = 0,
    val matchedItemIndex: Int? = null
) {
    val hasMatches: Boolean get() = matchedItemsIndices.isNotEmpty()
    val hasNextMatch: Boolean get() = hasMatches && currentMatchIndex < matchedItemsIndices.size - 1
    val hasPreviousMatch: Boolean get() = hasMatches && currentMatchIndex > 0
    val currentMatchedIndex: Int get() = if (hasMatches) currentMatchIndex + 1 else 0
    val totalMatches: Int get() = matchedItemsIndices.size
}

data class JsonStructureState(
    val jsonElement: JsonElement? = null,
    val expandedNodes: Set<String> = emptySet(),
    val flattenedJson: List<JsonTreeItem> = emptyList(),
    val searchResults: JsonStructureSearchResults = JsonStructureSearchResults()
)

data class JsonQueryState(
    val jsonArrayStructure: JsonArrayStructure = JsonArrayStructure(),
    val selectedFilterOperation: FilterOperation? = null,
    val queryKeySuggestions: List<String> = emptyList(),
    val availableOperations: List<FilterOperation> = emptyList(),
    val isJsonFilterSuggestionsVisible: Boolean = false,
    val filteredJsonArray: JsonArray? = null,
){
    val jsonToDisplay: JsonArray? get() =  filteredJsonArray ?: jsonArrayStructure.originalJsonArray
    val encodedJson: String? get() = jsonToDisplay?.let { jsonToDisplay?.let { json.encodeToString(it) }  }
}

private val json = Json { prettyPrint = true; }


@Service(Service.Level.PROJECT)
class MyProjectService(val project: Project, private val serviceCoroutineScope: CoroutineScope) {

    private val jsonTypeGeneratorStateFlow = MutableStateFlow(JsonTypeGeneratorState())
    val jsonTypeGeneratorState get() = jsonTypeGeneratorStateFlow.asStateFlow()

    private val jsonStructureStateFlow = MutableStateFlow(JsonStructureState())
    val jsonStructureState get() = jsonStructureStateFlow.asStateFlow()

    private val jsonQueryStateFlow = MutableStateFlow(JsonQueryState())
    val jsonQueryState get() = jsonQueryStateFlow.asStateFlow()


    var jsonInput = TextFieldState()
    val classNameInput = TextFieldState()
    val jsonQueryKeyState = TextFieldState()
    val filterValueState = TextFieldState()
    val filterSecondValueState = TextFieldState()
    val jsonSearchQueryState = TextFieldState()
    val jsonTreeLazyListState = LazyListState()


    private val jsonParsingEventsFlow = MutableStateFlow<JsonSmithEvent?>(null)
    val jsonParsingEvents: Flow<JsonSmithEvent?> = jsonParsingEventsFlow.asStateFlow()

    private val jsonStructureParsingEventsFlow = MutableStateFlow<JsonSmithEvent?>(null)
    val jsonStructureParsingEvents: StateFlow<JsonSmithEvent?> = jsonStructureParsingEventsFlow.asStateFlow()

    private val jsonQueryEventsFlow = MutableStateFlow<JsonSmithEvent?>(null)
    val jsonQueryEvents: StateFlow<JsonSmithEvent?> = jsonStructureParsingEventsFlow.asStateFlow()


    init {
        with(serviceCoroutineScope) {
            launch { observeTypeTitleText() }
            launch { observeTargetLanguageChanges() }
            launch { observeSearchQueryChanges() }
            launch { observeJsonQueryKeyText() }
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
        jsonTypeGeneratorStateFlow.update {
            it.copy(targetLanguage = language)
        }
        val generatedType = jsonTypeGeneratorStateFlow.value.generatedType
        if (generatedType?.stringRepresentation?.isNotEmpty() == true) {
            generateTypeFromJson(json = jsonInput.text.toString())
        }
    }

    fun generateTypeFromJson(json: String) {
        jsonParsingEventsFlow.removeErrorParsingState()
        val className = jsonTypeGeneratorStateFlow.value.typeName ?: "JsonClass"
        val generatedType: ParsedType? = when (val targetLanguage = jsonTypeGeneratorStateFlow.value.targetLanguage) {
            is Java -> parseJsonToJavaClass(
                className = className,
                json = json,
                javaConfig = targetLanguage.targetLanguageConfig as Java.JavaConfigOptions
            )

            is Kotlin -> parseJsonToKotlinClass(
                className = className,
                json = json,
                kotlinConfig = targetLanguage.targetLanguageConfig as Kotlin.KotlinConfigOptions
            )

            is Go -> parseJsonToGoStruct(
                json = json,
                structName = className,
                goConfig = targetLanguage.targetLanguageConfig as Go.GoConfigOptions
            )
        }

        if (generatedType == null) {
            serviceCoroutineScope.launch {
                jsonParsingEventsFlow.sendParsingError()
            }
        } else {
            jsonTypeGeneratorStateFlow.update {
                it.copy(generatedType = generatedType)
            }
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
        val jsonTypeGeneratorState: JsonTypeGeneratorState = jsonTypeGeneratorStateFlow.value
        val generatedType: ParsedType? = jsonTypeGeneratorState.generatedType
        val targetLanguage = jsonTypeGeneratorState.targetLanguage

        generatedType?.let { parsedType ->
            val filesSaved: SaveFileResult = runCatching {
                saveGeneratedTypesToFiles(
                    parsedType = parsedType,
                    targetLanguage = targetLanguage,
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
        val currentTargetLanguage = jsonTypeGeneratorStateFlow.value.targetLanguage
        val updatedTargetLanguage: TargetLanguage = when (currentTargetLanguage) {
            is Java -> Java(newConfig)
            is Kotlin -> Kotlin(newConfig)
            is Go -> Go(newConfig)
        }
        jsonTypeGeneratorStateFlow.update {
            it.copy(
                targetLanguage = updatedTargetLanguage
            )
        }

        if (jsonTypeGeneratorStateFlow.value.generatedType?.stringRepresentation?.isNotEmpty() == true) {
            generateTypeFromJson(json = jsonInput.text.toString())
        }
    }


    fun parseJsonStructure(json: String = jsonInput.text.toString()) {
        try {
            jsonStructureParsingEventsFlow.removeErrorParsingState()
            val element = Json.parseToJsonElement(json)
            val currentExpandedNodes = jsonStructureStateFlow.value.expandedNodes
            val flattenedJson: List<JsonTreeItem> =
                flattenJsonTree(jsonElement = element, expandedNodes = currentExpandedNodes)

            jsonStructureStateFlow.update {
                it.copy(
                    jsonElement = element,
                    expandedNodes = emptySet(),
                    flattenedJson = flattenedJson
                )
            }
            resetSearchData()
        } catch (e: Exception) {
            serviceCoroutineScope.launch {
                jsonStructureParsingEventsFlow.sendParsingError()
            }
            e.printStackTrace()
        }
    }

    fun parseJsonForQuery() = serviceCoroutineScope.launch {
        try {
            val jsonString = jsonInput.text.toString()
            val jsonElement: JsonElement = json.parseToJsonElement(jsonString)
            val jsonArrayStructure: JsonArrayStructure = parseJsonArrayStructure(jsonArray = jsonElement.jsonArray)
            jsonQueryStateFlow.update {
                it.copy(jsonArrayStructure = jsonArrayStructure)
            }
            // Reset filter state when parsing new JSON
            resetFilterState()
            // Update suggestions and available operations
            updateSuggestions()
            updateAvailableOperations()
        } catch (e: Exception) {
            serviceCoroutineScope.launch {
                jsonQueryEventsFlow.sendParsingError()
            }
            e.printStackTrace()
        }
    }


    fun resetFilterState() {
        jsonQueryKeyState.setTextAndPlaceCursorAtEnd("")
        filterValueState.setTextAndPlaceCursorAtEnd("")
        filterSecondValueState.setTextAndPlaceCursorAtEnd("")

        // Reset suggestions and available operations
        val jsonArrayStructure = jsonQueryStateFlow.value.jsonArrayStructure
        val queryKeySuggestions: List<String> = jsonArrayStructure.items.map { it.key }
        jsonQueryStateFlow.update {
            it.copy(
                selectedFilterOperation = null,
                filteredJsonArray = null,
                availableOperations = emptyList(),
                queryKeySuggestions = queryKeySuggestions
            )
        }
    }

    fun setSelectedFilterOperation(operation: FilterOperation) {
        jsonQueryStateFlow.update {
            it.copy(selectedFilterOperation = operation)
        }
    }

    fun onFilterSuggestionSelected(selectedSuggestion: String) {
        jsonQueryKeyState.setTextAndPlaceCursorAtEnd(selectedSuggestion)
        updateSuggestionVisible(false)
    }

    /**
     * Updates the suggestions based on the current filter key text.
     * If the text is empty, shows all keys.
     * If the text is not empty, shows keys that contain the text.
     * If no keys match, shows all keys.
     */
    private fun updateSuggestions() {
        val jsonQueryState: JsonQueryState = jsonQueryStateFlow.value
        val userKeyInput = jsonQueryKeyState.text.toString().lowercase()
        val allKeys: List<String> = jsonQueryState.jsonArrayStructure.items.map { it.key }

        val filteredKeys = if (userKeyInput.isBlank()) {
            allKeys
        } else {
            val matchingKeys = allKeys.filter { it.lowercase().contains(userKeyInput.lowercase()) }
            if (matchingKeys.isEmpty()) allKeys else matchingKeys
        }
        jsonQueryStateFlow.update {
            it.copy(queryKeySuggestions = filteredKeys)
        }
    }

    private fun updateSuggestionVisible(isVisible: Boolean) {
        jsonQueryStateFlow.update {
            it.copy(isJsonFilterSuggestionsVisible = isVisible)
        }
    }

    /**
     * Updates the available operations based on the current filter key text.
     */
    private fun updateAvailableOperations() {
        val jsonQueryState = jsonQueryStateFlow.value
        val currentInput = jsonQueryKeyState.text.toString()

        val operations: List<FilterOperation> = if (currentInput.isBlank()) {
            emptyList()
        } else {
            val item: JsonArrayItem? = jsonQueryState.jsonArrayStructure.items.find { it.key == currentInput }
            if (item != null) {
                FilterOperation.getOperationsForType(item.valueType)
            } else {
                emptyList()
            }
        }
        jsonQueryStateFlow.update {
            it.copy(availableOperations = operations)
        }
    }

    /**
     * Observes changes to the filter key text and updates suggestions and available operations.
     */
    private suspend fun observeJsonQueryKeyText() {
        snapshotFlow { jsonQueryKeyState.text }.collect { text ->
            updateSuggestions()
            updateAvailableOperations()
            if (text.isNotEmpty() && jsonQueryStateFlow.value.queryKeySuggestions.contains(text.toString().lowercase())
                    .not()
            ) {
                updateSuggestionVisible(true)
            } else {
                updateSuggestionVisible(false)
            }
        }
    }


    /**
     * Applies the selected filter to the JSON tree structure.
     */
    fun applyFilter() {
        val jsonQueryState: JsonQueryState = jsonQueryStateFlow.value
        val jsonArrayStructure: JsonArrayStructure = jsonQueryState.jsonArrayStructure
        val key = jsonQueryKeyState.text.toString()
        val operation: FilterOperation = jsonQueryState.selectedFilterOperation ?: return

        try {
            val filterValue = if (operation is FilterOperation.NumberOperation.Between) {
                // For "between" operations, combine the two values
                "${filterValueState.text},${filterSecondValueState.text}"
            } else {
                filterValueState.text.toString()
            }

            val filteredArray =
                operation.apply(keyPath = key, jsonArrayStructure = jsonArrayStructure, filterValue = filterValue)
            jsonQueryStateFlow.update {
                it.copy(filteredJsonArray = filteredArray)
            }

        } catch (e: Exception) {
            serviceCoroutineScope.launch {
                jsonQueryEventsFlow.update {
                    JsonSmithEvent.JsonFilterFailed(
                        message = "Failed to apply filter: ${e.message}",
                        errorEvent = true,
                        timeOut = 3.seconds
                    )
                }
            }
            e.printStackTrace()
        }
    }

    fun toggleNodeExpanded(nodePath: String) {
        val existingExpandedNodes: Set<String> = jsonStructureStateFlow.value.expandedNodes
        val nodeIsExpanded: Boolean = existingExpandedNodes.contains(nodePath)
        if (nodeIsExpanded) {
            // Current node is expanded, to collapse it, we find it's children, remove the node and also it's children
            val childrenToRemove: List<String> = existingExpandedNodes.filter { childPath ->
                childPath != nodePath && (
                        childPath.startsWith("$nodePath.") ||
                                childPath.startsWith("$nodePath[")
                        )
            }

            val newExpandedNodes: Set<String> =  existingExpandedNodes.toMutableSet().apply {
                remove(nodePath)
                removeAll(childrenToRemove)
            }
            jsonStructureStateFlow.update {
                it.copy(expandedNodes = newExpandedNodes)
            }
        } else {
            // Current node is collapsed, to expand it, we only need to add it to the expanded nodes
            val newExpandedNodes: Set<String> = existingExpandedNodes + nodePath
            jsonStructureStateFlow.update {
                it.copy(expandedNodes = newExpandedNodes)
            }
        }
        updateFlattenedJson(jsonElement = jsonStructureStateFlow.value.jsonElement)
    }

    fun clearSearchQuery() {
        jsonSearchQueryState.setTextAndPlaceCursorAtEnd("")
        resetSearchData()
        collapseAllJsonItems()
    }

    private fun updateFlattenedJson(jsonElement: JsonElement?) {
        if (jsonElement == null) return
        val expandedNodes = jsonStructureStateFlow.value.expandedNodes
        val newFlattenedJson = flattenJsonTree(jsonElement = jsonElement, expandedNodes = expandedNodes)
        jsonStructureStateFlow.update {
            it.copy(flattenedJson = newFlattenedJson)
        }
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

    private suspend fun observeTypeTitleText() {
        snapshotFlow { classNameInput.text }.collect { newClassName ->
            if (newClassName.isBlank()) return@collect
            jsonParsingEventsFlow.removeErrorParsingState()
            jsonTypeGeneratorStateFlow.update {
                it.copy(typeName = newClassName.toString())
            }
            return@collect
        }
    }

    private fun MutableStateFlow<JsonSmithEvent?>.removeErrorParsingState() {
        if (this.value is JsonSmithEvent.JsonParsingFailed) {
            this.update { null }
        }
    }

    private suspend fun observeTargetLanguageChanges() {
        jsonTypeGeneratorStateFlow.collect {
            val generatedType = it.generatedType
            val jsonHasBeenParsedAtLeastOnce = generatedType != null
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
            val flattenedJson = jsonStructureStateFlow.value.flattenedJson
            if (query.isEmpty()) {
                if (flattenedJson.any { jsonItem -> jsonItem.expanded }) {
                    return@collect
                } else {
                    collapseAllJsonItems()
                    resetSearchData()
                    return@collect
                }
            }
            expandAllJsonItems()
            val matchedItemIndexes: List<Int> = flattenedJson.mapIndexed { index, item ->
                val matched = itemMatchesSearch(item = item, query = query)
                if (matched) index else null
            }.filterNotNull()

            val matchedItemIndex = if (matchedItemIndexes.isNotEmpty()) matchedItemIndexes.first() else null
            val newSearchResults: JsonStructureSearchResults = jsonStructureStateFlow.value.searchResults.copy(
                matchedItemsIndices = matchedItemIndexes,
                currentMatchIndex = 0,
                matchedItemIndex = matchedItemIndex
            )
            jsonStructureStateFlow.update {
                it.copy(searchResults = newSearchResults)
            }
        }
    }

    private fun expandAllJsonItems() {
        val jsonElement = jsonStructureStateFlow.value.jsonElement ?: return
        val expandedNodes = jsonStructureStateFlow.value.expandedNodes
        val newFlattenedJson =
            flattenJsonTree(jsonElement = jsonElement, expandedNodes = expandedNodes, expandAll = true)
        jsonStructureStateFlow.update {
            it.copy(flattenedJson = newFlattenedJson)
        }
    }

    private fun collapseAllJsonItems() {
        val jsonElement = jsonStructureStateFlow.value.jsonElement ?: return
        val expandedNodes = jsonStructureStateFlow.value.expandedNodes
        val newFlattenedJson =
            flattenJsonTree(jsonElement = jsonElement, expandedNodes = expandedNodes, expandAll = false)
        jsonStructureStateFlow.update {
            it.copy(flattenedJson = newFlattenedJson)
        }
    }

    private fun resetSearchData() {
        jsonStructureStateFlow.update {
            it.copy(searchResults = JsonStructureSearchResults())
        }
    }


    fun navigateToNextMatch(): Boolean {
        val currentSearchResults: JsonStructureSearchResults = jsonStructureStateFlow.value.searchResults
        val matchedIndices: List<Int> = currentSearchResults.matchedItemsIndices
        val currentIndex: Int = currentSearchResults.currentMatchIndex

        if (matchedIndices.isEmpty() || currentIndex >= matchedIndices.size - 1) {
            return false
        }
        val nextIndex = currentIndex + 1
        val newSearchResults: JsonStructureSearchResults = currentSearchResults.copy(
            currentMatchIndex = nextIndex,
            matchedItemIndex = matchedIndices[nextIndex]
        )
        jsonStructureStateFlow.update {
            it.copy(searchResults = newSearchResults)
        }
        return true
    }


    fun navigateToPreviousMatch(): Boolean {
        val currentSearchResults: JsonStructureSearchResults = jsonStructureStateFlow.value.searchResults
        val matchedIndices: List<Int> = currentSearchResults.matchedItemsIndices
        val currentIndex: Int = currentSearchResults.currentMatchIndex

        if (matchedIndices.isEmpty() || currentIndex <= 0) {
            return false
        }

        val previousIndex = currentIndex - 1
        val newSearchResults = currentSearchResults.copy(
            currentMatchIndex = previousIndex,
            matchedItemIndex = matchedIndices[previousIndex]
        )
        jsonStructureStateFlow.update {
            it.copy(searchResults = newSearchResults)
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
