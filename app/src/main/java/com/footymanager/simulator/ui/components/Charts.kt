package com.footymanager.simulator.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.ui.theme.StatColors

/** One column of a [MonthlyBarChart]. */
data class ChartBar(
    val label: String,
    val income: Long,
    val expense: Long
)

/**
 * A compact grouped bar chart for monthly income and expense.
 *
 * Bars grow from a shared baseline using weights, so it never overflows on
 * narrow screens and needs no canvas maths. The whole chart animates in once
 * when the data changes.
 */
@Composable
fun MonthlyBarChart(
    bars: List<ChartBar>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 120.dp
) {
    if (bars.isEmpty()) {
        Text(
            text = "No financial activity recorded yet this season.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val maxValue = bars.maxOfOrNull { maxOf(it.income, it.expense) }?.coerceAtLeast(1L) ?: 1L

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            bars.forEach { bar ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        BarSegment(
                            value = bar.income,
                            maxValue = maxValue,
                            availableHeight = height - 18.dp,
                            color = StatColors.elite,
                            modifier = Modifier.weight(1f)
                        )
                        BarSegment(
                            value = bar.expense,
                            maxValue = maxValue,
                            availableHeight = height - 18.dp,
                            color = StatColors.bad,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = bar.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(color = StatColors.elite, label = "Income")
            LegendDot(color = StatColors.bad, label = "Expense")
        }
    }
}

@Composable
private fun BarSegment(
    value: Long,
    maxValue: Long,
    availableHeight: androidx.compose.ui.unit.Dp,
    color: Color,
    modifier: Modifier = Modifier
) {
    val fraction = (value.toFloat() / maxValue).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 600),
        label = "bar"
    )
    val barHeight = (availableHeight.value * animated).dp
    Box(
        modifier = modifier
            .height(if (barHeight < 2.dp) 2.dp else barHeight)
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .background(if (value == 0L) MaterialTheme.colorScheme.surfaceVariant else color)
    )
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A single horizontal composition bar, used to show what share of total income
 * each revenue stream contributes.
 */
@Composable
fun CompositionBar(
    segments: List<Pair<String, Long>>,
    modifier: Modifier = Modifier
) {
    val total = segments.sumOf { it.second }.coerceAtLeast(1L)
    val colors = listOf(
        StatColors.elite,
        MaterialTheme.colorScheme.primary,
        StatColors.good,
        StatColors.average,
        StatColors.poor
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            segments.forEachIndexed { index, (_, value) ->
                if (value <= 0) return@forEachIndexed
                Box(
                    modifier = Modifier
                        .weight(value.toFloat() / total)
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(colors[index % colors.size])
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        segments.forEachIndexed { index, (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors[index % colors.size])
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(value * 100 / total)}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = Fmt.money(value),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
