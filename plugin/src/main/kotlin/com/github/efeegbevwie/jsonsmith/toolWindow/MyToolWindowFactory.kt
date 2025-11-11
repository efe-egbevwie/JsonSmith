package com.github.efeegbevwie.jsonsmith.toolWindow

import androidx.compose.runtime.*
import com.efe.jsonSmith.parser.languageParsers.ParsedType
import com.efe.jsonSmith.parser.targetLanguages.TargetLanguage
import com.github.efeegbevwie.jsonsmith.models.JsonTreeItem
import com.github.efeegbevwie.jsonsmith.services.MyProjectService
import com.github.efeegbevwie.jsonsmith.models.JsonSmithEvent
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
            val generatedType: ParsedType? by service.generatedType.collectAsState()
            val targetLanguage: TargetLanguage by service.targetLanguage.collectAsState()
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
            val flattenedJson: List<JsonTreeItem> by service.flattenedJsonItems.collectAsState()

            SwingBridgeTheme {
                JsonStructureContent(
                    jsonInput = service.jsonInput,
                    flattenedJson = flattenedJson,
                    event = jsonStructureEvents,
                    onNodeExpandedToggle = { nodePath -> service.toggleNodeExpanded(nodePath) },
                    onFormatJsonClicked = { service.formatJson(it) },
                    onParseJsonStructureClicked = { service.parseJsonStructure() }
                )
            }
        }
    }

    override fun shouldBeAvailable(project: Project) = true
}
