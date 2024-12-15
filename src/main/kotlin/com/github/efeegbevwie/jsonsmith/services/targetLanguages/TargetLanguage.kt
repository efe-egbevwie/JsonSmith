package com.github.efeegbevwie.jsonsmith.services.targetLanguages

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf


val enabledTargetLanguages: ImmutableList<TargetLanguage> =
    persistentListOf(TargetLanguage.Kotlin(), TargetLanguage.Java())


@Stable
sealed class TargetLanguage(open var targetLanguageConfig: TargetLanguageConfig) {

    data class Kotlin(
        override var targetLanguageConfig: TargetLanguageConfig = KotlinConfigOptions()
    ) : TargetLanguage(targetLanguageConfig = KotlinConfigOptions()) {

        data class KotlinConfigOptions(
            override val className: String = "JsonClass",
            override val saveClassesAsSeparateFiles: Boolean = false,
            override val fileExtension: String = ".kt",
            val serializationFrameWork: SerializationFrameWork = KotlinSerializationFrameWorks.Kotlinx,
            val allPropertiesOptional: Boolean = true,
        ) : TargetLanguageConfig

        enum class KotlinSerializationFrameWorks : SerializationFrameWork {
            Kotlinx,
            Gson,
            Jackson
        }
    }


    data class Java(override var targetLanguageConfig: TargetLanguageConfig = JavaConfigOptions()) :
        TargetLanguage(targetLanguageConfig) {
        data class JavaConfigOptions(
            override val saveClassesAsSeparateFiles: Boolean = true,
            override val className: String = "JsonClass",
            override val fileExtension: String = ".java",
            val useLombok: Boolean = false,
            val useArrays: Boolean = true,
            val serializationFrameWork: SerializationFrameWork? = JavaSerializationFrameWorks.Lombok,
        ) : TargetLanguageConfig
        enum class JavaSerializationFrameWorks:SerializationFrameWork{
            Records,
            Lombok,
            PlainTypes
        }
    }

}


fun TargetLanguage.displayName(): String {
    return when (this) {
        is TargetLanguage.Java -> "Java"
        is TargetLanguage.Kotlin -> "Kotlin"
    }
}
