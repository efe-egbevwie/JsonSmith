package com.github.efeegbevwie.jsonsmith.services.languageParsers

data class ParsedType(
    val fileName: String,
    val imports: String? = null,
    val parsedClasses: List<ParsedClass>,
    val stringRepresentation: String,
)

data class ParsedClass(
    val className: String,
    val classBody:String
)
