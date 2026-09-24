// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.psi.PsiComment
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import java.util.regex.Pattern

/**
 * Parsing for the two inline suppression directives PyCharm understands:
 *
 *  - `# type: ignore[<code>, ...]` — the PEP 484 comment; a bare code may name a foreign checker's code
 *    (e.g. mypy's `attr-defined`);
 *  - `# pycharm: ignore[<code>, ...]` — PyCharm's own directive (PY-90627); every bare code is implicitly a
 *    PyCharm code.
 *
 * A `<code>` may carry an explicit `pycharm:` namespace prefix. This object stays purely syntactic: it does
 * not know which codes name real inspections. The callers in [com.jetbrains.python.inspections.typeignore]
 * resolve the codes.
 */
object PyIgnoreCommentUtil {
  private const val PYCHARM_NAMESPACE = "pycharm"

  /** `# pycharm: ignore[...]`, mirroring [PyTypingTypeProvider.TYPE_IGNORE_PATTERN]; group(1) = the bracketed codes incl. brackets. */
  @JvmField
  val PYCHARM_IGNORE_PATTERN: Pattern = Pattern.compile("#\\s*pycharm:\\s*ignore\\s*(\\[[^]#]*])?($|(\\s.*))", Pattern.CASE_INSENSITIVE)

  enum class Directive { TYPE, PYCHARM }

  /** A parsed ignore comment. [rawCodes] is empty for a bare comment or empty brackets. */
  data class ParsedIgnore(val directive: Directive, val rawCodes: Set<String>)

  /**
   * A single bracket code after namespace canonicalization. [pycharmNamespaced] is `true` when the code
   * carried an explicit `pycharm:` prefix or came from a `# pycharm: ignore` directive.
   */
  data class CodeRef(val name: String, val pycharmNamespaced: Boolean)

  /** Parses [comment] as one of the two directives, or returns `null` if it is neither. */
  fun parse(comment: PsiComment): ParsedIgnore? {
    val text = comment.text ?: return null
    parseWith(text, PyTypingTypeProvider.TYPE_IGNORE_PATTERN, Directive.TYPE)?.let { return it }
    return parseWith(text, PYCHARM_IGNORE_PATTERN, Directive.PYCHARM)
  }

  private fun parseWith(text: String, pattern: Pattern, directive: Directive): ParsedIgnore? {
    val matcher = pattern.matcher(text)
    if (!matcher.matches()) return null
    val bracketGroup = matcher.group(1) ?: return ParsedIgnore(directive, emptySet())
    val codes = LinkedHashSet<String>()  // preserve source order for deterministic merges
    for (part in bracketGroup.substring(1, bracketGroup.length - 1).split(',')) {
      val code = part.trim()
      if (code.isNotEmpty()) codes.add(code)
    }
    return ParsedIgnore(directive, codes)
  }

  /**
   * Canonicalizes a single [rawCode] of a [directive]. Strips a `pycharm:` prefix; returns `null` when the
   * code belongs to a different, foreign namespace (e.g. `mypy:foo`).
   */
  fun codeRef(directive: Directive, rawCode: String): CodeRef? {
    val colon = rawCode.indexOf(':')
    if (colon < 0) return CodeRef(rawCode, pycharmNamespaced = directive == Directive.PYCHARM)
    if (!rawCode.substring(0, colon).trim().equals(PYCHARM_NAMESPACE, ignoreCase = true)) return null
    val name = rawCode.substring(colon + 1).trim()
    return if (name.isEmpty()) null else CodeRef(name, pycharmNamespaced = true)
  }

  /** All canonical [CodeRef]s listed by [parsed], dropping foreign-namespaced codes. */
  fun codeRefs(parsed: ParsedIgnore): List<CodeRef> = parsed.rawCodes.mapNotNull { codeRef(parsed.directive, it) }
}
