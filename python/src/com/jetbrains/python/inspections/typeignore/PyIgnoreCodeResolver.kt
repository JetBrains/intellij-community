// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.typeignore

import com.jetbrains.python.inspections.PyIgnoreCommentUtil
import com.jetbrains.python.inspections.PyTypeCheckerSuppressionCode

/**
 * Semantic resolution of the codes listed in a `# type: ignore` / `# pycharm: ignore` comment: which PyCharm
 * inspection (if any) a code names. Shared by [TypeIgnoreInspectionSuppressor], [PyTypeIgnoreWithoutCodeInspection],
 * [PyUnknownIgnoreCodeInspection] and [PyIgnoreCodeCompletionContributor].
 */
internal object PyIgnoreCodeResolver {
  private val GRANULAR_TYPE_CHECKER_CODES: Set<String> = PyTypeCheckerSuppressionCode.entries.mapTo(HashSet()) { it.id }

  /** What a single code of an ignore comment names. */
  sealed interface Resolution {
    /** A granular [PyTypeCheckerSuppressionCode] such as `unsupported-operator`. The type checker applies it. */
    data object Granular : Resolution

    /** A whole inspection, by its suppress id or its kebab-case alias. */
    data class Inspection(val suppressId: String) : Resolution

    /**
     * A PyCharm code that names nothing. It has the `pycharm:` namespace, or it comes from `# pycharm: ignore`.
     * A name that looks like a suppress id, such as `PyFoo`, is never unknown. It can belong to an inspection of
     * another edition or of a disabled plugin.
     */
    data object Unknown : Resolution

    /** A bare code of another tool, such as mypy's `attr-defined`. */
    data object Foreign : Resolution
  }

  private fun isGranular(name: String): Boolean = name in GRANULAR_TYPE_CHECKER_CODES

  fun resolve(ref: PyIgnoreCommentUtil.CodeRef): Resolution {
    if (isGranular(ref.name)) return Resolution.Granular
    val ids = PyTypeIgnoreSuppressIds.getInstance()
    val suppressId = ref.name.takeIf { ids.isKnownSuppressId(it) } ?: ids.resolveKebabAlias(ref.name)
    return when {
      suppressId != null -> Resolution.Inspection(suppressId)
      !ref.pycharmNamespaced -> Resolution.Foreign
      looksLikeSuppressId(ref.name) -> Resolution.Inspection(ref.name)
      else -> Resolution.Unknown
    }
  }

  private fun looksLikeSuppressId(name: String): Boolean = name.length > 2 && name.startsWith("Py") && name[2].isUpperCase()

  /**
   * `true` if [parsed] names at least one PyCharm code. An unknown PyCharm code counts, because
   * [PyUnknownIgnoreCodeInspection] reports it.
   */
  fun namesPyCharmCode(parsed: PyIgnoreCommentUtil.ParsedIgnore): Boolean =
    PyIgnoreCommentUtil.codeRefs(parsed).any { resolve(it) != Resolution.Foreign }
}
