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
import com.prempoddar.oaf.ui.design.AppTheme
import com.prempoddar.oaf.ui.design.Appearance
import com.prempoddar.oaf.ui.design.isDark
import com.prempoddar.oaf.format.FileKind

/** Reader-first: the page is the interface. Paper and ink, one quiet accent, from the theme. */
data class Paper(val page: Color, val ink: Color, val muted: Color, val faint: Color, val accent: Color, val chrome: Color, val rule: Color, val dark: Boolean)

val LocalPaper = staticCompositionLocalOf { Paper(Color.White, Color.Black, Color.Gray, Color.LightGray, Color.Blue, Color.White, Color.LightGray, false) }

/** OAF's deep ink blue. */
const val OAF_BRAND = 0x1F3B63

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
fun OafTheme(appearance: Appearance, content: @Composable () -> Unit) {
    AppTheme(appearance, OAF_BRAND, Type) {
        val c = MaterialTheme.colorScheme
        val p = Paper(
            page = c.background, ink = c.onBackground, muted = c.onSurfaceVariant, faint = c.outline,
            accent = c.primary, chrome = c.surfaceContainer.copy(alpha = 0.96f), rule = c.outlineVariant, dark = isDark(appearance),
        )
        CompositionLocalProvider(LocalPaper provides p, content = content)
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
