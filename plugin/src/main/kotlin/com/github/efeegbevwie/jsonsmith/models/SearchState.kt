package com.github.efeegbevwie.jsonsmith.models
data class SearchState(
    val matchedItemsIndices: List<Int> = emptyList(),
    val currentMatchIndex: Int = 0,
    val matchedItemIndex: Int? = null
) {
    val hasMatches: Boolean get() = matchedItemsIndices.isNotEmpty()
    val hasNextMatch: Boolean get() = hasMatches && currentMatchIndex < matchedItemsIndices.size - 1
    val hasPreviousMatch: Boolean get() = hasMatches && currentMatchIndex > 0
    val currentMatchedIndex: Int get() = if (hasMatches) currentMatchIndex + 1 else 0
    val totalMatches: Int get() = matchedItemsIndices.size
}