package com.efe.jsonSmith.parser.targetLanguages

sealed interface TargetLanguageConfig{
    val className: String
    val saveClassesAsSeparateFiles: Boolean
    val fileExtension: String
}
