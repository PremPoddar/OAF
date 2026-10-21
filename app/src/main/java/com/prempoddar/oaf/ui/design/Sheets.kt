@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.prempoddar.oaf.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/*
 * Pieces for task sheets (compress, add a server, batch pictures…), in the same language as
 * Appearance: a scalloped header icon, choices as springy cards rather than chips, connected
 * segment bars, settings grouped into rounded cards, and one big button to finish.
 */

/** A tall bottom sheet for a task, scrolling when the keyboard is up. */
@Composable
fun ToolSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // Drawn in place in previews and screen tests, which can't show a sheet's own window.
    if (androidx.compose.ui.platform.LocalInspectionMode.current) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        return
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
        )
    }
}

/** What the sheet is for: a big scalloped icon, a title and a line about it. */
@Composable
fun SheetHeader(icon: ImageVector, title: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        IconTile(icon, size = 56.dp)
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A small accent-coloured heading over a part of a sheet. */
@Composable
fun SheetLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
}

/**
 * One of a set of choices as a card: icon, name and what it means. The chosen one fills with the
 * accent, springs rounder and gets a tick.
 */
@Composable
fun OptionCard(icon: ImageVector, title: String, caption: String?, selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val r by animateDpAsState(if (selected) 28.dp else 18.dp, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "card")
    val bg by animateColorAsState(if (selected) s.primaryContainer else s.surfaceContainerHigh, label = "bg")
    val fg = if (selected) s.onPrimaryContainer else s.onSurface
    val shape = RoundedCornerShape(r)
    Box(
        modifier.clip(shape).background(bg).border(if (selected) 2.dp else 0.dp, s.primary, shape)
            .clickable(enabled = enabled) { if (!selected) Haptics.select(); onClick() }
            .alpha(if (enabled) 1f else 0.45f).heightIn(min = 96.dp).padding(14.dp),
    ) {
        Column {
            Icon(icon, null, tint = if (selected) s.primary else s.onSurfaceVariant, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 1)
            if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.75f), maxLines = 2)
        }
        if (selected) Box(Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(s.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, null, Modifier.size(15.dp), tint = s.onPrimary)
        }
    }
}

/** Choices laid out as [OptionCard]s, [columns] to a row. */
@Composable
fun <T> OptionGrid(options: List<T>, selected: T, columns: Int = 2, enabled: (T) -> Boolean = { true }, onPick: (T) -> Unit, describe: (T) -> Triple<ImageVector, String, String?>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { o ->
                    val (icon, title, caption) = describe(o)
                    OptionCard(icon, title, caption, o == selected, Modifier.weight(1f), enabled(o)) { onPick(o) }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Options as one connected bar; the chosen segment fills and springs round. */
@Composable
fun <T> SegmentedBar(options: List<Pair<T, String>>, selected: T, enabled: Boolean = true, onPick: (T) -> Unit) {
    val s = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { i, (value, label) ->
            val on = value == selected
            val outer = 22.dp; val inner = 6.dp
            val corner by animateDpAsState(if (on) outer else inner, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "seg")
            val shape = when {
                on -> RoundedCornerShape(corner)
                i == 0 -> RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner)
                i == options.lastIndex -> RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer)
                else -> RoundedCornerShape(inner)
            }
            Surface(
                onClick = { if (!on) Haptics.select(); onPick(value) }, enabled = enabled, shape = shape,
                color = if (on) s.primary else s.surfaceContainerHighest, contentColor = if (on) s.onPrimary else s.onSurfaceVariant,
                modifier = Modifier.weight(1f).height(46.dp),
            ) { Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1) } }
        }
    }
}

/** Settings that belong together in one rounded card, with hairline gaps between rows. */
@Composable
fun CardGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
}

/** A row in a [CardGroup] with a scalloped icon, words and a tick-or-cross switch. */
@Composable
fun ToggleCard(icon: ImageVector, title: String, summary: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = enabled) { Haptics.toggle(!checked); onChange(!checked) }
            .alpha(if (enabled) 1f else 0.5f).heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconTile(icon, size = 42.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconSwitch(checked, enabled) { Haptics.toggle(it); onChange(it) }
    }
}

/** A plain area of a [CardGroup] for fields or anything else. */
@Composable
fun CardSection(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

/** Cancel and the main action, side by side at the foot of a sheet. */
@Composable
fun SheetActions(action: String, icon: ImageVector?, enabled: Boolean, onCancel: () -> Unit, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedBigButton("Cancel", onCancel, Modifier.weight(1f))
        BigButton(action, onAction, Modifier.weight(1.6f), icon = icon, enabled = enabled)
    }
}
