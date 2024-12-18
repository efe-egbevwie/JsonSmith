package com.github.efeegbevwie.jsonsmith.toolWindow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.efeegbevwie.jsonsmith.services.MyProjectService
import com.github.efeegbevwie.jsonsmith.services.MyProjectService.JsonSmithEvent
import com.github.efeegbevwie.jsonsmith.services.languageParsers.ParsedType
import com.github.efeegbevwie.jsonsmith.services.targetLanguages.TargetLanguage
import com.github.efeegbevwie.jsonsmith.services.targetLanguages.TargetLanguageConfig
import com.github.efeegbevwie.jsonsmith.services.targetLanguages.displayName
import com.github.efeegbevwie.jsonsmith.services.targetLanguages.enabledTargetLanguages
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys


class MyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.addComposeTab {
            val service = toolWindow.project.service<MyProjectService>()
            val generatedType: ParsedType? by service.generatedType.collectAsState()
            val targetLanguage: TargetLanguage by service.targetLanguage.collectAsState()
            val event: JsonSmithEvent? by service.eventsFlow.collectAsState(initial = null)

            JsonSmithToolWindowContent(
                jsonInput = service.jsonInput,
                className = service.classNameInput,
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

    override fun shouldBeAvailable(project: Project) = true

}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JsonSmithToolWindowContent(
    jsonInput: TextFieldState,
    className: TextFieldState,
    generatedType: ParsedType? = null,
    targetLanguage: TargetLanguage,
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
                    selectableItem(selected = targetLanguage == language,
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

        OutlinedButton(
            onClick = {
                if (jsonInput.text.isNotBlank()) {
                    onParseJsonClicked(jsonInput.text.toString())
                }
            }
        ) {
            Text("Parse")
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
                        Column {
                            if (generatedType?.imports?.isNotBlank() == true) {
                                Text(generatedType.imports)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TypeItem(
    typeContent: String,
    showCopyIcon: Boolean,
    onCopyGeneratedTypeClicked: (type: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (showCopyIcon){
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
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = typeContent,
            modifier = Modifier
        )
    }
}




