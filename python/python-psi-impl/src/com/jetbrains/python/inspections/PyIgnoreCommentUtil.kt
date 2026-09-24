// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.impl.PyPsiUtils
import java.util.regex.Pattern

/**
 * Parsing and matching for the two inline suppression directives PyCharm understands:
 *
 *  - `# type: ignore[<code>, ...]` — the PEP 484 comment; a bare code may name a foreign checker's code
 *    (e.g. mypy's `attr-defined`);
 *  - `# pycharm: ignore[<code>, ...]` — PyCharm's own directive (PY-90627); every bare code is implicitly a
 *    PyCharm code.
 *
 * A `<code>` may carry an explicit `pycharm:` namespace prefix. This object stays purely syntactic: it does
 * not know which codes name real inspections. Semantic resolution (known suppress ids, kebab aliases,
 * granular type-checker codes) lives with the callers — [com.jetbrains.python.inspections.typeignore]
 * on the impl side for whole-inspection suppression, and [PyTypeCheckerProblemReporter] for granular codes.
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

  /** A raw code of an ignore comment and its range in the comment text. */
  data class CodeOccurrence(val rawCode: String, val range: TextRange)

  /**
   * The codes of an ignore comment in source order. [bracketContent] is the range between the brackets in the
   * comment text. It is `null` when the comment has no brackets.
   */
  data class IgnoreCodes(val directive: Directive, val occurrences: List<CodeOccurrence>, val bracketContent: TextRange?)

  /** Parses [comment] as one of the two directives, or returns `null` if it is neither. */
  fun parse(comment: PsiComment): ParsedIgnore? {
    val codes = parseCodes(comment) ?: return null
    return ParsedIgnore(codes.directive, codes.occurrences.mapTo(LinkedHashSet()) { it.rawCode })
  }

  /** Parses [comment] like [parse], and keeps the range of each code. */
  fun parseCodes(comment: PsiComment): IgnoreCodes? {
    val text = comment.text ?: return null
    return parseCodesWith(text, PyTypingTypeProvider.TYPE_IGNORE_PATTERN, Directive.TYPE)
           ?: parseCodesWith(text, PYCHARM_IGNORE_PATTERN, Directive.PYCHARM)
  }

  private fun parseCodesWith(text: String, pattern: Pattern, directive: Directive): IgnoreCodes? {
    val matcher = pattern.matcher(text)
    if (!matcher.matches()) return null
    if (matcher.group(1) == null) return IgnoreCodes(directive, emptyList(), null)
    val bracketContent = TextRange(matcher.start(1) + 1, matcher.end(1) - 1)
    val occurrences = ArrayList<CodeOccurrence>()
    var partStart = bracketContent.startOffset
    while (partStart <= bracketContent.endOffset) {
      val comma = text.indexOf(',', partStart)
      val partEnd = if (comma < 0 || comma > bracketContent.endOffset) bracketContent.endOffset else comma
      val part = text.substring(partStart, partEnd)
      val code = part.trim()
      if (code.isNotEmpty()) {
        val codeStart = partStart + part.indexOf(code)
        occurrences.add(CodeOccurrence(code, TextRange(codeStart, codeStart + code.length)))
      }
      partStart = partEnd + 1
    }
    return IgnoreCodes(directive, occurrences, bracketContent)
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

  /**
   * `true` when a same-line trailing ignore comment for [element], or a leading file-level ignore comment,
   * explicitly lists [codeName] (in any non-foreign namespace). Used by [PyTypeCheckerProblemReporter] so a
   * granular type-checker code can be silenced with `# type: ignore[<code>]` / `# pycharm: ignore[<code>]`
   * exactly like `# noinspection <code>`.
   */
  fun isExplicitlyIgnored(element: PsiElement, codeName: String): Boolean {
    val sameLine = PyPsiUtils.findSameLineComment(element)
    if (sameLine != null && namesCode(sameLine, codeName)) return true
    val file = element.containingFile ?: return false
    var node = file.firstChild
    while (node is PsiComment || node is PsiWhiteSpace) {
      if (node is PsiComment && namesCode(node, codeName)) return true
      node = node.nextSibling
    }
    return false
  }

  private fun namesCode(comment: PsiComment, codeName: String): Boolean {
    val parsed = parse(comment) ?: return false
    return codeRefs(parsed).any { it.name == codeName }
  }
}
