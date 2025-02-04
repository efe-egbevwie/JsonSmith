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
import com.github.efeegbevwie.jsonsmith.services.fileSavers.SaveFileResult
import com.github.efeegbevwie.jsonsmith.services.fileSavers.saveGeneratedTypesToFiles
import com.github.efeegbevwie.jsonsmith.util.toClassNameCamelCase
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


@Service(Service.Level.PROJECT)
class MyProjectService(project: Project) {

    private val json = Json { prettyPrint = true; }

    private val targetLanguageFlow = MutableStateFlow<TargetLanguage>(TargetLanguage.Kotlin())
    val targetLanguage: StateFlow<TargetLanguage> = targetLanguageFlow.asStateFlow()

    private val generatedTypeFlow = MutableStateFlow<ParsedType?>(null)
    val generatedType = generatedTypeFlow.asStateFlow()

    // For JSON structure
    private val jsonElementFlow = MutableStateFlow<JsonElement?>(null)
    val jsonElement: StateFlow<JsonElement?> = jsonElementFlow.asStateFlow()

    var jsonInput = TextFieldState()
    val jsonStructureInput = TextFieldState()
    val classNameInput = TextFieldState()

    // For JSON parsing
    private val jsonParsingEventsFlow = MutableStateFlow<JsonSmithEvent?>(null)
    val jsonParsingEvents: Flow<JsonSmithEvent?> = jsonParsingEventsFlow.asStateFlow()

    private val jsonStructureParsingEventsFlow = MutableStateFlow<JsonSmithEvent?>(null)
    val jsonStructureParsingEvents: StateFlow<JsonSmithEvent?> = jsonStructureParsingEventsFlow.asStateFlow()


    private val serviceCoroutineScope = CoroutineScope(Dispatchers.Default)

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

    fun getRandomNumber() = (1..100).random()

    fun setTargetLanguage(language: TargetLanguage) {
        targetLanguageFlow.update { language }
        if (generatedTypeFlow.value?.stringRepresentation?.isNotEmpty() == true) {
            generateTypeFromJson(json = jsonInput.text.toString())
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


    fun getJsonElement(json: String = jsonStructureInput.text.toString()) {
        try {
            jsonStructureParsingEventsFlow.removeErrorParsingState()
            val element = Json.parseToJsonElement(json)
            jsonElementFlow.update { element }
        } catch (e: Exception) {
            serviceCoroutineScope.launch {
                jsonStructureParsingEventsFlow.sendParsingError()
            }
            e.printStackTrace()
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
            val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(selection, selection)
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

                else -> {}
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


    sealed class JsonSmithEvent {
        open val message: String = ""
        open val timeOut: Duration = 3.seconds
        open val errorEvent: Boolean = false

        data class ContentCopied(
            override val message: String = "Content copied",
            override val timeOut: Duration = 3.seconds
        ) : JsonSmithEvent()

        data class JsonParsingFailed(
            override val message: String = "Could not parse JSON, invalid input",
            override val timeOut: Duration = 0.seconds,
            override val errorEvent: Boolean = true
        ) : JsonSmithEvent()

        data class FileSaved(override val message: String = "Generated type saved") : JsonSmithEvent()
        data class FileSavedError(
            override val message: String = "Error saving files",
            override val errorEvent: Boolean = true
        ) : JsonSmithEvent()
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

