package com.prempoddar.oaf.sheet

import org.apache.poi.hssf.usermodel.HSSFCell
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hssf.util.HSSFColor
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.FormulaError
import org.apache.poi.ss.usermodel.HorizontalAlignment
import java.io.File

/** Excel 97–2003 workbooks, read through Apache POI. Saving makes an .xlsx. */
object Xls {
    fun read(file: File): Workbook = file.inputStream().use { input ->
        HSSFWorkbook(input).use { book ->
            val styleCache = HashMap<Short, CellStyle>()
            val wb = Workbook(mutableListOf())
            for (i in 0 until book.numberOfSheets) {
                val src = book.getSheetAt(i)
                val sheet = Sheet(src.sheetName)
                src.paneInformation?.takeIf { it.isFreezePane }?.let {
                    sheet.frozenRows = it.horizontalSplitPosition.toInt(); sheet.frozenCols = it.verticalSplitPosition.toInt()
                }
                for (m in 0 until src.numMergedRegions) src.getMergedRegion(m).let { sheet.merges += CellRange(it.firstRow, it.firstColumn, it.lastRow, it.lastColumn) }
                var maxCol = 0
                for (row in src) {
                    for (cell in row) {
                        maxCol = maxOf(maxCol, cell.columnIndex)
                        val style = styleCache.getOrPut(cell.cellStyle.index) { style(book, cell as HSSFCell) }
                        val (input, cached) = content(cell as HSSFCell)
                        if (input.isEmpty() && style == CellStyle.DEFAULT) continue
                        sheet.set(cell.rowIndex, cell.columnIndex, input, style)
                        if (cached != null) sheet[cell.rowIndex, cell.columnIndex]?.let { it.cached = cached; it.value = cached }
                    }
                }
                for (c in 0..maxCol) if (src.isColumnHidden(c).not()) {
                    val w = src.getColumnWidth(c)
                    if (w != src.defaultColumnWidth * 256) sheet.colWidths[c] = w / 256f
                }
                wb.sheets += sheet
            }
            if (wb.sheets.isEmpty()) wb.sheets += Sheet("Sheet1")
            wb.recalculate()
            wb
        }
    }

    private fun content(cell: HSSFCell): Pair<String, Value?> = when (cell.cellType) {
        CellType.FORMULA -> {
            val cached = when (cell.cachedFormulaResultType) {
                CellType.NUMERIC -> Value.Num(cell.numericCellValue)
                CellType.STRING -> Value.Text(cell.stringCellValue)
                CellType.BOOLEAN -> Value.Bool(cell.booleanCellValue)
                CellType.ERROR -> Value.Err(runCatching { FormulaError.forInt(cell.errorCellValue).string }.getOrDefault("#N/A"))
                else -> Value.Empty
            }
            ("=" + runCatching { cell.cellFormula }.getOrDefault("")) to cached
        }
        CellType.NUMERIC -> Value.formatNumber(cell.numericCellValue).let { if (it.contains('e')) cell.numericCellValue.toString() else it } to null
        CellType.STRING -> Csv.input(cell.stringCellValue).let { if (Workbook.literal(it) !is Value.Text && !it.startsWith("'")) "'$it" else it } to null
        CellType.BOOLEAN -> (if (cell.booleanCellValue) "TRUE" else "FALSE") to null
        CellType.ERROR -> (runCatching { FormulaError.forInt(cell.errorCellValue).string }.getOrDefault("")) to null
        else -> "" to null
    }

    private fun style(book: HSSFWorkbook, cell: HSSFCell): CellStyle {
        val s = cell.cellStyle
        val font = s.getFont(book)
        val palette = book.customPalette
        fun rgb(index: Short): Int? = palette.getColor(index)?.triplet?.let { (r, g, b) -> (0xFF shl 24) or (r shl 16) or (g shl 8) or b }
        val fmt = s.dataFormatString?.takeIf { it != "General" }
        return CellStyle(
            bold = font.bold, italic = font.italic, underline = font.underline.toInt() != 0, strike = font.strikeout,
            color = font.color.takeIf { it != HSSFColor.HSSFColorPredefined.AUTOMATIC.index && it != HSSFColor.HSSFColorPredefined.BLACK.index }?.let { rgb(it) },
            fill = if (s.fillPattern == FillPatternType.SOLID_FOREGROUND) rgb(s.fillForegroundColor) else null,
            align = when (s.alignment) {
                HorizontalAlignment.LEFT -> CellStyle.Align.LEFT
                HorizontalAlignment.CENTER, HorizontalAlignment.CENTER_SELECTION -> CellStyle.Align.CENTER
                HorizontalAlignment.RIGHT -> CellStyle.Align.RIGHT
                else -> CellStyle.Align.GENERAL
            },
            wrap = s.wrapText,
            fontSize = font.fontHeightInPoints.toDouble().takeIf { it != 10.0 },
            numFmt = fmt,
        )
    }

    private operator fun ShortArray.component1() = this[0].toInt()
    private operator fun ShortArray.component2() = this[1].toInt()
    private operator fun ShortArray.component3() = this[2].toInt()
}
