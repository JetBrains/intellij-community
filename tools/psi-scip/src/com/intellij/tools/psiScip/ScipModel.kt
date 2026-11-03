// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.psiScip

/** The `Definition` bit of the SCIP `SymbolRole` enum. */
internal const val ROLE_DEFINITION: Int = 0x1

/** The `Import` bit of the SCIP `SymbolRole` enum. */
internal const val ROLE_IMPORT: Int = 0x2

/** The `WriteAccess` bit of the SCIP `SymbolRole` enum. */
internal const val ROLE_WRITE: Int = 0x4

/** The `ReadAccess` bit of the SCIP `SymbolRole` enum. */
internal const val ROLE_READ: Int = 0x8

/**
 * A private bit of `symbol_roles`: the occurrence is the callee name of a call.
 * SCIP has no field for a call, so the exporter sets the bit itself.
 * A reader that does not know the bit ignores it.
 */
internal const val ROLE_CALL: Int = 1 shl 20

/** A private bit of `symbol_roles`: the occurrence is inside a doc comment. It never carries [ROLE_CALL]. */
internal const val ROLE_DOC: Int = 1 shl 21

/** A private bit of `symbol_roles`: the range of the occurrence is not the name of the symbol. */
internal const val ROLE_NON_NAME: Int = 1 shl 22

/** The values of the SCIP `SymbolInformation.Kind` enum that the exporter writes, with their protobuf numbers. */
internal enum class ScipKind(val number: Int) {
  Class(7),
  Constructor(9),
  Enum(11),
  EnumMember(12),
  Field(15),
  Interface(21),
  Method(26),
}

/**
 * A SCIP range: `[startLine, startColumn, endColumn]` on one line, else `[startLine, startColumn, endLine, endColumn]`.
 * Lines are 0-based, and columns count UTF-16 code units from the line start.
 */
internal class ScipRange(@JvmField val startLine: Int, @JvmField val startColumn: Int, @JvmField val endLine: Int, @JvmField val endColumn: Int) :
  Comparable<ScipRange> {
  fun toArray(): IntArray {
    return if (startLine == endLine) intArrayOf(startLine, startColumn, endColumn) else intArrayOf(startLine, startColumn, endLine, endColumn)
  }

  override fun compareTo(other: ScipRange): Int {
    return compareValuesBy(this, other, { it.startLine }, { it.startColumn }, { it.endLine }, { it.endColumn })
  }
}

internal class ScipOccurrence(
  @JvmField val range: ScipRange,
  @JvmField val symbol: String,
  @JvmField val roles: Int,
  @JvmField val enclosingRange: ScipRange?,
)

internal class ScipRelationship(@JvmField val symbol: String, @JvmField val isReference: Boolean, @JvmField val isImplementation: Boolean)

internal class ScipSymbolInformation(
  @JvmField val symbol: String,
  @JvmField val kind: ScipKind,
  @JvmField val displayName: String,
  @JvmField val relationships: List<ScipRelationship>,
)

/** One source file of the index. [occurrences] and [symbols] are in the order the writer emits. */
internal class ScipDocument(
  @JvmField val relativePath: String,
  @JvmField val language: String,
  @JvmField val occurrences: List<ScipOccurrence>,
  @JvmField val symbols: List<ScipSymbolInformation>,
)

internal val OCCURRENCE_ORDER: Comparator<ScipOccurrence> =
  compareBy<ScipOccurrence> { it.range }.thenBy { it.symbol }.thenBy { it.roles }

internal val SYMBOL_ORDER: Comparator<ScipSymbolInformation> = compareBy { it.symbol }
