package com.github.efeegbevwie.jsonsmith.toolWindow

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.efeegbevwie.jsonsmith.services.targetLanguages.TargetLanguage
import com.github.efeegbevwie.jsonsmith.services.targetLanguages.TargetLanguage.Java.JavaConfigOptions
import com.github.efeegbevwie.jsonsmith.services.targetLanguages.TargetLanguage.Java.JavaSerializationFrameWorks
import com.github.efeegbevwie.jsonsmith.services.targetLanguages.TargetLanguage.Kotlin.KotlinConfigOptions
import com.github.efeegbevwie.jsonsmith.services.targetLanguages.TargetLanguage.Kotlin.KotlinSerializationFrameWorks
import com.github.efeegbevwie.jsonsmith.services.targetLanguages.TargetLanguageConfig
import com.github.efeegbevwie.jsonsmith.services.targetLanguages.displayName
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Text

@Composable
fun LanguageConfig(
    targetLanguage: TargetLanguage,
    config: TargetLanguageConfig,
    onConfigChanged: (TargetLanguageConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (targetLanguage) {
        is TargetLanguage.Java -> JavaConfigOptionsUi(
            config = config as JavaConfigOptions,
            onJavaConfigChanged = onConfigChanged,
            modifier = modifier,
        )

        is TargetLanguage.Kotlin -> KotlinConfigOptionsUi(
            config = config as KotlinConfigOptions,
            onConfigChanged = onConfigChanged,
            modifier = modifier,
        )
    }
}

@Composable
private fun JavaConfigOptionsUi(
    config: JavaConfigOptions,
    onJavaConfigChanged: (JavaConfigOptions) -> Unit,
    modifier: Modifier = Modifier
) {
    Column (modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)){
        Dropdown(
            modifier = Modifier,
            menuContent = {
                JavaSerializationFrameWorks.entries.forEach { framework ->
                    selectableItem(selected = config.serializationFrameWork == framework,
                        onClick = {
                            val newConfig = config.copy(serializationFrameWork = framework)
                            onJavaConfigChanged(newConfig)
                        }
                    ) {
                        Text(text = framework.displayName())
                    }
                }

            }
        ) {
            Text(text = "${config.serializationFrameWork?.displayName()}")
        }

        Row(
            modifier = modifier.horizontalScroll(state = rememberScrollState()),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            CheckboxRow(
                checked = config.useArrays,
                onCheckedChange = { checked ->
                    val newConfig = config.copy(useArrays = checked)
                    onJavaConfigChanged(newConfig)
                }
            ) {
                Text(text = "Use Arrays")
            }
        }

    }


}

@Composable
private fun KotlinConfigOptionsUi(
    config: KotlinConfigOptions,
    onConfigChanged: (KotlinConfigOptions) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Dropdown(
            modifier = Modifier,
            menuContent = {
                KotlinSerializationFrameWorks.entries.forEach { framework ->
                    selectableItem(selected = config.serializationFrameWork == framework,
                        onClick = {
                            val newConfig = config.copy(serializationFrameWork = framework)
                            onConfigChanged(newConfig)
                        }
                    ) {
                        Text(text = framework.displayName())
                    }
                }

            }
        ) {
            Text(text = config.serializationFrameWork.displayName())
        }

        Row(horizontalArrangement = Arrangement.SpaceBetween) {
            CheckboxRow(
                checked = config.allPropertiesOptional,
                onCheckedChange = { checked ->
                    val newConfig = config.copy(allPropertiesOptional = checked)
                    onConfigChanged(newConfig)
                }
            ) {
                Text(text = "Optional Properties")
            }

            CheckboxRow(
                checked = config.saveClassesAsSeparateFiles,
                onCheckedChange = { checked ->
                    val newConfig = config.copy(saveClassesAsSeparateFiles = checked)
                    onConfigChanged(newConfig)
                }
            ) {
                Text(text = "Separate files")
            }
        }

    }
}
