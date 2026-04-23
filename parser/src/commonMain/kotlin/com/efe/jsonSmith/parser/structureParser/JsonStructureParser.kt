package com.efe.jsonSmith.parser.structureParser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

enum class JsonValueType {
    String,
    Number,
    Boolean,
    TimeStamp
}


data class JsonArrayItem(
    val key: String,
    val valueType: JsonValueType
)


data class JsonArrayStructure(
    val items: List<JsonArrayItem> = emptyList(),
    val originalJsonArray: JsonArray? = null
) {

    fun getAllJsonValueTypes(): List<JsonValueType> {
        return items.map { it.valueType }.distinct()
    }

    fun applyFilter(filterPredicate: (JsonElement) -> Boolean): JsonArray? {
        if (originalJsonArray == null) return null

        val filteredElements = originalJsonArray.filter(filterPredicate)
        return JsonArray(filteredElements)
    }

    fun filterByStringEquals(jsonKey: String, value: String): JsonArray? {
        return filterByJsonKey(jsonKey) { element ->
            element is JsonPrimitive &&
                    element.isString &&
                    element.content.lowercase().contains(value.lowercase())
        }
    }

    fun filterByNumberEquals(jsonKey: String, queryValue: Number): JsonArray? {
        return filterByJsonKey(jsonKey) { element ->
            when (queryValue) {
                is Int, is Long -> element is JsonPrimitive && element.longOrNull == queryValue.toLong()
                is Float, is Double -> element is JsonPrimitive && element.doubleOrNull == queryValue.toDouble()
                else -> false
            }
        }
    }

    fun filterByBooleanEquals(jsonKey: String, value: Boolean): JsonArray? {
        return filterByJsonKey(jsonKey) { element ->
            element is JsonPrimitive && element.booleanOrNull == value
        }
    }

    //Filters the original JSON array based on a key and a condition on the value of the key.
    fun filterByJsonKey(jsonKey: String, condition: (JsonElement?) -> Boolean): JsonArray? {
        if (originalJsonArray == null) return null

        val keyParts = jsonKey.split(".")

        val filteredElements: List<JsonElement> = originalJsonArray.filter { element ->
            if (element !is JsonObject) return@filter false

            var currentElement: JsonElement? = element
            for (key in keyParts) {
                if (currentElement !is JsonObject) return@filter false
                currentElement = currentElement[key]
                if (currentElement == null) return@filter false
            }

            condition(currentElement)
        }

        return JsonArray(filteredElements)
    }
}



fun parseJsonArrayStructure(jsonArray: JsonArray): JsonArrayStructure {
    val items = mutableListOf<JsonArrayItem>()
    val firstElement: JsonElement? = jsonArray.firstOrNull()

    when(firstElement) {
        is JsonArray -> {
            val nestedItems = parseJsonArray(firstElement, "item")
            items.addAll(nestedItems)
        }
        is JsonObject -> {
            val objectItems = parseJsonObject(firstElement)
            items.addAll(objectItems)
        }
        is JsonPrimitive -> {
            val valueType = determineType(firstElement)
            items.add(JsonArrayItem("value", valueType))
        }
        JsonNull, null -> {}
    }

    return JsonArrayStructure(items, jsonArray)
}


private fun determineType(
    value: JsonElement,
): JsonValueType {
    return when {
        value is JsonPrimitive && value.isString -> JsonValueType.String
        value is JsonPrimitive && value.booleanOrNull != null -> JsonValueType.Boolean
        value is JsonPrimitive && value.longOrNull != null -> JsonValueType.Number
        value is JsonPrimitive && value.doubleOrNull != null -> JsonValueType.Number
        else -> JsonValueType.String
    }
}


private fun parseJsonArray(jsonArray: JsonArray, parentKey: String): List<JsonArrayItem> {
    val items = mutableListOf<JsonArrayItem>()

    val firstElement = jsonArray.firstOrNull()

    when (firstElement) {
        is JsonObject -> {
            val objectItems: List<JsonArrayItem> = parseJsonObject(firstElement)
            items.addAll(objectItems.map { 
                // Prefix the key with the parent key to create a path
                JsonArrayItem("$parentKey.${it.key}", it.valueType)
            })
        }
        is JsonArray -> {
            // If the first element is an array, parse it recursively
            val nestedItems = parseJsonArray(firstElement, "$parentKey.item")
            items.addAll(nestedItems)
        }
        is JsonPrimitive -> {
            val valueType = determineType(firstElement)
            items.add(JsonArrayItem("$parentKey.item", valueType))
        }
        else -> {}
    }
    return items
}


private fun parseJsonObject(jsonObject: JsonObject): List<JsonArrayItem> {
    val items = mutableListOf<JsonArrayItem>()

    jsonObject.entries.forEach { (key, value) ->
        when (value) {
            is JsonPrimitive -> {
                val valueType = determineType(value)
                items.add(JsonArrayItem(key, valueType))
            }
            is JsonObject -> {
                val nestedItems: List<JsonArrayItem> = parseJsonObject(value)
                items.addAll(nestedItems.map {
                    JsonArrayItem("$key.${it.key}", it.valueType)
                })
            }
            is JsonArray -> {
                val arrayItems: List<JsonArrayItem> = parseJsonArray(value, key)
                items.addAll(arrayItems)
            }
        }
    }

    return items
}
