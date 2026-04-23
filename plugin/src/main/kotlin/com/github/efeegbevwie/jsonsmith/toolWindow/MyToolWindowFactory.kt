package com.github.efeegbevwie.jsonsmith.toolWindow

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import com.efe.jsonSmith.parser.languageParsers.ParsedType
import com.efe.jsonSmith.parser.structureParser.JsonArrayStructure
import com.efe.jsonSmith.parser.targetLanguages.TargetLanguage
import com.github.efeegbevwie.jsonsmith.models.FilterOperation
import com.github.efeegbevwie.jsonsmith.models.JsonTreeItem
import com.github.efeegbevwie.jsonsmith.services.MyProjectService
import com.github.efeegbevwie.jsonsmith.models.JsonSmithEvent
import com.github.efeegbevwie.jsonsmith.services.JsonQueryState
import com.github.efeegbevwie.jsonsmith.services.JsonStructureSearchResults
import com.github.efeegbevwie.jsonsmith.services.JsonStructureState
import com.github.efeegbevwie.jsonsmith.services.JsonTypeGeneratorState
import kotlinx.serialization.json.JsonArray
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme


class MyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val service = toolWindow.project.service<MyProjectService>()

        toolWindow.addComposeTab(tabDisplayName = "Parse") {
            val typeGeneratorState: JsonTypeGeneratorState by service.jsonTypeGeneratorState.collectAsState()
            val generatedType: ParsedType? = typeGeneratorState.generatedType
            val targetLanguage: TargetLanguage = typeGeneratorState.targetLanguage
            val event: JsonSmithEvent? by service.jsonParsingEvents.collectAsState(initial = null)

            SwingBridgeTheme {
                JsonParsingToolWindowContent(
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
            val jsonStructureEvents: JsonSmithEvent? by service.jsonStructureParsingEvents.collectAsState(initial = null)
            val jsonStructureState: JsonStructureState by service.jsonStructureState.collectAsState()
            val flattenedJson: List<JsonTreeItem> = jsonStructureState.flattenedJson
            val searchState: JsonStructureSearchResults = jsonStructureState.searchResults

            val jsonStructureUiEvents = object : JsonStructureUiEvents {
                override fun onParseJsonStructureClicked() {
                    service.parseJsonStructure()
                }

                override fun onFormatJsonClicked(jsonInput: TextFieldState) {
                    service.formatJson(jsonContent = jsonInput)
                }

                override fun onNavigateToNextMatch(): Boolean {
                    return service.navigateToNextMatch()
                }

                override fun onNavigateToPreviousMatch(): Boolean {
                    return service.navigateToPreviousMatch()
                }

                override fun onNodeExpandedToggle(nodePath: String) {
                    service.toggleNodeExpanded(nodePath)
                }

                override fun onCancelSearch() {
                    service.clearSearchQuery()
                }

                override fun jsonItemMatchesSearch(
                    item: JsonTreeItem,
                    query: String
                ): Boolean {
                    return service.itemMatchesSearch(item = item, query = query)
                }
            }

            SwingBridgeTheme {
                JsonStructureContent(
                    jsonInput = service.jsonInput,
                    flattenedJson = flattenedJson,
                    event = jsonStructureEvents,
                    searchResults = searchState,
                    lazyListState = service.jsonTreeLazyListState,
                    jsonSearchTextState = service.jsonSearchQueryState,
                    events = jsonStructureUiEvents,
                )
            }
        }

        toolWindow.addComposeTab(tabDisplayName = "Query") {
            val jsonQueryState: JsonQueryState by service.jsonQueryState.collectAsState()
            val jsonArrayStructure: JsonArrayStructure? = jsonQueryState.jsonArrayStructure
            val filteredJsonArray: JsonArray? = jsonQueryState.filteredJsonArray
            val selectedFilterOperation: FilterOperation? = jsonQueryState.selectedFilterOperation
            val suggestions: List<String> = jsonQueryState.queryKeySuggestions
            val encodedJson: String? = jsonQueryState.encodedJson
            val availableOperations: List<FilterOperation> = jsonQueryState.availableOperations
            val isAutoCompleteVisible = jsonQueryState.isJsonFilterSuggestionsVisible

            SwingBridgeTheme {
                JsonQueryUi(
                    jsonInput = service.jsonInput,
                    jsonArrayStructure = jsonArrayStructure,
                    filteredJsonArray = filteredJsonArray,
                    filterKeyState = service.jsonQueryKeyState,
                    filterValueState = service.filterValueState,
                    filterSecondValueState = service.filterSecondValueState,
                    selectedFilterOperation = selectedFilterOperation,
                    suggestions = suggestions,
                    encodedJson = encodedJson,
                    isAutocompleteDropdownVisible = isAutoCompleteVisible,
                    availableOperations = availableOperations,
                    onParseJsonClicked = {
                        service.parseJsonForQuery()
                    },
                    onFilterOperationSelected = { operation ->
                        service.setSelectedFilterOperation(operation)
                    },
                    onApplyFilterClicked = {
                        service.applyFilter()
                    },
                    onClearFilerClicked = {
                        service.resetFilterState()
                    },
                    onSuggestionSelected = {
                        service.onFilterSuggestionSelected(it)
                    }
                )
            }
        }
    } 

    override fun shouldBeAvailable(project: Project) = true
}
