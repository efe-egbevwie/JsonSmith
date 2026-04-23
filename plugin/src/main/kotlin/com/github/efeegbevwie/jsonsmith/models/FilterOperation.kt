package com.github.efeegbevwie.jsonsmith.models

import com.efe.jsonSmith.parser.structureParser.JsonArrayStructure
import com.efe.jsonSmith.parser.structureParser.JsonValueType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Represents a filter operation that can be applied to a JSON element.
 * Different operations are available depending on the type of the element.
 */
sealed class FilterOperation {
    abstract val displayName: String

    abstract val applicableType: JsonValueType

    abstract fun apply(keyPath: String, jsonArrayStructure: JsonArrayStructure, filterValue: String): JsonArray?

    sealed class StringOperation(override val applicableType: JsonValueType = JsonValueType.String) :
        FilterOperation() {

        class Equals : StringOperation() {
            override val displayName: String = "equals"

            override fun apply(
                keyPath: String,
                jsonArrayStructure: JsonArrayStructure,
                filterValue: String
            ): JsonArray? {
                return jsonArrayStructure.filterByStringEquals(keyPath, filterValue)
            }
        }
    }


    sealed class NumberOperation(override val applicableType: JsonValueType = JsonValueType.Number) :
        FilterOperation() {

        class Equals : NumberOperation() {
            override val displayName: String = "equals"

            override fun apply(
                keyPath: String,
                jsonArrayStructure: JsonArrayStructure,
                filterValue: String
            ): JsonArray? {
                val numberValue = filterValue.toDoubleOrNull() ?: return null
                return jsonArrayStructure.filterByNumberEquals(keyPath, numberValue)
            }
        }


        class LessThan : NumberOperation() {
            override val displayName: String = "less than"

            override fun apply(
                keyPath: String,
                jsonArrayStructure: JsonArrayStructure,
                filterValue: String
            ): JsonArray? {
                val inputNumber: Double = filterValue.toDoubleOrNull() ?: return null
                return jsonArrayStructure.filterByJsonKey(keyPath) { element ->
                    val jsonNumber: Double = (element as? JsonPrimitive).let {
                        it?.doubleOrNull
                    } ?: return@filterByJsonKey false
                    jsonNumber < inputNumber
                }
            }
        }


        class GreaterThan : NumberOperation() {
            override val displayName: String = "greater than"

            override fun apply(
                keyPath: String,
                jsonArrayStructure: JsonArrayStructure,
                filterValue: String
            ): JsonArray? {
                val inputNumber: Double = filterValue.toDoubleOrNull() ?: return null
                return jsonArrayStructure.filterByJsonKey(keyPath) { element ->
                    val jsonNumber: Double = (element as? JsonPrimitive).let {
                        it?.doubleOrNull
                    } ?: return@filterByJsonKey false

                    jsonNumber > inputNumber
                }
            }
        }


        class Between : NumberOperation() {
            override val displayName: String = "between"

            override fun apply(
                keyPath: String,
                jsonArrayStructure: JsonArrayStructure,
                filterValue: String
            ): JsonArray? {
                // filterValue should be in the format "min,max"
                val (minStr, maxStr) = filterValue.split(",", limit = 2)
                val min = minStr.toDoubleOrNull() ?: return null
                val max = maxStr.toDoubleOrNull() ?: return null
                val filterRange: Array<Double> = arrayOf(min, max)

                return jsonArrayStructure.filterByJsonKey(keyPath) { element ->
                    val number: JsonPrimitive = element as? JsonPrimitive ?: return@filterByJsonKey false

                    number.doubleOrNull in filterRange
                }
            }
        }
    }


    sealed class BooleanOperation(override val applicableType: JsonValueType = JsonValueType.Boolean) :
        FilterOperation() {
        class Equals : BooleanOperation() {
            override val displayName: String = "equals"

            override fun apply(
                keyPath: String,
                jsonArrayStructure: JsonArrayStructure,
                filterValue: String
            ): JsonArray? {
                val boolValue = filterValue.lowercase() == "true"
                return jsonArrayStructure.filterByBooleanEquals(jsonKey = keyPath, value =  boolValue)
            }
        }
    }

    companion object {
        fun getOperationsForType(type: JsonValueType): List<FilterOperation> {
            return when (type) {
                JsonValueType.String -> listOf(StringOperation.Equals())
                JsonValueType.Number -> listOf(
                    NumberOperation.Equals(),
                    NumberOperation.LessThan(),
                    NumberOperation.GreaterThan(),
                    NumberOperation.Between()
                )

                JsonValueType.Boolean -> listOf(BooleanOperation.Equals())
                JsonValueType.TimeStamp -> listOf() // TODO
            }
        }
    }
}
