package com.github.efeegbevwie.jsonsmith.toolWindow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.efe.jsonSmith.parser.languageParsers.ParsedType
import com.efe.jsonSmith.parser.targetLanguages.TargetLanguage
import com.efe.jsonSmith.parser.targetLanguages.TargetLanguage.*
import com.efe.jsonSmith.parser.targetLanguages.TargetLanguageConfig
import com.efe.jsonSmith.parser.targetLanguages.displayName
import com.efe.jsonSmith.parser.targetLanguages.enabledTargetLanguages
import com.github.efeegbevwie.jsonsmith.models.JsonSmithEvent
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import generateAnnotatedString
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.*
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

        Spacer(modifier = Modifier.height(10.dp))

        LanguageConfig(
            targetLanguage = targetLanguage,
            config = targetLanguage.targetLanguageConfig,
            onConfigChanged = onLanguageConfigChanged
        )

        Spacer(modifier = Modifier.height(10.dp))

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


        Spacer(modifier = Modifier.height(10.dp))

        AnimatedVisibility(visible = generatedType?.stringRepresentation?.isNotEmpty() == true) {
            Column(
                modifier = Modifier
                    .padding(bottom = 10.dp)
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
                            Row(modifier = Modifier.fillMaxWidth()) {
                                val generatedCode: String = buildString {
                                    val imports: String? = generatedType.imports
                                    val types: String =
                                        generatedType.parsedClasses.joinToString(separator = " ") { (className, classBody) ->
                                            classBody.plus("\n")
                                        }

                                    imports?.let {
                                        appendLine(it)
                                        appendLine()
                                    }
                                    appendLine(types)
                                }

                                val highlights =
                                    Highlights
                                        .Builder(code = generatedCode.trimIndent())
                                        .language(targetLanguage.getCodeTextLanguage())
                                        .theme(SyntaxThemes.darcula(darkMode = isSystemInDarkTheme()))
                                        .build()

                                val currentText = TextFieldValue(
                                    annotatedString = highlights.getHighlights()
                                        .generateAnnotatedString(code = highlights.getCode())
                                )
                                val linesCount = currentText.annotatedString.count { it == '\n' }

                                Text(
                                    modifier = Modifier
                                        .fillMaxHeight(),
                                    color = JewelTheme.globalColors.text.disabled,
                                    fontWeight = FontWeight.Light,
                                    text = IntRange(1, linesCount).joinToString(separator = "\n"),
                                )
                                Spacer(modifier = Modifier.width(6.dp))

                                Column {
                                    SelectionContainer(modifier = Modifier) {
                                        Text(
                                            text = currentText.annotatedString
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun TargetLanguage.getCodeTextLanguage(): SyntaxLanguage =
    when (this) {
        is Go -> SyntaxLanguage.GO
        is Java -> SyntaxLanguage.JAVA
        is Kotlin -> SyntaxLanguage.KOTLIN
    }