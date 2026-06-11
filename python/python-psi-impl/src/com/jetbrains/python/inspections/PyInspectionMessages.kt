// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.AbstractBundle
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.xml.util.XmlStringUtil
import com.jetbrains.python.documentation.PyDocumentationLink
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.inspections.PyInspectionMessages.bundleMessage
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import java.text.MessageFormat

/**
 * Helpers for building PY inspection messages whose bundle templates mark code-like spans with backticks.
 *
 * A bundle template such as
 * ```
 * INSP.foo.bad.type=Expected type `{0}`, got `{1}` instead
 * INSP.bar.references=References must always resolve to `int`
 * ```
 * yields two formatted strings:
 *  - a plain-text description used in the Problems view (e.g. `Expected type 'int', got 'str' instead`),
 *  - an HTML tooltip used for the editor hover (e.g. `Expected type <code>int</code>, got <code>str</code> instead`).
 *
 * Only backticks present in the template are translated. Backticks that appear in the params values are
 * substituted verbatim and never converted, so type names that happen to contain backticks survive into
 * both the description and the tooltip untouched.
 *
 * Write a doubled `` `` `` in the template to emit a single literal backtick (mirroring MessageFormat's
 * `''`-means-`'` rule); it is not treated as a code-span delimiter.
 *
 * Templates without any backticks fall back to a plain formatted message identical to
 * [AbstractBundle.getMessage]; the tooltip is the same plain text as the description.
 */
object PyInspectionMessages {

  /** A localized inspection message paired with its editor-hover tooltip. */
  @JvmRecord
  data class ProblemMessage(
    @InspectionMessage @Nls val description: String,
    @NlsContexts.Tooltip val tooltip: String,
  )

  /**
   * A parameter value that carries its own description form and tooltip form. Use this when the value is
   * a joined or pre-formatted span of code-like names — the bundle template can only convert its own
   * backticks, not characters inside dynamic inputs, so the caller supplies both forms here and the
   * helper substitutes the correct one into each template.
   *
   * [tooltip] is expected to be HTML; it is inserted into the tooltip template as-is (no further
   * escaping). [description] is plain text used in the Problems view.
   */
  @JvmRecord
  data class CodifiedParam(val description: String, @NlsContexts.Tooltip val tooltip: String) {
    companion object {
      /**
       * Joins [names] as `'a', 'b', 'c'` for the description and
       * `<code>a</code>, <code>b</code>, <code>c</code>` for the tooltip, separated by `", "`.
       */
      @JvmStatic
      fun joinNames(names: Iterable<String>): CodifiedParam = CodifiedParam(
        description = names.joinToString(", ") { "'$it'" },
        tooltip = names.joinToString(", ") { "<code>${XmlStringUtil.escapeString(it)}</code>" },
      )

      /**
       * Renders [type] as a code-like span: the description is the plain type name shown in the Problems view
       * (identical to [PythonDocumentationProvider.getTypeName]), and the tooltip is the same type rendered as
       * HTML with highlighted, clickable class links, exactly as Quick Documentation shows it.
       *
       * Set [verbose] to mirror [PythonDocumentationProvider.getVerboseTypeName] (e.g. inline TypeVar bounds);
       * use it for the *expected* side of a type-mismatch message.
       *
       * [anchor] is the element the type belongs to; it is used to resolve class names to their declarations.
       */
      @JvmStatic
      @JvmOverloads
      fun ofType(
        type: PyType?,
        anchor: PsiElement,
        context: TypeEvalContext,
        verbose: Boolean = false,
      ): CodifiedParam = CodifiedParam(
        description = if (verbose) PythonDocumentationProvider.getVerboseTypeName(type, context)
        else PythonDocumentationProvider.getTypeName(type, context),
        tooltip = if (verbose) PythonDocumentationProvider.getVerboseTypeNameWithLinks(type, context, anchor)
        else PythonDocumentationProvider.getTypeNameWithLinks(type, context, anchor),
      )

      /**
       * Renders a named symbol ([target]) as a code-like span: the description is [text] (plain, shown in the
       * Problems view), and the tooltip is [text] wrapped in a navigable `#element/<fqn>` link pointing at
       * [target] — so e.g. a class or method name in an inspection message becomes clickable on hover.
       *
       * [text] defaults to the symbol's own name; pass it explicitly when the message shows a different form,
       * such as a qualified `B.f()` signature.
       */
      @JvmStatic
      @JvmOverloads
      fun ofReference(
        target: PyQualifiedNameOwner,
        text: @NlsSafe String = target.name.orEmpty(),
      ): CodifiedParam = CodifiedParam(
        description = text,
        tooltip = PyDocumentationLink.toElementTooltipLink(target, text).toString(),
      )
    }
  }

  /**
   * Looks up the raw template from [bundle] for [key] and formats it twice — once for the Problems-view
   * description (backticks become single quotes) and once for the editor hover tooltip (backticks become
   * `<code>` blocks).
   */
  @JvmStatic
  fun bundleMessage(bundle: AbstractBundle, key: String, vararg params: Any?): ProblemMessage {
    val rawTemplate = bundle.getResourceBundle().getString(key)
    return formatTemplate(rawTemplate, *params)
  }

  /**
   * Same as [bundleMessage] but takes the already-resolved raw template. Visible for unit testing the
   * transformation without touching the bundle layer.
   */
  @VisibleForTesting
  fun formatTemplate(rawTemplate: String, vararg params: Any?): ProblemMessage {
    val description = MessageFormat.format(rawTemplate.toDescriptionTemplate(), *params.forDescription())
    if ('`' !in rawTemplate && params.none { it is CodifiedParam }) {
      // No code-rendered spans anywhere: description and tooltip are the same plain text.
      return ProblemMessage(description, description)
    }
    val tooltip = "<html>" + MessageFormat.format(rawTemplate.toTooltipTemplate(), *params.forTooltip()) + "</html>"
    return ProblemMessage(description, tooltip)
  }

  /**
   * Walks the receiver, copying ordinary characters verbatim. A doubled `` `` `` is an escape that emits a
   * single literal backtick; every remaining single `` ` `` is a code-span delimiter that invokes
   * [onDelimiter] (its flag is `true` when the span is opening). Returns the rendered text paired with
   * whether the delimiters were balanced — an odd number signals a malformed template.
   */
  private inline fun String.renderBackticks(onDelimiter: StringBuilder.(opening: Boolean) -> Unit): Pair<String, Boolean> {
    val sb = StringBuilder(length + 16)
    var inCode = false
    var i = 0
    while (i < length) {
      val c = this[i]
      when {
        c == '`' && i + 1 < length && this[i + 1] == '`' -> {
          sb.append('`') // `` -> a single literal backtick
          i += 2
        }
        c == '`' -> {
          sb.onDelimiter(!inCode)
          inCode = !inCode
          i++
        }
        else -> {
          sb.append(c)
          i++
        }
      }
    }
    return sb.toString() to !inCode
  }

  /**
   * Renders every code-span delimiter as `''` so MessageFormat emits a literal single quote there, and
   * every doubled `` `` `` as a literal backtick. Apostrophes already present in the template (typically
   * doubled as `''` for possessives like `can''t`) are preserved. Delimiter balance is irrelevant here:
   * an unbalanced delimiter just yields an unbalanced quote, exactly as MessageFormat would otherwise.
   */
  private fun String.toDescriptionTemplate(): String = renderBackticks { append("''") }.first

  /**
   * Renders code-span delimiters as alternating `<code>` / `</code>` and every doubled `` `` `` as a literal
   * backtick. An odd number of delimiters signals a malformed template; fall back to a single-quote
   * rendering rather than emit an unclosed `<code>` into the tooltip.
   */
  private fun String.toTooltipTemplate(): String {
    val (rendered, balanced) = renderBackticks { opening -> append(if (opening) "<code>" else "</code>") }
    return if (balanced) rendered else renderBackticks { append("'") }.first
  }

  /**
   * Converts an already-formatted message that marks code-like spans with backticks into an HTML fragment
   * (no `<html>` wrapper): ordinary text is XML-escaped and each `` `…` `` pair becomes `<code>…</code>`;
   * a doubled `` `` `` is a literal backtick. Use for strings that are produced *after* parameter
   * substitution — e.g. a type-mismatch breakdown node built with [AbstractBundle.getMessage] from a
   * backtick template — so it cannot distinguish template backticks from value backticks (callers pass
   * names that do not contain backticks). An unbalanced delimiter is tolerated by closing the span.
   */
  @JvmStatic
  fun codeSpansToHtmlFragment(message: @InspectionMessage String): @NlsContexts.Tooltip String {
    val sb = StringBuilder(message.length + 16)
    val text = StringBuilder()
    fun flushText() {
      if (text.isNotEmpty()) {
        sb.append(XmlStringUtil.escapeString(text.toString()))
        text.setLength(0)
      }
    }
    var inCode = false
    var i = 0
    while (i < message.length) {
      val c = message[i]
      when {
        c == '`' && i + 1 < message.length && message[i + 1] == '`' -> {
          text.append('`') // `` -> a single literal backtick
          i += 2
        }
        c == '`' -> {
          flushText()
          sb.append(if (inCode) "</code>" else "<code>")
          inCode = !inCode
          i++
        }
        else -> {
          text.append(c)
          i++
        }
      }
    }
    flushText()
    if (inCode) sb.append("</code>")
    return sb.toString()
  }

  /**
   * The tooltip form of [message] as an HTML fragment for embedding inside a larger tooltip (e.g. as the
   * headline above a type-mismatch breakdown). [formatTemplate] wraps code-bearing tooltips in `<html>…</html>`;
   * this strips that wrapper. A plain message (no code spans) is XML-escaped so it is safe to embed verbatim.
   */
  @JvmStatic
  fun tooltipFragment(message: ProblemMessage): @NlsContexts.Tooltip String =
    if (message.tooltip.startsWith("<html>")) message.tooltip.removeSurrounding("<html>", "</html>")
    else XmlStringUtil.escapeString(message.description)

  private fun Array<out Any?>.forDescription() = map {
    (it as? CodifiedParam)?.description ?: it
  }.toTypedArray()

  private fun Array<out Any?>.forTooltip() = map {
    when (it) {
      is CodifiedParam -> it.tooltip
      is String -> XmlStringUtil.escapeString(it)
      else -> it
    }
  }.toTypedArray()
}
