package com.github.efeegbevwie.jsonsmith.services

import app.cash.turbine.test
import com.efe.jsonSmith.languageParsers.ParsedType
import com.efe.jsonSmith.targetLanguages.TargetLanguage
import com.efe.jsonSmith.targetLanguages.enabledTargetLanguages
import com.github.efeegbevwie.jsonsmith.models.JsonSmithEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement


class MyProjectServiceTest : BasePlatformTestCase() {
    private val json = Json { prettyPrint = true }
    private lateinit var service: MyProjectService
    private lateinit var serviceCoroutineScope: CoroutineScope
    private val validJson = """
    {
    	"id": "0001",
    	"type": "donut",
    	"name": "Cake",
    	"ppu": 0.55,
    "prepared": false,
    	"batters":{"batter":	[
    	{ "id": "1001", "type": "Regular" },
    					{ "id": "1002", "type": "Chocolate" },
    					{ "id": "1003", "type": "Blueberry" },
    					{ "id": "1004", "type": "Devil's Food" }
    				]
    		},
    	"topping":
    		[
    			{ "id": "5001", "type": "None" },
    			{ "id": "5002", "type": "Glazed" },
    			{ "id": "5005", "type": "Sugar" },
    			{ "id": "5007", "type": "Powdered Sugar" },
    			{ "id": "5006", "type": "Chocolate with Sprinkles" },
    			{ "id": "5003", "type": "Chocolate" },
    			{ "id": "5004", "type": "Maple" }
    		]
    }
   """

    private val invalidJson = """
         {
    	"id": "0001",
    	"type": "donut",
    	"name": "Cake",
    	"ppu": 0.55,
    "prepared": false,
    	"batters":{"batter":	[
    	{ "id": "1001", "type": "Regular" },
    					{ "id": "1002", "type": "Chocolate" },
    					{ "id": "1003", "type": "Blueberry" },
    					{ "id": "1004", "type": "Devil's Food" }
    				]
    """.trimIndent()

    override fun setUp() {
        super.setUp()
        serviceCoroutineScope = CoroutineScope(Dispatchers.IO)
        service = MyProjectService(project = project, serviceCoroutineScope =  serviceCoroutineScope)
    }

    fun `test when valid json is formatted, json element flow should contain json element`() =
        runTest {
            service.parseJsonStructure(json = validJson)
            service.jsonElement.test {
                val jsonElement: JsonElement? = awaitItem()
                assertNotNull(jsonElement)
            }
        }

    fun `test when parsing invalid json structure should emit error to jsonStructureEvents`() = runTest {
        service.parseJsonStructure(json = invalidJson)
        service.jsonElement.test {
            val jsonElement: JsonElement? = awaitItem()
            assertNull(jsonElement)
        }
        service.jsonStructureParsingEvents.test {
            val event: JsonSmithEvent? = awaitItem()
            assertEquals(JsonSmithEvent.JsonParsingFailed(), event)
        }
    }

    fun `test generate type from valid JSON should emit ParsedType`() = runTest {
        enabledTargetLanguages.forEach { targetLanguage: TargetLanguage ->
            service.setTargetLanguage(targetLanguage)
            service.generateTypeFromJson(validJson)
            service.generatedType.test {
                val parsedType: ParsedType? = awaitItem()
                assertNotNull(parsedType)
            }
        }
    }

    fun `test generate type from invalid JSON should emit parsing error `() = runTest {
        service.generateTypeFromJson(invalidJson)
        service.generatedType.test {
            val parsedType: ParsedType? = awaitItem()
            assertNull(parsedType)
        }
        service.jsonParsingEvents.test {
            val event: JsonSmithEvent? = awaitItem()
            assertEquals(JsonSmithEvent.JsonParsingFailed(), event)
        }
    }

    fun `test generated type class name should equal target language config class name`() = runTest {
        val className = "MyClassName"
        val go = TargetLanguage.Go(targetLanguageConfig = TargetLanguage.Go.GoConfigOptions(className = className))
        val kotlin =
            TargetLanguage.Kotlin(targetLanguageConfig = TargetLanguage.Kotlin.KotlinConfigOptions(className = className))
        val java =
            TargetLanguage.Java(targetLanguageConfig = TargetLanguage.Java.JavaConfigOptions(className = className))

        listOf(go, kotlin, java).forEach { language ->
            service.setTargetLanguage(language)
            service.generateTypeFromJson(validJson)

            service.generatedType.test {
                val parsedType: ParsedType? = awaitItem()
                TestCase.assertEquals(className, parsedType?.fileName)
            }
        }
    }
}