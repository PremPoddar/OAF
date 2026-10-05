package com.prempoddar.oaf.sheet

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class FormulaTest {
    private fun book(vararg cells: Pair<String, String>): Workbook {
        val wb = Workbook()
        val s = wb.sheets[0]
        cells.forEach { (ref, input) -> val (r, c) = A1.parse(ref)!!; s.set(r, c, input) }
        wb.recalculate()
        return wb
    }

    private fun Workbook.at(ref: String, sheet: Int = 0): Value {
        val (r, c) = A1.parse(ref)!!
        return sheets[sheet][r, c]!!.value
    }

    private fun eval(f: String, wb: Workbook = book()) =
        Evaluator(wb, today = { LocalDate.of(2026, 10, 5) }).evaluate(f).display()

    @Test fun addressesRoundTrip() {
        assertThat(A1.name(0, 0)).isEqualTo("A1")
        assertThat(A1.colName(25)).isEqualTo("Z")
        assertThat(A1.colName(26)).isEqualTo("AA")
        assertThat(A1.colName(701)).isEqualTo("ZZ")
        assertThat(A1.colIndex("AAA")).isEqualTo(702)
        assertThat(A1.parse("\$C\$10")).isEqualTo(9 to 2)
        assertThat(A1.parseRange("B3:A1")).isEqualTo(CellRange(0, 0, 2, 1))
    }

    @Test fun arithmeticFollowsPrecedence() {
        assertThat(eval("=1+2*3")).isEqualTo("7")
        assertThat(eval("=(1+2)*3")).isEqualTo("9")
        assertThat(eval("=-2^2")).isEqualTo("4")
        assertThat(eval("=2^3^2")).isEqualTo("64")
        assertThat(eval("=50%")).isEqualTo("0.5")
        assertThat(eval("=1/0")).isEqualTo("#DIV/0!")
        assertThat(eval("=\"a\"&1+1")).isEqualTo("a2")
        assertThat(eval("=1+\"x\"")).isEqualTo("#VALUE!")
        assertThat(eval("=3>2")).isEqualTo("TRUE")
        assertThat(eval("=\"abc\"=\"ABC\"")).isEqualTo("TRUE")
        assertThat(eval("=0.1+0.2")).isEqualTo("0.3")
    }

    @Test fun referencesAndRanges() {
        val wb = book("A1" to "10", "A2" to "20", "A3" to "hello", "A4" to "=SUM(A1:A3)", "B1" to "=A4*2", "C1" to "=A1+A2+Z99")
        assertThat(wb.at("A4").display()).isEqualTo("30")
        assertThat(wb.at("B1").display()).isEqualTo("60")
        assertThat(wb.at("C1").display()).isEqualTo("30")
    }

    @Test fun crossSheetReferences() {
        val wb = Workbook(mutableListOf(Sheet("Data"), Sheet("My Sheet")))
        wb.sheets[0].set(0, 0, "5")
        wb.sheets[1].set(0, 0, "=Data!A1*2")
        wb.sheets[1].set(0, 1, "='My Sheet'!A1+1")
        wb.sheets[1].set(0, 2, "=Nope!A1")
        wb.recalculate()
        assertThat(wb.at("A1", 1).display()).isEqualTo("10")
        assertThat(wb.at("B1", 1).display()).isEqualTo("11")
        assertThat(wb.at("C1", 1)).isEqualTo(Value.REF)
    }

    @Test fun cyclesAreCaught() {
        val wb = book("A1" to "=B1+1", "B1" to "=A1+1", "C1" to "=C1", "D1" to "=5")
        assertThat(wb.at("A1")).isEqualTo(Value.CIRC)
        assertThat(wb.at("C1")).isEqualTo(Value.CIRC)
        assertThat(wb.at("D1").display()).isEqualTo("5")
    }

    @Test fun errorsPropagateAndCanBeCaught() {
        val wb = book("A1" to "=1/0", "A2" to "=A1+1", "A3" to "=IFERROR(A2,\"oops\")", "A4" to "=NOPE(1)", "A5" to "=ISERROR(A1)")
        assertThat(wb.at("A2")).isEqualTo(Value.DIV0)
        assertThat(wb.at("A3").display()).isEqualTo("oops")
        assertThat(wb.at("A4")).isEqualTo(Value.NAME)
        assertThat(wb.at("A5").display()).isEqualTo("TRUE")
        assertThat(eval("=IFNA(NA(),1)")).isEqualTo("1")
        assertThat(eval("=IFNA(1/0,1)")).isEqualTo("#DIV/0!")
        assertThat(eval("=SUM(1,")).isEqualTo("#ERROR!")
    }

    @Test fun aggregates() {
        val wb = book("A1" to "1", "A2" to "2", "A3" to "3", "A4" to "4", "A5" to "x", "A6" to "TRUE")
        fun f(s: String) = eval(s, wb)
        assertThat(f("=SUM(A1:A6)")).isEqualTo("10")
        assertThat(f("=AVERAGE(A1:A6)")).isEqualTo("2.5")
        assertThat(f("=COUNT(A1:A6)")).isEqualTo("4")
        assertThat(f("=COUNTA(A1:A6)")).isEqualTo("6")
        assertThat(f("=COUNTBLANK(A1:A8)")).isEqualTo("2")
        assertThat(f("=MIN(A1:A6,0)")).isEqualTo("0")
        assertThat(f("=MAX(A:A)")).isEqualTo("4")
        assertThat(f("=MEDIAN(A1:A4)")).isEqualTo("2.5")
        assertThat(f("=LARGE(A1:A4,2)")).isEqualTo("3")
        assertThat(f("=SMALL(A1:A4,1)")).isEqualTo("1")
        assertThat(f("=RANK(3,A1:A4)")).isEqualTo("2")
        assertThat(f("=PRODUCT(A1:A4)")).isEqualTo("24")
        assertThat(f("=ROUND(STDEV(A1:A4),4)")).isEqualTo("1.291")
        assertThat(f("=VAR.P(A1:A4)")).isEqualTo("1.25")
        assertThat(f("=SUMPRODUCT(A1:A2,A3:A4)")).isEqualTo("11")
        assertThat(f("=AVERAGE(A5)")).isEqualTo("#DIV/0!")
    }

    @Test fun conditionalAggregates() {
        val wb = book(
            "A1" to "apple", "B1" to "3",
            "A2" to "banana", "B2" to "5",
            "A3" to "apple", "B3" to "7",
            "A4" to "cherry", "B4" to "1",
        )
        fun f(s: String) = eval(s, wb)
        assertThat(f("=SUMIF(A1:A4,\"apple\",B1:B4)")).isEqualTo("10")
        assertThat(f("=COUNTIF(B1:B4,\">2\")")).isEqualTo("3")
        assertThat(f("=COUNTIF(A1:A4,\"<>apple\")")).isEqualTo("2")
        assertThat(f("=COUNTIF(A1:A4,\"b*\")")).isEqualTo("1")
        assertThat(f("=COUNTIF(B1:B4,5)")).isEqualTo("1")
        assertThat(f("=AVERAGEIF(A1:A4,\"apple\",B1:B4)")).isEqualTo("5")
        assertThat(f("=SUMIFS(B1:B4,A1:A4,\"apple\",B1:B4,\">4\")")).isEqualTo("7")
        assertThat(f("=COUNTIFS(A1:A4,\"?????\",B1:B4,\"<=3\")")).isEqualTo("1")
    }

    @Test fun logic() {
        assertThat(eval("=IF(1>2,\"a\",\"b\")")).isEqualTo("b")
        assertThat(eval("=IF(FALSE,1)")).isEqualTo("FALSE")
        assertThat(eval("=IFS(1>2,\"a\",2>1,\"b\")")).isEqualTo("b")
        assertThat(eval("=AND(TRUE,1,2>1)")).isEqualTo("TRUE")
        assertThat(eval("=OR(FALSE,0)")).isEqualTo("FALSE")
        assertThat(eval("=XOR(TRUE,TRUE,TRUE)")).isEqualTo("TRUE")
        assertThat(eval("=NOT(0)")).isEqualTo("TRUE")
        assertThat(eval("=CHOOSE(2,\"x\",\"y\",\"z\")")).isEqualTo("y")
        // IF only evaluates the branch it takes.
        assertThat(eval("=IF(TRUE,1,1/0)")).isEqualTo("1")
    }

    @Test fun maths() {
        assertThat(eval("=ROUND(2.345,2)")).isEqualTo("2.35")
        assertThat(eval("=ROUND(-2.5,0)")).isEqualTo("-3")
        assertThat(eval("=ROUND(1234,-2)")).isEqualTo("1200")
        assertThat(eval("=ROUNDUP(1.21,1)")).isEqualTo("1.3")
        assertThat(eval("=ROUNDDOWN(-1.29,1)")).isEqualTo("-1.2")
        assertThat(eval("=INT(-1.5)")).isEqualTo("-2")
        assertThat(eval("=MOD(-3,2)")).isEqualTo("1")
        assertThat(eval("=MOD(1,0)")).isEqualTo("#DIV/0!")
        assertThat(eval("=SQRT(-1)")).isEqualTo("#NUM!")
        assertThat(eval("=CEILING(4.1,2)")).isEqualTo("6")
        assertThat(eval("=FLOOR(4.9,2)")).isEqualTo("4")
        assertThat(eval("=ABS(-3)+POWER(2,10)")).isEqualTo("1027")
        assertThat(eval("=LOG(1000)")).isEqualTo("3")
        assertThat(eval("=LOG10(100)")).isEqualTo("2")
    }

    @Test fun text() {
        assertThat(eval("=CONCAT(\"a\",1,TRUE)")).isEqualTo("a1TRUE")
        assertThat(eval("=TEXTJOIN(\"-\",TRUE,\"a\",\"\",\"b\")")).isEqualTo("a-b")
        assertThat(eval("=LEFT(\"hello\",2)&RIGHT(\"hello\")")).isEqualTo("heo")
        assertThat(eval("=MID(\"hello\",2,3)")).isEqualTo("ell")
        assertThat(eval("=LEN(\"hello\")")).isEqualTo("5")
        assertThat(eval("=UPPER(\"a\")&LOWER(\"B\")")).isEqualTo("Ab")
        assertThat(eval("=PROPER(\"hello wORLD-foo\")")).isEqualTo("Hello World-Foo")
        assertThat(eval("=TRIM(\"  a   b  \")")).isEqualTo("a b")
        assertThat(eval("=SUBSTITUTE(\"a-b-c\",\"-\",\"+\")")).isEqualTo("a+b+c")
        assertThat(eval("=SUBSTITUTE(\"a-b-c\",\"-\",\"+\",2)")).isEqualTo("a-b+c")
        assertThat(eval("=REPLACE(\"abcdef\",2,3,\"X\")")).isEqualTo("aXef")
        assertThat(eval("=FIND(\"b\",\"abcb\",3)")).isEqualTo("4")
        assertThat(eval("=SEARCH(\"B\",\"abc\")")).isEqualTo("2")
        assertThat(eval("=FIND(\"B\",\"abc\")")).isEqualTo("#VALUE!")
        assertThat(eval("=REPT(\"ab\",3)")).isEqualTo("ababab")
        assertThat(eval("=VALUE(\"1,234.5\")+1")).isEqualTo("1235.5")
        assertThat(eval("=TEXT(1234.567,\"#,##0.00\")")).isEqualTo("1,234.57")
        assertThat(eval("=TEXT(0.256,\"0.0%\")")).isEqualTo("25.6%")
        assertThat(eval("=TEXT(DATE(2026,3,7),\"yyyy-mm-dd\")")).isEqualTo("2026-03-07")
        assertThat(eval("=\"say \"\"hi\"\"\"")).isEqualTo("say \"hi\"")
    }

    @Test fun lookups() {
        val wb = book(
            "A1" to "1", "B1" to "one", "C1" to "uno",
            "A2" to "2", "B2" to "two", "C2" to "dos",
            "A3" to "3", "B3" to "three", "C3" to "tres",
        )
        fun f(s: String) = eval(s, wb)
        assertThat(f("=VLOOKUP(2,A1:C3,2,FALSE)")).isEqualTo("two")
        assertThat(f("=VLOOKUP(2.5,A1:C3,3)")).isEqualTo("dos")
        assertThat(f("=VLOOKUP(9,A1:C3,2,FALSE)")).isEqualTo("#N/A")
        assertThat(f("=VLOOKUP(1,A1:C3,4,FALSE)")).isEqualTo("#REF!")
        assertThat(f("=HLOOKUP(\"two\",B1:B3,1,FALSE)")).isEqualTo("#N/A")
        assertThat(f("=HLOOKUP(1,A1:C3,3,FALSE)")).isEqualTo("3")
        assertThat(f("=XLOOKUP(\"three\",B1:B3,C1:C3)")).isEqualTo("tres")
        assertThat(f("=XLOOKUP(\"four\",B1:B3,C1:C3,\"none\")")).isEqualTo("none")
        assertThat(f("=MATCH(\"TWO\",B1:B3,0)")).isEqualTo("2")
        assertThat(f("=INDEX(A1:C3,3,2)")).isEqualTo("three")
        assertThat(f("=INDEX(B1:B3,2)")).isEqualTo("two")
        assertThat(f("=INDEX(A1:C3,MATCH(3,A1:A3,0),3)")).isEqualTo("tres")
        assertThat(f("=ROWS(A1:C3)*COLUMNS(A1:B1)")).isEqualTo("6")
    }

    @Test fun dates() {
        assertThat(eval("=DATE(1900,3,1)")).isEqualTo("61")
        assertThat(eval("=DATE(2026,10,5)")).isEqualTo("46300")
        assertThat(eval("=TODAY()")).isEqualTo("46300")
        assertThat(eval("=DATE(2026,13,1)=DATE(2027,1,1)")).isEqualTo("TRUE")
        assertThat(eval("=YEAR(46300)&\"/\"&MONTH(46300)&\"/\"&DAY(46300)")).isEqualTo("2026/10/5")
        assertThat(eval("=WEEKDAY(DATE(2026,10,5))")).isEqualTo("2")
        assertThat(eval("=EOMONTH(DATE(2024,1,15),1)=DATE(2024,2,29)")).isEqualTo("TRUE")
        assertThat(eval("=EDATE(DATE(2024,1,31),1)=DATE(2024,2,29)")).isEqualTo("TRUE")
        assertThat(eval("=DATEDIF(DATE(2000,5,10),DATE(2026,5,9),\"Y\")")).isEqualTo("25")
        assertThat(eval("=DAYS(DATE(2026,1,1),DATE(2025,1,1))")).isEqualTo("365")
        assertThat(eval("=HOUR(TIME(13,45,10))&\":\"&MINUTE(TIME(13,45,10))&\":\"&SECOND(TIME(13,45,10))")).isEqualTo("13:45:10")
    }

    @Test fun literalsAreUnderstood() {
        assertThat(Workbook.literal("1,234")).isEqualTo(Value.Num(1234.0))
        assertThat(Workbook.literal("12%")).isEqualTo(Value.Num(0.12))
        assertThat(Workbook.literal("true")).isEqualTo(Value.Bool(true))
        assertThat(Workbook.literal("'123")).isEqualTo(Value.Text("123"))
        assertThat(Workbook.literal("abc")).isEqualTo(Value.Text("abc"))
    }

    @Test fun sortingAndShifting() {
        val wb = book("A1" to "Name", "A2" to "c", "A3" to "a", "A4" to "10", "A5" to "b", "B2" to "3", "B3" to "1", "B4" to "4", "B5" to "2")
        val s = wb.sheets[0]
        s.sortBy(0, 1, ascending = true)
        assertThat((1..4).map { s[it, 0]!!.input }).containsExactly("10", "a", "b", "c").inOrder()
        assertThat(s[2, 1]!!.input).isEqualTo("1")
        s.shiftRows(1, 2)
        assertThat(s[1, 0]).isNull()
        assertThat(s[3, 0]!!.input).isEqualTo("10")
        s.shiftRows(1, -2)
        assertThat(s[1, 0]!!.input).isEqualTo("10")
        s.shiftCols(0, -1)
        assertThat(s[2, 0]!!.input).isEqualTo("1")
    }
}
