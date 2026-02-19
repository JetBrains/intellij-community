package com.intellij.python.hatch.cli

/**
 * Simple table without joined cells.
 * Each row has the same number of cells as the header.
 */
internal data class AsciiTable(val headers: List<String>, val rows: List<List<String>>) {
  fun findColumnIdx(caption: String): Int? = headers.indexOf(caption).takeIf { it >= 0 }
  fun List<String>.cell(columnIdx: Int?): String? = columnIdx?.let { this[it] }
}


/**
 * Parses Tables like this:
 *
 * +------+------+
 * |  H1  |  H2  |
 * +======+======+
 * | R1_1 | R1_2 |
 * +------+------+
 * | R2_1 | R2_2 |
 * +------+------+
 * ...
 * +------+------+
 */
internal fun String.parseAsciiTable(): AsciiTable? {
  val lines = this.trim().lines()
  val columns = lines.first().count { it == '+' } - 1
  if (columns <= 0) return null

  val data = buildList {
    val row = Array(columns) { "" }
    for (line in lines.drop(1)) {
      if (line.startsWith('+')) {
        add(row.map { it.trim() })
        row.fill("")
        continue
      }

      val cells = line.splitToSequence('|').map { it.trim() }.toList()
      for (col in 0..<columns) {
        row[col] += "\n${cells[col + 1]}"
      }
    }
  }
  return AsciiTable(headers = data.first(), rows = data.drop(1))
}