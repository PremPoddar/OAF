package com.prempoddar.oaf.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.prempoddar.oaf.R
import com.prempoddar.oaf.data.ThemeMode
import com.prempoddar.oaf.format.FileKind

/** Reader-first: the page is the interface. Paper and ink, one quiet accent. */
data class Paper(val page: Color, val ink: Color, val muted: Color, val faint: Color, val accent: Color, val chrome: Color, val rule: Color, val dark: Boolean)

private val LightPaper = Paper(Color(0xFFFBF8F3), Color(0xFF1F1D1A), Color(0xFF6B655C), Color(0xFFA39C91), Color(0xFF2F5D8C), Color(0xF2FFFFFF), Color(0xFFE7E1D7), false)
private val DarkPaper = Paper(Color(0xFF000000), Color(0xFFE8E4DD), Color(0xFF9C968C), Color(0xFF5E5A53), Color(0xFF8DB4E2), Color(0xF2161616), Color(0xFF242424), true)

val LocalPaper = staticCompositionLocalOf { LightPaper }

@OptIn(ExperimentalTextApi::class)
private fun font(res: Int, w: Int) = Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

/** For documents. */
val Literata = FontFamily(font(R.font.literata, 400), font(R.font.literata, 600), font(R.font.literata, 700))
/** For the interface. */
val Inter = FontFamily(font(R.font.inter, 400), font(R.font.inter, 500), font(R.font.inter, 600), font(R.font.inter, 700))
/** For code, hex and numbers that line up. */
val Mono = FontFamily(font(R.font.jetbrains_mono, 400), font(R.font.jetbrains_mono, 600))

private val Type = Typography(
    displaySmall = TextStyle(fontFamily = Literata, fontWeight = FontWeight(600), fontSize = 34.sp, letterSpacing = (-0.01).em),
    headlineMedium = TextStyle(fontFamily = Literata, fontWeight = FontWeight(600), fontSize = 27.sp),
    headlineSmall = TextStyle(fontFamily = Literata, fontWeight = FontWeight(600), fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight(600), fontSize = 19.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight(500), fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight(600), fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight(400), fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight(600), fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight(500), fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight(600), fontSize = 11.sp, letterSpacing = 0.06.em),
)

val ReadingBody = TextStyle(fontFamily = Literata, fontWeight = FontWeight(400), fontSize = 17.sp, lineHeight = 27.sp)
val CodeStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight(400), fontSize = 13.sp, lineHeight = 19.sp)

@Composable
fun isDark(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun OafTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val p = if (isDark(mode)) DarkPaper else LightPaper
    val scheme = if (p.dark) darkColorScheme(
        primary = p.accent, onPrimary = Color(0xFF0B1B2C), primaryContainer = Color(0xFF1C2B3B), onPrimaryContainer = p.ink,
        background = p.page, onBackground = p.ink, surface = Color(0xFF111111), onSurface = p.ink,
        surfaceVariant = Color(0xFF1C1C1C), onSurfaceVariant = p.muted, surfaceContainer = Color(0xFF141414), surfaceContainerHigh = Color(0xFF1C1C1C),
        surfaceContainerLow = Color(0xFF0C0C0C), surfaceContainerHighest = Color(0xFF242424), outline = Color(0xFF3A3A3A), outlineVariant = p.rule,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = Color.White, primaryContainer = Color(0xFFDCE6F1), onPrimaryContainer = Color(0xFF13263A),
        background = p.page, onBackground = p.ink, surface = Color.White, onSurface = p.ink,
        surfaceVariant = Color(0xFFF1ECE3), onSurfaceVariant = p.muted, surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFF5F1EA),
        surfaceContainerLow = Color(0xFFFDFBF8), surfaceContainerHighest = Color(0xFFEDE7DD), outline = Color(0xFFCFC7BA), outlineVariant = p.rule,
    )
    CompositionLocalProvider(LocalPaper provides p) {
        MaterialTheme(scheme, typography = Type, shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)), content = content)
    }
}

/** A small, quiet colour per file family, used only as a mark beside names. */
fun familyColor(kind: FileKind, dark: Boolean): Color = when (kind.family) {
    FileKind.Family.DOCUMENT -> if (dark) Color(0xFF8DB4E2) else Color(0xFF2F5D8C)
    FileKind.Family.SHEET -> if (dark) Color(0xFF8CCB9B) else Color(0xFF2E7D4F)
    FileKind.Family.SLIDES -> if (dark) Color(0xFFF0A878) else Color(0xFFB4532A)
    FileKind.Family.TEXT -> if (dark) Color(0xFFB8B2A7) else Color(0xFF6B655C)
    FileKind.Family.IMAGE -> if (dark) Color(0xFFC9A6E8) else Color(0xFF7446A6)
    FileKind.Family.MEDIA -> if (dark) Color(0xFFE79AB8) else Color(0xFFA63A66)
    FileKind.Family.ARCHIVE -> if (dark) Color(0xFFD8BE8C) else Color(0xFF8A6A2B)
    FileKind.Family.OTHER -> if (dark) Color(0xFF9C968C) else Color(0xFF8A847A)
}
