package com.aigame.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aigame.viewmodel.UiEvent
import com.aigame.viewmodel.UiState

/** פאנל הפיתוח (F1): להקליד במקום לדבר, להעיר את המארח, ולראות מה קורה. */
@Composable
fun DevPanel(state: UiState, onEvent: (UiEvent) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    val send = {
        if (text.isNotBlank()) onEvent(UiEvent.Typed(text))
        text = ""
    }

    Surface(
        modifier = modifier.width(420.dp).fillMaxHeight().padding(16.dp),
        color = Color(0xF01A1A24),
        // טקסט בהיר: בלי זה הוא יוצא שחור על הרקע הכהה.
        contentColor = Color(0xFFE8EAF0),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("פאנל פיתוח · F1", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("להגיד למארח") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                    if (e.key == Key.Enter && e.type == KeyEventType.KeyDown) { send(); true } else false
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = send) { Text("שלח") }
                OutlinedButton(onClick = { onEvent(UiEvent.WakeNow) }) { Text("העֵר אותו") }
            }

            Text("שחקנים: " + state.players.ifEmpty { listOf("אין עדיין") }.joinToString(", "))
            Text("התעוררות הבאה: ${state.nextWake ?: "—"}")

            Text("יומן", style = MaterialTheme.typography.titleSmall)
            state.recent.forEach { Text(it, fontSize = 13.sp, color = Color(0xFFB8BCC8)) }
        }
    }
}
