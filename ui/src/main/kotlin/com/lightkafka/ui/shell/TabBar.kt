package com.lightkafka.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lightkafka.ui.infra.AccentViolet
import com.lightkafka.ui.infra.BorderSubtle
import com.lightkafka.ui.infra.SurfaceCard
import com.lightkafka.ui.infra.SurfaceElevated
import com.lightkafka.ui.infra.TextMuted
import com.lightkafka.ui.infra.TextPrimary

@Suppress("ktlint:standard:function-naming")
@Composable
fun TabBar(
    tabs: List<TabInstance>,
    activeTabId: String?,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().height(38.dp).background(SurfaceCard),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        items(tabs, key = { it.id }) { tab ->
            val active = tab.id == activeTabId
            Box(
                modifier =
                    Modifier.height(38.dp).background(if (active) SurfaceElevated else SurfaceCard)
                        .clickable { onTabClick(tab.id) },
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        tab.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) TextPrimary else TextMuted,
                    )
                    IconButton(onClick = { onTabClose(tab.id) }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Default.Close,
                            "Close ${tab.title}",
                            tint = TextMuted,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                if (active) {
                    Box(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(2.dp).background(AccentViolet),
                    )
                }
                Box(
                    Modifier.align(Alignment.CenterEnd).size(width = 1.dp, height = 20.dp).background(BorderSubtle),
                )
            }
        }
    }
}
