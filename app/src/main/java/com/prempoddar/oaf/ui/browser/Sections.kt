package com.prempoddar.oaf.ui.browser

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prempoddar.oaf.data.SortBy
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Markers between groups of files, matching how they're sorted: A, B, C… by name; Today,
 * Yesterday, This week, then months by date; size bands by size; and the file type by type.
 */
object Sections {
    private val month = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    fun of(e: Entry, by: SortBy, foldersFirst: Boolean, now: Long = System.currentTimeMillis()): String {
        if (e.isDir && (foldersFirst || by == SortBy.SIZE || by == SortBy.TYPE)) return "Folders"
        return when (by) {
            SortBy.NAME -> letter(e.file.name)
            SortBy.DATE -> date(e.modified, now)
            SortBy.SIZE -> size(e.size)
            SortBy.TYPE -> e.file.extension.uppercase().ifEmpty { "No extension" }
        }
    }

    fun letter(name: String): String {
        val c = name.firstOrNull() ?: return "#"
        // "Élan" goes under E, as people would look for it.
        val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).first()
        return when {
            base.isLetter() -> base.uppercase()
            c.isDigit() -> "0–9"
            else -> "#"
        }
    }

    fun date(at: Long, now: Long): String {
        val day = startOfDay(now)
        return when {
            at >= day -> "Today"
            at >= day - DAY -> "Yesterday"
            at >= day - 6 * DAY -> "This week"
            else -> month.format(Date(at)).replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }
    }

    fun size(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "1 GB and over"
        bytes >= 100L shl 20 -> "100 MB – 1 GB"
        bytes >= 10L shl 20 -> "10 – 100 MB"
        bytes >= 1L shl 20 -> "1 – 10 MB"
        bytes >= 100L shl 10 -> "100 KB – 1 MB"
        else -> "Under 100 KB"
    }

    private fun startOfDay(t: Long) = Calendar.getInstance().apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private const val DAY = 24L * 60 * 60 * 1000
}

/** [list] in groups, each under its marker across the whole width; without markers unless [marked]. */
fun LazyGridScope.sectioned(list: List<Entry>, by: SortBy, foldersFirst: Boolean, marked: Boolean = true, content: @Composable (Entry) -> Unit) {
    if (!marked) { items(list, key = { it.file.path }) { content(it) }; return }
    val now = System.currentTimeMillis()
    val labels = list.map { Sections.of(it, by, foldersFirst, now) }
    var start = 0
    while (start < list.size) {
        val label = labels[start]
        var end = start + 1
        while (end < list.size && labels[end] == label) end++
        val group = list.subList(start, end)
        item(key = "section:$start:$label", span = { GridItemSpan(maxLineSpan) }, contentType = "section") { SectionHeader(label, group.size) }
        items(group, key = { it.file.path }) { content(it) }
        start = end
    }
}

@Composable
private fun SectionHeader(label: String, count: Int) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Text("  $count", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
