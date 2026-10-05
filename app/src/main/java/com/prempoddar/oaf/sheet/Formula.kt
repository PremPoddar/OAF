package com.prempoddar.oaf.sheet

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong

/** Parsed formula. */
sealed interface Node {
    data class Num(val v: Double) : Node
    data class Str(val v: String) : Node
    data class Bool(val v: Boolean) : Node
    data class ErrLit(val code: String) : Node
    data class Ref(val sheet: String?, val r: Int, val c: Int) : Node
    data class Area(val sheet: String?, val range: CellRange) : Node
    data class Call(val name: String, val args: List<Node>) : Node
    data class Unary(val op: Char, val x: Node) : Node
    data class Binary(val op: String, val a: Node, val b: Node) : Node
    data class Percent(val x: Node) : Node
}

class FormulaError(val value: Value.Err) : Exception(value.code)

/** Turns "=SUM(A1:B2)*2" into a [Node] tree. Excel precedence: comparison < & < +- < * and / < ^ < unary < %. */
class FormulaParser(input: String) {
    private val src = input.trimStart().removePrefix("=")
    private var i = 0

    fun parse(): Node {
        val n = comparison()
        skip()
        if (i < src.length) throw FormulaError(Value.Err("#ERROR!"))
        return n
    }

    private fun skip() { while (i < src.length && src[i].isWhitespace()) i++ }
    private fun peek(s: String): Boolean { skip(); return src.startsWith(s, i) }
    private fun eat(s: String): Boolean = if (peek(s)) { i += s.length; true } else false

    private fun comparison(): Node {
        var left = concat()
        while (true) {
            val op = listOf("<=", ">=", "<>", "=", "<", ">").firstOrNull { peek(it) } ?: return left
            i += op.length
            left = Node.Binary(op, left, concat())
        }
    }

    private fun concat(): Node {
        var left = additive()
        while (eat("&")) left = Node.Binary("&", left, additive())
        return left
    }

    private fun additive(): Node {
        var left = term()
        while (true) {
            left = when {
                eat("+") -> Node.Binary("+", left, term())
                eat("-") -> Node.Binary("-", left, term())
                else -> return left
            }
        }
    }

    private fun term(): Node {
        var left = power()
        while (true) {
            left = when {
                eat("*") -> Node.Binary("*", left, power())
                eat("/") -> Node.Binary("/", left, power())
                else -> return left
            }
        }
    }

    private fun power(): Node {
        var left = unary()
        while (eat("^")) left = Node.Binary("^", left, unary())
        return left
    }

    private fun unary(): Node = when {
        eat("-") -> Node.Unary('-', unary())
        eat("+") -> unary()
        else -> postfix()
    }

    private fun postfix(): Node {
        var n = primary()
        while (eat("%")) n = Node.Percent(n)
        return n
    }

    private fun primary(): Node {
        skip()
        if (i >= src.length) throw FormulaError(Value.Err("#ERROR!"))
        val c = src[i]
        when {
            c == '(' -> { i++; val n = comparison(); if (!eat(")")) throw FormulaError(Value.Err("#ERROR!")); return n }
            c == '"' -> {
                val sb = StringBuilder(); i++
                while (i < src.length) {
                    if (src[i] == '"') { if (i + 1 < src.length && src[i + 1] == '"') { sb.append('"'); i += 2; continue }; i++; return Node.Str(sb.toString()) }
                    sb.append(src[i]); i++
                }
                throw FormulaError(Value.Err("#ERROR!"))
            }
            c == '#' -> {
                val m = Regex("#(DIV/0!|VALUE!|REF!|NAME\\?|N/A|NUM!|NULL!)").find(src, i)?.takeIf { it.range.first == i } ?: throw FormulaError(Value.Err("#ERROR!"))
                i = m.range.last + 1
                return Node.ErrLit(m.value)
            }
            c.isDigit() || c == '.' -> {
                val m = Regex("\\d*\\.?\\d+([eE][+-]?\\d+)?").find(src, i)!!
                i = m.range.last + 1
                return Node.Num(m.value.toDouble())
            }
        }
        // Sheet-qualified reference: 'My Sheet'!A1 or Sheet1!A1:B2
        var sheet: String? = null
        if (c == '\'') {
            val end = src.indexOf("'!", i + 1)
            if (end < 0) throw FormulaError(Value.Err("#ERROR!"))
            sheet = src.substring(i + 1, end).replace("''", "'")
            i = end + 2
        } else {
            val m = Regex("[A-Za-z_][A-Za-z0-9_.]*!").find(src, i)
            if (m != null && m.range.first == i) { sheet = m.value.dropLast(1); i = m.range.last + 1 }
        }
        val ref = Regex("\\$?[A-Za-z]{1,3}\\$?\\d+(:\\$?[A-Za-z]{1,3}\\$?\\d+)?").find(src, i)
        if (ref != null && ref.range.first == i && (ref.range.last + 1 >= src.length || !src[ref.range.last + 1].let { it.isLetterOrDigit() || it == '(' })) {
            i = ref.range.last + 1
            val range = A1.parseRange(ref.value.replace("$", "")) ?: throw FormulaError(Value.REF)
            return if (ref.value.contains(':')) Node.Area(sheet, range) else Node.Ref(sheet, range.r1, range.c1)
        }
        if (sheet != null) throw FormulaError(Value.REF)
        // Whole-column ranges like A:A.
        val col = Regex("\\$?([A-Za-z]{1,3}):\\$?([A-Za-z]{1,3})").find(src, i)
        if (col != null && col.range.first == i) {
            i = col.range.last + 1
            val a = A1.colIndex(col.groupValues[1]); val b = A1.colIndex(col.groupValues[2])
            return Node.Area(null, CellRange(0, minOf(a, b), 1_048_575, maxOf(a, b)))
        }
        val name = Regex("[A-Za-z_][A-Za-z0-9_.]*").find(src, i)?.takeIf { it.range.first == i } ?: throw FormulaError(Value.Err("#ERROR!"))
        i = name.range.last + 1
        val upper = name.value.uppercase()
        if (eat("(")) {
            val args = ArrayList<Node>()
            if (!eat(")")) {
                do {
                    skip()
                    args += if (peek(",") || peek(";") || peek(")")) Node.Str("") else comparison()
                } while (eat(",") || eat(";"))
                if (!eat(")")) throw FormulaError(Value.Err("#ERROR!"))
            }
            return Node.Call(upper.removePrefix("_XLFN."), args)
        }
        return when (upper) {
            "TRUE" -> Node.Bool(true)
            "FALSE" -> Node.Bool(false)
            else -> throw FormulaError(Value.NAME)
        }
    }
}

/** Computes formulas against a [Workbook], following references and catching loops. */
class Evaluator(private val wb: Workbook, private val today: () -> LocalDate = { LocalDate.now() }, private val now: () -> LocalDateTime = { LocalDateTime.now() }) {
    private val done = HashMap<Pair<Sheet, Long>, Value>()
    private val visiting = HashSet<Pair<Sheet, Long>>()
    private val parsed = HashMap<String, Node?>()

    private sealed interface R {
        data class S(val v: Value) : R
        data class A(val sheet: Sheet, val range: CellRange) : R
    }

    fun cellValue(sheet: Sheet, r: Int, c: Int): Value {
        val key = sheet to sheet.key(r, c)
        done[key]?.let { return it }
        val cell = sheet[r, c] ?: return Value.Empty
        if (!cell.isFormula) return Workbook.literal(cell.input).also { done[key] = it }
        if (!visiting.add(key)) return Value.CIRC
        val v = try {
            val node = parsed.getOrPut(cell.input) { runCatching { FormulaParser(cell.input).parse() }.getOrNull() } ?: throw FormulaError(Value.Err("#ERROR!"))
            when (val res = eval(node, sheet)) {
                is R.S -> res.v.let { if (it == Value.Empty) Value.Num(0.0) else it }
                is R.A -> if (res.range.r1 == res.range.r2 && res.range.c1 == res.range.c2) cellValue(res.sheet, res.range.r1, res.range.c1) else Value.VALUE
            }
        } catch (e: FormulaError) {
            e.value
        } catch (e: StackOverflowError) {
            Value.CIRC
        }
        visiting.remove(key)
        done[key] = v
        return v
    }

    /** Evaluates a formula typed somewhere, e.g. for tests or a preview. */
    fun evaluate(formula: String, sheet: Sheet = wb.sheets.first()): Value = try {
        when (val r = eval(FormulaParser(formula).parse(), sheet)) {
            is R.S -> r.v
            is R.A -> scalar(r)
        }
    } catch (e: FormulaError) {
        e.value
    }

    private fun sheetOf(name: String?, current: Sheet) = if (name == null) current else wb.sheet(name) ?: throw FormulaError(Value.REF)

    private fun eval(n: Node, sheet: Sheet): R = when (n) {
        is Node.Num -> R.S(Value.Num(n.v))
        is Node.Str -> R.S(Value.Text(n.v))
        is Node.Bool -> R.S(Value.Bool(n.v))
        is Node.ErrLit -> R.S(Value.Err(n.code))
        is Node.Ref -> R.S(cellValue(sheetOf(n.sheet, sheet), n.r, n.c))
        is Node.Area -> R.A(sheetOf(n.sheet, sheet), clip(sheetOf(n.sheet, sheet), n.range))
        is Node.Unary -> R.S(Value.Num(-num(eval(n.x, sheet))))
        is Node.Percent -> R.S(Value.Num(num(eval(n.x, sheet)) / 100))
        is Node.Binary -> R.S(binary(n.op, scalar(eval(n.a, sheet)), scalar(eval(n.b, sheet))))
        is Node.Call -> call(n.name, n.args, sheet)
    }

    /** Whole-column ranges only go as far as the data does. */
    private fun clip(s: Sheet, r: CellRange) = if (r.r2 - r.r1 > 100_000) r.copy(r2 = maxOf(r.r1, s.maxRow)) else r

    private fun scalar(r: R): Value = when (r) {
        is R.S -> r.v.also { if (it is Value.Err) throw FormulaError(it) }
        is R.A -> if (r.range.r1 == r.range.r2 && r.range.c1 == r.range.c2) cellValue(r.sheet, r.range.r1, r.range.c1).also { if (it is Value.Err) throw FormulaError(it) } else throw FormulaError(Value.VALUE)
    }

    private fun num(r: R): Double = toNum(scalar(r))
    private fun text(r: R): String = toText(scalar(r))
    private fun bool(r: R): Boolean = toBool(scalar(r))

    private fun toNum(v: Value): Double = when (v) {
        is Value.Num -> v.v
        is Value.Bool -> if (v.v) 1.0 else 0.0
        is Value.Text -> v.v.trim().replace(",", "").toDoubleOrNull() ?: (if (v.v.trim().endsWith("%")) v.v.trim().dropLast(1).toDoubleOrNull()?.div(100) else null) ?: throw FormulaError(Value.VALUE)
        Value.Empty -> 0.0
        is Value.Err -> throw FormulaError(v)
    }

    private fun toText(v: Value): String = when (v) {
        is Value.Err -> throw FormulaError(v)
        else -> v.display()
    }

    private fun toBool(v: Value): Boolean = when (v) {
        is Value.Bool -> v.v
        is Value.Num -> v.v != 0.0
        is Value.Text -> when (v.v.uppercase()) { "TRUE" -> true; "FALSE" -> false; else -> throw FormulaError(Value.VALUE) }
        Value.Empty -> false
        is Value.Err -> throw FormulaError(v)
    }

    private fun binary(op: String, a: Value, b: Value): Value = when (op) {
        "+" -> Value.Num(toNum(a) + toNum(b))
        "-" -> Value.Num(toNum(a) - toNum(b))
        "*" -> Value.Num(toNum(a) * toNum(b))
        "/" -> toNum(b).let { d -> if (d == 0.0) Value.DIV0 else Value.Num(toNum(a) / d) }
        "^" -> Value.Num(toNum(a).pow(toNum(b))).let { if (it.v.isNaN()) Value.NUM else it }
        "&" -> Value.Text(toText(a) + toText(b))
        else -> Value.Bool(compare(a, b).let { c ->
            when (op) { "=" -> c == 0; "<>" -> c != 0; "<" -> c < 0; ">" -> c > 0; "<=" -> c <= 0; else -> c >= 0 }
        })
    }

    /** Excel ordering: numbers < text < booleans; text compares without case. */
    private fun compare(a: Value, b: Value): Int {
        fun norm(v: Value) = if (v == Value.Empty) null else v
        val x = norm(a); val y = norm(b)
        if (x == null && y == null) return 0
        val xx = x ?: if (y is Value.Text) Value.Text("") else if (y is Value.Bool) Value.Bool(false) else Value.Num(0.0)
        val yy = y ?: if (x is Value.Text) Value.Text("") else if (x is Value.Bool) Value.Bool(false) else Value.Num(0.0)
        fun rank(v: Value) = when (v) { is Value.Num -> 0; is Value.Text -> 1; is Value.Bool -> 2; else -> 3 }
        if (rank(xx) != rank(yy)) return rank(xx) - rank(yy)
        return when (xx) {
            is Value.Num -> xx.v.compareTo((yy as Value.Num).v)
            is Value.Text -> xx.v.compareTo((yy as Value.Text).v, ignoreCase = true)
            is Value.Bool -> xx.v.compareTo((yy as Value.Bool).v)
            else -> 0
        }
    }

    // Collecting values from arguments.

    /** Every value in the arguments: ranges cell by cell, scalars as they are. */
    private fun values(args: List<Node>, sheet: Sheet): List<Pair<Value, Boolean>> = args.flatMap { a ->
        when (val r = eval(a, sheet)) {
            is R.S -> listOf(r.v to (a !is Node.Ref))
            is R.A -> cellsOf(r).map { it to false }
        }
    }

    private fun cellsOf(r: R.A): List<Value> {
        val out = ArrayList<Value>()
        for (row in r.range.r1..r.range.r2) for (col in r.range.c1..r.range.c2) out += cellValue(r.sheet, row, col)
        return out
    }

    private fun grid(r: R): List<List<Value>> = when (r) {
        is R.S -> listOf(listOf(r.v))
        is R.A -> (r.range.r1..r.range.r2).map { row -> (r.range.c1..r.range.c2).map { col -> cellValue(r.sheet, row, col) } }
    }

    /** Numbers for SUM-like functions: from ranges only real numbers count; typed values are coerced. */
    private fun numbers(args: List<Node>, sheet: Sheet): List<Double> = values(args, sheet).mapNotNull { (v, direct) ->
        when {
            v is Value.Err -> throw FormulaError(v)
            v is Value.Num -> v.v
            direct && v != Value.Empty -> toNum(v)
            else -> null
        }
    }

    private fun criteria(raw: Value): (Value) -> Boolean {
        val s = raw.display()
        val op = listOf("<=", ">=", "<>", "=", "<", ">").firstOrNull { s.startsWith(it) } ?: ""
        val rest = s.substring(op.length)
        val n = rest.trim().toDoubleOrNull()
        val pattern = if (rest.contains('*') || rest.contains('?')) Regex(rest.split("*").joinToString(".*") { p -> p.split("?").joinToString(".") { Regex.escape(it) } }, RegexOption.IGNORE_CASE) else null
        return { v ->
            // null means the types can't be compared: only "<>" matches then.
            val c: Int? = when {
                n != null -> (v as? Value.Num)?.v?.compareTo(n)
                pattern != null -> if (pattern.matches(v.display())) 0 else 1
                v is Value.Num && rest.isNotEmpty() -> null
                else -> v.display().compareTo(rest, ignoreCase = true)
            }
            if (c == null) op == "<>"
            else when (op) { "", "=" -> c == 0; "<>" -> c != 0; "<" -> c < 0; ">" -> c > 0; "<=" -> c <= 0; else -> c >= 0 }
        }
    }

    private fun need(args: List<Node>, min: Int, max: Int = min) {
        if (args.size < min || args.size > max) throw FormulaError(Value.VALUE)
    }

    private fun call(name: String, a: List<Node>, s: Sheet): R {
        fun n(i: Int) = num(eval(a[i], s))
        fun t(i: Int) = text(eval(a[i], s))
        fun b(i: Int) = bool(eval(a[i], s))
        fun opt(i: Int, d: Double) = if (a.size > i) n(i) else d
        fun v(x: Double) = R.S(if (x.isNaN() || x.isInfinite()) Value.NUM else Value.Num(x))
        fun str(x: String) = R.S(Value.Text(x))
        fun bl(x: Boolean) = R.S(Value.Bool(x))
        return when (name) {
            // Maths
            "SUM" -> v(numbers(a, s).sum())
            "PRODUCT" -> v(numbers(a, s).fold(1.0) { x, y -> x * y })
            "AVERAGE" -> numbers(a, s).let { if (it.isEmpty()) R.S(Value.DIV0) else v(it.average()) }
            "MIN" -> v(numbers(a, s).minOrNull() ?: 0.0)
            "MAX" -> v(numbers(a, s).maxOrNull() ?: 0.0)
            "COUNT" -> v(values(a, s).count { (x, direct) -> x is Value.Num || (direct && x is Value.Text && x.v.toDoubleOrNull() != null) }.toDouble())
            "COUNTA" -> v(values(a, s).count { it.first != Value.Empty }.toDouble())
            "COUNTBLANK" -> v(values(a, s).count { it.first == Value.Empty || (it.first as? Value.Text)?.v == "" }.toDouble())
            "MEDIAN" -> numbers(a, s).sorted().let { l -> if (l.isEmpty()) R.S(Value.NUM) else v(if (l.size % 2 == 1) l[l.size / 2] else (l[l.size / 2 - 1] + l[l.size / 2]) / 2) }
            "MODE", "MODE.SNGL" -> numbers(a, s).groupingBy { it }.eachCount().let { m -> m.maxByOrNull { it.value }?.takeIf { it.value > 1 }?.let { v(it.key) } ?: R.S(Value.NA) }
            "STDEV", "STDEV.S", "VAR", "VAR.S", "STDEV.P", "STDEVP", "VAR.P", "VARP" -> {
                val l = numbers(a, s)
                val pop = name.endsWith("P")
                val nn = if (pop) l.size else l.size - 1
                if (nn <= 0) R.S(Value.DIV0) else {
                    val mean = l.average()
                    val variance = l.sumOf { (it - mean) * (it - mean) } / nn
                    v(if (name.startsWith("STDEV")) kotlin.math.sqrt(variance) else variance)
                }
            }
            "LARGE", "SMALL" -> {
                need(a, 2)
                val l = numbers(listOf(a[0]), s).sorted().let { if (name == "LARGE") it.reversed() else it }
                val k = n(1).toInt()
                if (k < 1 || k > l.size) R.S(Value.NUM) else v(l[k - 1])
            }
            "RANK", "RANK.EQ" -> {
                val x = n(0)
                val l = numbers(listOf(a[1]), s)
                val desc = a.size < 3 || n(2) == 0.0
                if (x !in l) R.S(Value.NA) else v((l.count { if (desc) it > x else it < x } + 1).toDouble())
            }
            "ABS" -> v(kotlin.math.abs(n(0)))
            "INT" -> v(floor(n(0)))
            "ROUND", "ROUNDUP", "ROUNDDOWN" -> {
                val x = n(0); val d = opt(1, 0.0).toInt()
                val f = 10.0.pow(d)
                v(when (name) {
                    "ROUND" -> Math.round(kotlin.math.abs(x) * f + 1e-9).toDouble() / f * kotlin.math.sign(x)
                    "ROUNDUP" -> kotlin.math.ceil(kotlin.math.abs(x) * f - 1e-9) / f * kotlin.math.sign(x)
                    else -> floor(kotlin.math.abs(x) * f + 1e-9) / f * kotlin.math.sign(x)
                })
            }
            "CEILING", "CEILING.MATH" -> { val sig = opt(1, 1.0); if (sig == 0.0) v(0.0) else v(kotlin.math.ceil(n(0) / sig) * sig) }
            "FLOOR", "FLOOR.MATH" -> { val sig = opt(1, 1.0); if (sig == 0.0) R.S(Value.DIV0) else v(floor(n(0) / sig) * sig) }
            "MOD" -> { val d = n(1); if (d == 0.0) R.S(Value.DIV0) else v(n(0) - d * floor(n(0) / d)) }
            "POWER" -> v(n(0).pow(n(1)))
            "SQRT" -> n(0).let { if (it < 0) R.S(Value.NUM) else v(kotlin.math.sqrt(it)) }
            "EXP" -> v(kotlin.math.exp(n(0)))
            "LN" -> n(0).let { if (it <= 0) R.S(Value.NUM) else v(kotlin.math.ln(it)) }
            "LOG10" -> n(0).let { if (it <= 0) R.S(Value.NUM) else v(kotlin.math.log10(it)) }
            "LOG" -> n(0).let { x -> if (x <= 0) R.S(Value.NUM) else v(kotlin.math.ln(x) / kotlin.math.ln(opt(1, 10.0))) }
            "PI" -> v(Math.PI)
            "SIGN" -> v(kotlin.math.sign(n(0)))
            "RAND" -> v(Math.random())
            "RANDBETWEEN" -> v((n(0).toLong() + (Math.random() * (n(1) - n(0) + 1)).toLong()).toDouble())
            "SUMPRODUCT" -> {
                val grids = a.map { grid(eval(it, s)).flatten() }
                if (grids.map { it.size }.distinct().size != 1) R.S(Value.VALUE)
                else v(grids[0].indices.sumOf { i -> grids.fold(1.0) { acc, g -> acc * ((g[i] as? Value.Num)?.v ?: 0.0) } })
            }
            "SUMIF", "AVERAGEIF", "COUNTIF" -> {
                val range = grid(eval(a[0], s)).flatten()
                val test = criteria(scalar(eval(a[1], s)))
                val target = if (name != "COUNTIF" && a.size > 2) grid(eval(a[2], s)).flatten() else range
                val hits = range.indices.filter { test(range[it]) }
                when (name) {
                    "COUNTIF" -> v(hits.size.toDouble())
                    "SUMIF" -> v(hits.sumOf { (target.getOrNull(it) as? Value.Num)?.v ?: 0.0 })
                    else -> hits.mapNotNull { (target.getOrNull(it) as? Value.Num)?.v }.let { if (it.isEmpty()) R.S(Value.DIV0) else v(it.average()) }
                }
            }
            "SUMIFS", "COUNTIFS", "AVERAGEIFS" -> {
                val offset = if (name == "COUNTIFS") 0 else 1
                val target = if (offset == 1) grid(eval(a[0], s)).flatten() else null
                val pairs = (offset until a.size step 2).map { i -> grid(eval(a[i], s)).flatten() to criteria(scalar(eval(a[i + 1], s))) }
                val size = pairs.first().first.size
                val hits = (0 until size).filter { i -> pairs.all { (r, test) -> test(r.getOrElse(i) { Value.Empty }) } }
                when (name) {
                    "COUNTIFS" -> v(hits.size.toDouble())
                    "SUMIFS" -> v(hits.sumOf { (target!!.getOrNull(it) as? Value.Num)?.v ?: 0.0 })
                    else -> hits.mapNotNull { (target!!.getOrNull(it) as? Value.Num)?.v }.let { if (it.isEmpty()) R.S(Value.DIV0) else v(it.average()) }
                }
            }
            // Logic
            "IF" -> { need(a, 2, 3); if (b(0)) eval(a[1], s) else if (a.size > 2) eval(a[2], s) else bl(false) }
            "IFS" -> { var i = 0; var out: R? = null; while (i + 1 < a.size) { if (b(i)) { out = eval(a[i + 1], s); break }; i += 2 }; out ?: R.S(Value.NA) }
            "IFERROR", "IFNA" -> try {
                R.S(scalar(eval(a[0], s)))
            } catch (e: FormulaError) {
                if (name == "IFNA" && e.value.code != "#N/A") throw e else eval(a[1], s)
            }
            "AND" -> bl(values(a, s).filter { it.first != Value.Empty }.all { toBool(it.first) })
            "OR" -> bl(values(a, s).filter { it.first != Value.Empty }.any { toBool(it.first) })
            "XOR" -> bl(values(a, s).filter { it.first != Value.Empty }.count { toBool(it.first) } % 2 == 1)
            "NOT" -> bl(!b(0))
            "TRUE" -> bl(true)
            "FALSE" -> bl(false)
            "CHOOSE" -> { val k = n(0).toInt(); if (k < 1 || k >= a.size) R.S(Value.VALUE) else eval(a[k], s) }
            // Information
            "ISBLANK" -> bl(scalarOrErr(a[0], s) == Value.Empty)
            "ISNUMBER" -> bl(scalarOrErr(a[0], s) is Value.Num)
            "ISTEXT" -> bl(scalarOrErr(a[0], s) is Value.Text)
            "ISERROR" -> bl(scalarOrErr(a[0], s) is Value.Err)
            "ISNA" -> bl((scalarOrErr(a[0], s) as? Value.Err)?.code == "#N/A")
            "NA" -> R.S(Value.NA)
            // Text
            "CONCAT", "CONCATENATE" -> str(values(a, s).joinToString("") { toText(it.first) })
            "TEXTJOIN" -> {
                val sep = t(0); val skipEmpty = b(1)
                str(values(a.drop(2), s).map { toText(it.first) }.filter { !skipEmpty || it.isNotEmpty() }.joinToString(sep))
            }
            "LEFT" -> str(t(0).take(opt(1, 1.0).toInt().coerceAtLeast(0)))
            "RIGHT" -> str(t(0).takeLast(opt(1, 1.0).toInt().coerceAtLeast(0)))
            "MID" -> { val x = t(0); val start = n(1).toInt() - 1; if (start < 0) R.S(Value.VALUE) else str(x.drop(start).take(n(2).toInt().coerceAtLeast(0))) }
            "LEN" -> v(t(0).length.toDouble())
            "LOWER" -> str(t(0).lowercase())
            "UPPER" -> str(t(0).uppercase())
            "PROPER" -> str(t(0).lowercase().split(Regex("(?<=[^\\p{L}])|(?=[^\\p{L}])")).joinToString("") { it.replaceFirstChar { c -> c.uppercase() } })
            "TRIM" -> str(t(0).trim().replace(Regex(" +"), " "))
            "SUBSTITUTE" -> {
                val x = t(0); val old = t(1); val new = t(2)
                if (a.size > 3) {
                    val which = n(3).toInt(); var idx = -1
                    repeat(which) { idx = x.indexOf(old, idx + 1); if (idx < 0) return R.S(Value.Text(x)) }
                    str(x.replaceRange(idx, idx + old.length, new))
                } else str(if (old.isEmpty()) x else x.replace(old, new))
            }
            "REPLACE" -> { val x = t(0); val start = (n(1).toInt() - 1).coerceIn(0, x.length); str(x.replaceRange(start, (start + n(2).toInt()).coerceAtMost(x.length), t(3))) }
            "FIND", "SEARCH" -> {
                val needle = t(0); val hay = t(1); val from = opt(2, 1.0).toInt() - 1
                val idx = if (name == "FIND") hay.indexOf(needle, from) else hay.indexOf(needle, from, ignoreCase = true)
                if (idx < 0) R.S(Value.VALUE) else v((idx + 1).toDouble())
            }
            "REPT" -> str(t(0).repeat(n(1).toInt().coerceIn(0, 32_767)))
            "EXACT" -> bl(t(0) == t(1))
            "VALUE" -> v(toNum(Value.Text(t(0))))
            "TEXT" -> str(NumFmt.format(scalar(eval(a[0], s)), t(1)))
            // Lookup
            "VLOOKUP", "HLOOKUP" -> {
                val key = scalar(eval(a[0], s))
                var table = grid(eval(a[1], s))
                if (name == "HLOOKUP") table = table[0].indices.map { c -> table.map { it[c] } }
                val col = n(2).toInt() - 1
                val exact = a.size > 3 && !b(3)
                if (col < 0 || col >= (table.firstOrNull()?.size ?: 0)) R.S(Value.REF) else {
                    val row = if (exact) table.indexOfFirst { compare(it[0], key) == 0 } else table.indexOfLast { compare(it[0], key) <= 0 }
                    if (row < 0) R.S(Value.NA) else R.S(table[row][col])
                }
            }
            "XLOOKUP" -> {
                val key = scalar(eval(a[0], s))
                val look = grid(eval(a[1], s)).flatten()
                val ret = grid(eval(a[2], s)).flatten()
                val i = look.indexOfFirst { compare(it, key) == 0 }
                if (i >= 0) R.S(ret.getOrElse(i) { Value.NA }) else if (a.size > 3) eval(a[3], s) else R.S(Value.NA)
            }
            "MATCH" -> {
                val key = scalar(eval(a[0], s))
                val list = grid(eval(a[1], s)).flatten()
                val type = opt(2, 1.0).toInt()
                val i = when (type) {
                    0 -> list.indexOfFirst { compare(it, key) == 0 }
                    1 -> list.indexOfLast { it != Value.Empty && compare(it, key) <= 0 }
                    else -> list.indexOfLast { it != Value.Empty && compare(it, key) >= 0 }
                }
                if (i < 0) R.S(Value.NA) else v((i + 1).toDouble())
            }
            "INDEX" -> {
                val g = grid(eval(a[0], s))
                val r = n(1).toInt(); val c = if (a.size > 2) n(2).toInt() else 1
                val rr = if (g.size == 1 && a.size == 2) 1 else r
                val cc = if (g.size == 1 && a.size == 2) r else c
                g.getOrNull(rr - 1)?.getOrNull(cc - 1)?.let { R.S(it) } ?: R.S(Value.REF)
            }
            "ROWS" -> v(grid(eval(a[0], s)).size.toDouble())
            "COLUMNS" -> v((grid(eval(a[0], s)).firstOrNull()?.size ?: 0).toDouble())
            // Dates (Excel serial numbers, 1900 system)
            "TODAY" -> v(serial(today()))
            "NOW" -> now().let { d -> v(serial(d.toLocalDate()) + (d.toLocalTime().toSecondOfDay() / 86_400.0)) }
            "DATE" -> v(serial(LocalDate.of(n(0).toInt(), 1, 1).plusMonths(n(1).toLong() - 1).plusDays(n(2).toLong() - 1)))
            "TIME" -> v((n(0) * 3600 + n(1) * 60 + n(2)) / 86_400.0 % 1.0)
            "YEAR" -> v(date(n(0)).year.toDouble())
            "MONTH" -> v(date(n(0)).monthValue.toDouble())
            "DAY" -> v(date(n(0)).dayOfMonth.toDouble())
            "WEEKDAY" -> v((date(n(0)).dayOfWeek.value % 7 + 1).toDouble())
            "HOUR" -> v(floor((n(0) % 1) * 24 + 1e-9))
            "MINUTE" -> v(floor(((n(0) % 1) * 1440 + 1e-9) % 60))
            "SECOND" -> v(((n(0) % 1) * 86_400).roundToLong().toDouble() % 60)
            "DAYS" -> v(floor(n(0)) - floor(n(1)))
            "EDATE" -> v(serial(date(n(0)).plusMonths(n(1).toLong())))
            "EOMONTH" -> v(serial(date(n(0)).plusMonths(n(1).toLong()).let { it.withDayOfMonth(it.lengthOfMonth()) }))
            "DATEDIF" -> {
                val d1 = date(n(0)); val d2 = date(n(1))
                when (t(2).uppercase()) {
                    "D" -> v(ChronoUnit.DAYS.between(d1, d2).toDouble())
                    "M" -> v(ChronoUnit.MONTHS.between(d1, d2).toDouble())
                    "Y" -> v(ChronoUnit.YEARS.between(d1, d2).toDouble())
                    else -> R.S(Value.NUM)
                }
            }
            else -> R.S(Value.NAME)
        }
    }

    private fun scalarOrErr(n: Node, s: Sheet): Value = try { scalar(eval(n, s)) } catch (e: FormulaError) { e.value }

    companion object {
        private val EPOCH = LocalDate.of(1899, 12, 30)
        fun serial(d: LocalDate): Double = ChronoUnit.DAYS.between(EPOCH, d).toDouble()
        fun date(serial: Double): LocalDate = EPOCH.plusDays(floor(serial).toLong())

        /** Every function the engine knows, for the formula bar's suggestions. */
        val FUNCTIONS = listOf(
            "SUM", "AVERAGE", "MIN", "MAX", "COUNT", "COUNTA", "COUNTBLANK", "COUNTIF", "COUNTIFS", "SUMIF", "SUMIFS", "AVERAGEIF", "AVERAGEIFS",
            "PRODUCT", "MEDIAN", "MODE", "STDEV", "STDEV.P", "VAR", "VAR.P", "LARGE", "SMALL", "RANK", "ABS", "INT", "ROUND", "ROUNDUP", "ROUNDDOWN",
            "CEILING", "FLOOR", "MOD", "POWER", "SQRT", "EXP", "LN", "LOG", "LOG10", "PI", "SIGN", "RAND", "RANDBETWEEN", "SUMPRODUCT",
            "IF", "IFS", "IFERROR", "IFNA", "AND", "OR", "XOR", "NOT", "TRUE", "FALSE", "CHOOSE", "ISBLANK", "ISNUMBER", "ISTEXT", "ISERROR", "ISNA", "NA",
            "CONCAT", "CONCATENATE", "TEXTJOIN", "LEFT", "RIGHT", "MID", "LEN", "LOWER", "UPPER", "PROPER", "TRIM", "SUBSTITUTE", "REPLACE", "FIND",
            "SEARCH", "REPT", "EXACT", "VALUE", "TEXT", "VLOOKUP", "HLOOKUP", "XLOOKUP", "MATCH", "INDEX", "ROWS", "COLUMNS",
            "TODAY", "NOW", "DATE", "TIME", "YEAR", "MONTH", "DAY", "WEEKDAY", "HOUR", "MINUTE", "SECOND", "DAYS", "EDATE", "EOMONTH", "DATEDIF",
        )
    }
}

/** Excel number formats, the common ones. */
object NumFmt {
    fun format(v: Value, fmt: String?): String {
        if (fmt.isNullOrBlank() || fmt.equals("General", true) || fmt == "@") return v.display()
        val d = (v as? Value.Num)?.v ?: return v.display()
        val f = fmt.split(';').let { parts -> if (d < 0 && parts.size > 1) parts[1] else parts[0] }
        val lower = f.lowercase().replace("\\", "").replace("\"", "")
        if (Regex("[dmyhs]").containsMatchIn(lower.replace("[red]", "")) && !lower.contains("0")) return date(d, lower)
        val percent = f.contains('%')
        val x = kotlin.math.abs(if (percent) d * 100 else d)
        val decimals = Regex("\\.([0#]+)").find(f)?.groupValues?.get(1)?.length ?: 0
        val grouping = f.contains(",")
        val pattern = (if (grouping) "#,##0" else "0") + if (decimals > 0) "." + "0".repeat(decimals) else ""
        val body = java.text.DecimalFormat(pattern, java.text.DecimalFormatSymbols(java.util.Locale.US)).format(x)
        val prefix = Regex("^[^0#,.]*").find(f.replace("\"", ""))?.value?.replace("[Red]", "").orEmpty()
        val suffix = Regex("[^0#,.%]*$").find(f.replace("\"", "").removeSuffix("%"))?.value.orEmpty()
        val sign = if (d < 0 && !f.contains(';')) "-" else ""
        return sign + prefix + body + (if (percent) "%" else "") + if (!percent) suffix.trim() else ""
    }

    private fun date(serial: Double, f: String): String {
        val d = Evaluator.date(serial)
        val secs = ((serial % 1) * 86_400).roundToLong()
        val hasTime = f.contains("h") || f.contains("s")
        var out = f
        out = out.replace("yyyy", "%04d".format(d.year)).replace("yy", "%02d".format(d.year % 100))
        out = out.replace("mmmm", d.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()))
            .replace("mmm", d.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()))
        out = if (hasTime && Regex("h+:mm").containsMatchIn(out)) out.replace(Regex("(h+):mm")) { "${it.groupValues[1]}:%02d".format(secs / 60 % 60) } else out
        out = out.replace("mm", "%02d".format(d.monthValue)).replace(Regex("(?<![a-z])m(?![a-z])"), d.monthValue.toString())
        out = out.replace("dddd", d.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()))
            .replace("ddd", d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()))
            .replace("dd", "%02d".format(d.dayOfMonth)).replace(Regex("(?<![a-z])d(?![a-z])"), d.dayOfMonth.toString())
        out = out.replace("hh", "%02d".format(secs / 3600)).replace(Regex("(?<![a-z])h(?![a-z])"), (secs / 3600).toString())
        out = out.replace("ss", "%02d".format(secs % 60))
        return out
    }
}
