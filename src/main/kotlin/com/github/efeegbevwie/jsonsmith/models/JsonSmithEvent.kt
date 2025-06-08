package com.github.efeegbevwie.jsonsmith.models

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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