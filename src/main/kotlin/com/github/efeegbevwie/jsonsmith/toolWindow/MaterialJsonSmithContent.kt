package com.github.efeegbevwie.jsonsmith.toolWindow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.VerticalScrollbar

@Composable
fun MaterialJsonSmithContent(
    onParseJsonClicked: (json: String) -> Unit,
    generatedType: String? = null,
    modifier: Modifier = Modifier
        .fillMaxHeight()
        .widthIn(min = 100.dp, max = 500.dp)
        .padding(10.dp)
) {
    Column(modifier = modifier) {
        var jsonInput by remember {
            mutableStateOf("")
        }
        OutlinedTextField(
            modifier = Modifier.size(width = 300.dp, height = 300.dp),
            value = jsonInput,
            placeholder = {
                Text("Enter Json Value")
            },
            onValueChange = { newValue ->
                jsonInput = newValue
            }
        )
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedButton(onClick = {
            if (jsonInput.isNotBlank()) {
                onParseJsonClicked(jsonInput)
            }
        }) {
            Text("Parse")
        }

        Spacer(modifier = Modifier.height(20.dp))
        AnimatedVisibility(visible = generatedType?.isNotBlank() == true) {
            println("generated type -> $generatedType")
            val textScrollState = rememberScrollState()
            Column(modifier = Modifier.verticalScroll(textScrollState).padding(10.dp)) {
                Text(
                    text = generatedType.orEmpty(),
                    modifier = Modifier
                )
            }
        }
    }
}