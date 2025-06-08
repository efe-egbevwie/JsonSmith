package com.github.efeegbevwie.jsonsmith.toolWindow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.efe.jsonSmith.languageParsers.ParsedType
import com.efe.jsonSmith.targetLanguages.TargetLanguage
import com.efe.jsonSmith.targetLanguages.TargetLanguageConfig
import com.efe.jsonSmith.targetLanguages.displayName
import com.efe.jsonSmith.targetLanguages.enabledTargetLanguages
import com.github.efeegbevwie.jsonsmith.models.JsonSmithEvent
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.ErrorBanner
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.SuccessBanner
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JsonParsingToolWindowContent(
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
