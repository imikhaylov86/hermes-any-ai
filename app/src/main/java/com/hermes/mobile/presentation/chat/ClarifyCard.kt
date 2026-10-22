package com.hermes.mobile.presentation.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.domain.model.ClarifyPrompt
import com.hermes.mobile.presentation.i18n.strings
import com.hermes.mobile.presentation.theme.AppDimensions
import com.hermes.mobile.presentation.theme.SecondarySky

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClarifyCard(
    prompt: ClarifyPrompt,
    onAnswerSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = AppDimensions.ShapeCard,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = strings.clarifyPromptTitle,
                    tint = SecondarySky,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.clarifyPromptTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = prompt.question,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                prompt.options.forEach { option ->
                    SuggestionChip(
                        onClick = { onAnswerSelected(option) },
                        label = { Text(option, fontSize = 12.sp) },
                        shape = AppDimensions.ShapeChip
                    )
                }
            }
        }
    }
}
