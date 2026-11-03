// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.psiScip

import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.util.TreeMap

private const val SITES_PER_COUNTER = 50

/** The counters of the summary. A counter in [SITE_COUNTERS] also keeps its first sites. */
internal enum class Counter(@JvmField val key: String) {
  FILES("files"),
  DEFINITIONS("definitions"),
  REFS("refs"),
  RESOLVED("resolved"),
  CALLS("calls"),
  DOC_REFS("doc_refs"),
  INVALID_SINGLE("invalid_single"),
  AMBIGUOUS("ambiguous"),
  UNRESOLVED("unresolved"),
  PACKAGE_REFS("package_refs"),
  ARRAY_REFS("array_refs"),
  FOREIGN_LOCAL("foreign_local"),
  NO_SYMBOL("no_symbol"),
  EXCEPTIONS("exceptions"),
  MS("ms"),
}

private val SITE_COUNTERS = setOf(
  Counter.INVALID_SINGLE, Counter.AMBIGUOUS, Counter.UNRESOLVED, Counter.FOREIGN_LOCAL, Counter.NO_SYMBOL, Counter.EXCEPTIONS,
)

/** The counters of one file or of a sum of files, with the first sites of each failure counter. */
internal class ExportStats {
  private val counts = LongArray(Counter.entries.size)
  private val sites = HashMap<Counter, MutableList<String>>()

  fun add(counter: Counter, delta: Long = 1) {
    counts[counter.ordinal] += delta
  }

  fun site(counter: Counter, site: String) {
    add(counter)
    val list = sites.getOrPut(counter) { ArrayList() }
    if (list.size < SITES_PER_COUNTER) {
      list.add(site)
    }
  }

  fun get(counter: Counter): Long = counts[counter.ordinal]

  /** Adds [other] to this sum. The caller adds files in path order, so the kept sites are deterministic. */
  fun merge(other: ExportStats) {
    for (i in counts.indices) {
      counts[i] += other.counts[i]
    }
    for ((counter, otherSites) in other.sites) {
      val list = sites.getOrPut(counter) { ArrayList() }
      for (site in otherSites) {
        if (list.size >= SITES_PER_COUNTER) break
        list.add(site)
      }
    }
  }

  fun writeJson(out: Writer, indent: String) {
    out.append("{")
    var first = true
    for (counter in Counter.entries) {
      if (!first) out.append(",")
      first = false
      out.append("\n").append(indent).append("  \"").append(counter.key).append("\": ").append(counts[counter.ordinal].toString())
    }
    for (counter in Counter.entries) {
      val list = sites[counter] ?: continue
      if (counter !in SITE_COUNTERS) continue
      out.append(",\n").append(indent).append("  \"").append(counter.key).append("_sites\": [")
      list.forEachIndexed { index, site ->
        if (index > 0) out.append(", ")
        out.append(jsonString(site))
      }
      out.append("]")
    }
    out.append("\n").append(indent).append("}")
  }
}

/**
 * The summary of an export: totals, per module counters, the file categories that the exporter does not walk,
 * and every duplicate symbol.
 */
internal class ExportSummary {
  val total: ExportStats = ExportStats()
  private val modules = TreeMap<String, ExportStats>()
  val skippedFiles: TreeMap<String, Int> = TreeMap()
  private val duplicateSymbols = TreeMap<String, List<String>>()

  fun add(module: String, stats: ExportStats) {
    total.merge(stats)
    modules.getOrPut(module) { ExportStats() }.merge(stats)
  }

  /**
   * Records every global symbol with more than one definition in [documents], with the `path:line` of each definition.
   * Symbols carry no module, so same-named classes of two modules, such as two copies of one class, share a symbol.
   * The oracle drops the definitions of such a symbol, so the summary names all of them.
   * Returns the number of duplicate symbols.
   */
  fun findDuplicateSymbols(documents: List<ScipDocument>): Int {
    val definitions = HashMap<String, MutableList<String>>()
    for (document in documents) {
      for (occurrence in document.occurrences) {
        if (occurrence.roles and ROLE_DEFINITION != 0 && occurrence.symbol.startsWith(SYMBOL_PREFIX)) {
          definitions.getOrPut(occurrence.symbol) { ArrayList(1) }.add("${document.relativePath}:${occurrence.range.startLine + 1}")
        }
      }
    }
    for ((symbol, sites) in definitions) {
      if (sites.size > 1) {
        duplicateSymbols[symbol] = sites
      }
    }
    return duplicateSymbols.size
  }

  fun write(file: Path, extra: Map<String, String>) {
    Files.newBufferedWriter(file).use { out ->
      out.append("{\n")
      for ((key, value) in extra) {
        out.append("  \"").append(key).append("\": ").append(value).append(",\n")
      }
      out.append("  \"skipped_files\": {")
      skippedFiles.entries.forEachIndexed { index, (key, value) ->
        if (index > 0) out.append(", ")
        out.append('"').append(key).append("\": ").append(value.toString())
      }
      out.append("},\n  \"duplicate_symbols\": ").append(duplicateSymbols.size.toString())
      out.append(",\n  \"duplicate_symbol_sites\": {")
      duplicateSymbols.entries.forEachIndexed { index, (symbol, sites) ->
        out.append(if (index > 0) ",\n    " else "\n    ").append(jsonString(symbol)).append(": [")
        sites.joinTo(out, ", ") { jsonString(it) }
        out.append("]")
      }
      out.append(if (duplicateSymbols.isEmpty()) "}" else "\n  }")
      out.append(",\n  \"total\": ")
      total.writeJson(out, "  ")
      out.append(",\n  \"modules\": {")
      modules.entries.forEachIndexed { index, (module, stats) ->
        out.append(if (index > 0) ",\n    " else "\n    ").append('"').append(module).append("\": ")
        stats.writeJson(out, "    ")
      }
      out.append("\n  }\n}\n")
    }
  }
}

private fun jsonString(value: String): String = '"' + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + '"'
