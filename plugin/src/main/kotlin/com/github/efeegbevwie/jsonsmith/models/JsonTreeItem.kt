package com.github.efeegbevwie.jsonsmith.models

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

sealed class JsonTreeItem {
    /**
     * Represents the unique key for a JSON tree item, starting from the root and constructed
     * by joining keys with a period (.). Each level in the hierarchy appends the respective
     * key to the path, indicating the item's position within the JSON structure.
     */
    abstract val nodePath: String
    /**
     * Represents the nesting level of a JSON tree item in relation to its parent.
     * The root element has a level of 0, and each subsequent child increases the level by 1,
     * indicating its depth in the hierarchical structure.
     */
    abstract val level: Int
    /**
     * Indicates whether this node is expanded in the UI.
     * Only applicable for container types (Object and Array).
     */
    open val expanded: Boolean = false

    data class ObjectItem(
        override val nodePath: String,
        override val level: Int,
        override val expanded: Boolean = false,
        val jsonObject: JsonObject,
        val key: String? = null,
        val fromArray: Boolean = false,
        val arrayIndex: Int? = null
    ) : JsonTreeItem()

    data class ArrayItem(
        override val nodePath: String,
        override val level: Int,
        override val expanded: Boolean = false,
        val jsonArray: JsonArray,
        val key: String? = null,
        val fromArray: Boolean = false,
        val arrayIndex: Int? = null
    ) : JsonTreeItem()

    data class PrimitiveItem(
        override val nodePath: String,
        override val level: Int,
        override val expanded: Boolean = false, // Primitive items can't be expanded
        val jsonPrimitive: JsonPrimitive,
        val key: String,
        val fromArray: Boolean = false,
        val arrayIndex: Int? = null
    ) : JsonTreeItem()
}
