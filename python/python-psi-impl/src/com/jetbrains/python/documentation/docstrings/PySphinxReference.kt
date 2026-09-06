// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation.docstrings

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.PsiReferenceEx
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.impl.ResolveResultList
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.TypeEvalContext
import java.util.regex.Pattern

/**
 * A reference to a Python object referenced from a Sphinx Python-domain cross-reference role
 * (e.g. `` :py:class:`socket.socket` `` or `` :meth:`~queue.Queue.get` ``) or a Python-domain directive
 * signature (e.g. `.. py:class:: Foo`).
 *
 * Found inside a docstring or a line comment. One reference is created per dotted component of the target so that
 * renaming any symbol in the path (module, class or member) updates the markup, mirroring [DocStringTypeReference].
 *
 * @see <a href="https://www.sphinx-doc.org/en/master/usage/domains/python.html#cross-referencing-python-objects">Cross-referencing Python objects</a>
 * @see <a href="https://www.sphinx-doc.org/en/master/usage/domains/python.html#python-signatures">Python signatures</a>
 * @see SphinxReferences
 */
internal class PySphinxReference(
  element: PsiElement,
  rangeInElement: TextRange,
  /** The range of the target from its first component through this component. A move rewrites it. */
  private val prefixRange: TextRange,
  private val qualifiedName: QualifiedName,
  private val soft: Boolean,
) : PsiPolyVariantReferenceBase<PsiElement>(element, rangeInElement), PsiReferenceEx {

  /**
   * The reference to the first component of a dotted target, or `null` when the target has one component.
   * A dotted target whose head does not resolve comes from another project, so it must not produce a warning.
   */
  internal var head: PySphinxReference? = null

  override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
    ResolveCache.getInstance(element.project).resolveWithCaching(this, Resolver, true, incompleteCode)

  private fun resolveInner(): Array<ResolveResult> {
    val host = element
    val file = host.containingFile ?: return ResolveResult.EMPTY_ARRAY
    val context = TypeEvalContext.codeAnalysis(host.project, file)

    val resolved = LinkedHashSet<PsiElement>()
    val scopeOwner = ScopeUtil.getScopeOwner(host)
    if (scopeOwner != null) {
      resolved.addAll(PyResolveUtil.resolveQualifiedNameInScope(qualifiedName, scopeOwner, context))
    }
    if (resolved.isEmpty()) {
      PyResolveUtil.resolveFullyQualifiedName(qualifiedName, host, context)?.let { resolved.add(it) }
    }

    if (resolved.isEmpty()) return ResolveResult.EMPTY_ARRAY
    val results = ResolveResultList()
    for (target in resolved) {
      results.poke(target, RatedResolveResult.RATE_NORMAL)
    }
    return results.toArray(ResolveResult.EMPTY_ARRAY)
  }

  override fun isSoft(): Boolean = soft

  override fun handleElementRename(newElementName: String): PsiElement {
    // A module rename carries a ".py" or a ".pyi" suffix that must not appear in the qualified name.
    val name = newElementName.removeSuffix(PyNames.DOT_PY).removeSuffix(PyNames.DOT_PYI)
    return super.handleElementRename(name)
  }

  /**
   * Rewrites the target path when the referenced object moves to another module. Only the components up to this one
   * change, so a move of the module in `` :py:class:`pkg.mod.Foo` `` keeps the `Foo` tail.
   */
  override fun bindToElement(element: PsiElement): PsiElement? {
    if (element == resolve()) return getElement()
    val newName = canonicalName(element) ?: return null
    return ElementManipulators.handleContentChange(getElement(), prefixRange, newName.toString())
  }

  /**
   * Returns the path through which [target] is imported. A class that `pkg/_impl.py` defines and `pkg/__init__.py`
   * re-exports gets `pkg.Foo`, not `pkg._impl.Foo`. A class member keeps its path inside the top-level declaration.
   */
  private fun canonicalName(target: PsiElement): QualifiedName? {
    if (target is PyFile) return QualifiedNameFinder.findCanonicalImportPath(target, element)
    if (target !is PyQualifiedNameOwner) return null
    val path = ArrayDeque<String>()
    var declaration: PyQualifiedNameOwner = target
    while (true) {
      val declarationName = declaration.name ?: return null
      path.addFirst(declarationName)
      val owner = ScopeUtil.getScopeOwner(declaration)
      if (owner is PyFile) break
      // A local of a function has no import path.
      if (owner !is PyClass) return null
      declaration = owner
    }
    val modulePath = QualifiedNameFinder.findCanonicalImportPath(declaration, element) ?: return null
    return modulePath.append(QualifiedName.fromComponents(path))
  }

  override fun getUnresolvedHighlightSeverity(context: TypeEvalContext?): HighlightSeverity? {
    if (soft) return null
    // Sphinx resolves a dotted target through its own inventory, which can hold objects this project does not see.
    // Warn only when the head of the path resolves, as PyCharm does for a qualified expression.
    val head = this.head ?: return HighlightSeverity.WARNING
    return if (head.multiResolve(false).isEmpty()) null else HighlightSeverity.WARNING
  }

  // The bundle template marks the name with backticks. `problemMessage` turns them into the quotes that the
  // Problems view shows, which `PyPsiBundle.message` would leave in place.
  override fun getUnresolvedDescription(): String =
    PyPsiBundle.problemMessage("INSP.unresolved.refs.unresolved.reference", value).description

  override fun getVariants(): Array<Any> = emptyArray()

  private object Resolver : ResolveCache.PolyVariantResolver<PySphinxReference> {
    override fun resolve(ref: PySphinxReference, incompleteCode: Boolean): Array<ResolveResult> = ref.resolveInner()
  }
}

/**
 * Recognizes Sphinx markup inside docstrings and line comments. Turns a Python-domain target into a
 * [PySphinxReference], and reports the markup ranges that natural-language analysis must skip.
 * See the [Python domain documentation](https://www.sphinx-doc.org/en/master/usage/domains/python.html).
 */
object SphinxReferences {
  /** Inline cross-referencing roles of the Python domain, with or without the explicit `py:` domain prefix. */
  private const val ROLE_NAMES = "mod|func|class|meth|attr|data|const|exc|obj|type|deco"

  /** Object-describing directives of the Python domain. */
  private const val DIRECTIVE_NAMES =
    "module|currentmodule|function|decorator|decoratormethod|class|exception|attribute|data|method|staticmethod|classmethod|property|type"

  private val ROLE_PATTERN = Regex(":(?:py:)?(?:$ROLE_NAMES):`([^`\\n]+)`")

  private val DIRECTIVE_PATTERN = Regex("(?m)^[ \\t]*\\.\\.[ \\t]+(?:py:)?(?:$DIRECTIVE_NAMES)::[ \\t]+(\\S+)")

  /**
   * Any reST inline role, not just a Python-domain one. It also covers `` :ref:`label` ``, `` :doc:`/index` `` and
   * `` :envvar:`PATH` ``. Used only for natural-language exclusion, because the target of such a role is never
   * prose. References and tag highlighting stay narrow ([ROLE_PATTERN]) so only a Python object becomes a reference.
   */
  private const val ANY_ROLE = ":[\\w.+-]+(?::[\\w.+-]+)*:`[^`\\n]+`"

  /** A whole Python-domain directive line, marker and signature together. */
  private const val PY_DIRECTIVE_LINE = "^[ \\t]*\\.\\.[ \\t]+(?:py:)?(?:$DIRECTIVE_NAMES)::[ \\t]+\\S+"

  private const val DOCSTRING_MARKUP = "$ANY_ROLE|$PY_DIRECTIVE_LINE"

  /**
   * Matches the Sphinx markup of a docstring. Used to exclude the markup from natural-language analysis where the
   * coordinates are relative to the extracted text rather than to the PSI element.
   */
  @JvmField
  val DOCSTRING_MARKUP_PATTERN: Pattern = Pattern.compile(DOCSTRING_MARKUP, Pattern.MULTILINE)

  /**
   * Matches the Sphinx markup of a line comment, which is a role only. A directive pattern anchors on the start of a
   * line, and the text of a comment starts with the `#` marker, so a directive can never match there. Sphinx does
   * not read a `#` comment either, so a directive in one is not markup.
   */
  @JvmField
  val COMMENT_MARKUP_PATTERN: Pattern = Pattern.compile(ANY_ROLE)

  private val DOCSTRING_MARKUP_REGEX = DOCSTRING_MARKUP_PATTERN.toRegex()

  private val COMMENT_MARKUP_REGEX = COMMENT_MARKUP_PATTERN.toRegex()

  /** Matches just the role marker (e.g. `:py:class:`) that precedes the backtick-quoted target. */
  private val ROLE_TAG_PATTERN = Regex(":(?:py:)?(?:$ROLE_NAMES):(?=`)")

  /** Matches just the directive marker (e.g. `.. py:class::`), without the leading indentation. */
  private val DIRECTIVE_TAG_PATTERN = Regex("(?m)^[ \\t]*(\\.\\.[ \\t]+(?:py:)?(?:$DIRECTIVE_NAMES)::)")

  /**
   * Returns the element-relative ranges of the Sphinx role and directive *markers* (e.g. `:py:class:`,
   * `.. py:class::`), without the referenced target, so they can be highlighted like a docstring field tag
   * (`:param:`, `:return:`).
   */
  fun findTagRanges(host: PyStringLiteralExpression): List<TextRange> =
    if (isInInjectedFragment(host)) emptyList()
    else mapValueRanges(host) { text, offset -> tagRangesIn(text, offset) }

  /** Returns the comment-relative ranges of the Sphinx role markers. A comment holds no directive. */
  fun findTagRanges(comment: PsiComment): List<TextRange> =
    if (isInInjectedFragment(comment)) emptyList()
    else ROLE_TAG_PATTERN.findAll(comment.text).map { it.toTextRange(0) }.toList()

  private fun tagRangesIn(text: String, offset: Int): List<TextRange> {
    val result = ArrayList<TextRange>()
    for (m in ROLE_TAG_PATTERN.findAll(text)) {
      result.add(m.toTextRange(offset))
    }
    for (m in DIRECTIVE_TAG_PATTERN.findAll(text)) {
      val group = m.groups[1] ?: continue
      result.add(TextRange(group.range.first + offset, group.range.last + 1 + offset))
    }
    return result
  }

  /** A target name resolved relative to the host text, paired with its start offset. */
  private class Target(val name: String, val start: Int, val soft: Boolean)

  /** Builds references for every Sphinx Python-domain target found in the given docstring expression. */
  fun findReferences(host: PyStringLiteralExpression): List<PsiReference> =
    if (isInInjectedFragment(host)) emptyList()
    else mapValueRanges(host) { text, offset -> collectReferences(host, offset, text) }

  /** Builds references for every Sphinx Python-domain target found in the given line comment. */
  fun findReferences(comment: PsiComment): List<PsiReference> =
    if (isInInjectedFragment(comment)) emptyList() else collectReferences(comment, 0, comment.text)

  /**
   * Sphinx reads no markup in an injected fragment, such as the body of a `.. code-block:: python` directive, because
   * it renders the fragment as literal code. The host docstring skips the same range in [mapValueRanges]. So a
   * docstring or a comment of the fragment gets no reference and no tag highlighting.
   */
  private fun isInInjectedFragment(element: PsiElement): Boolean =
    InjectedLanguageManager.getInstance(element.project).isInjectedFragment(element.containingFile)

  private fun collectReferences(host: PsiElement, offset: Int, text: String): List<PsiReference> {
    val roleTargets = ROLE_PATTERN.findAll(text).mapNotNull { m ->
      m.groups[1]?.let { parseRoleTarget(it.value, it.range.first) }
    }
    // A directive signature gets a soft reference: navigate when possible, never warn. Sphinx qualifies the name by
    // the context, which this reference does not track. That context is the `.. py:currentmodule::` directive and an
    // enclosing directive, so `close` under `.. py:class:: Socket` means `Socket.close`. A signature can also
    // document an object that has no Python source, such as a member of a C extension.
    val signatureTargets = if (host is PsiComment) emptySequence()
    else DIRECTIVE_PATTERN.findAll(text).mapNotNull { m ->
      m.groups[1]?.let { parseSignatureTarget(it.value, it.range.first) }
    }

    val result = ArrayList<PsiReference>()
    for (target in roleTargets + signatureTargets) {
      result.addAll(buildComponentReferences(host, offset, target))
    }
    return result
  }

  /**
   * Returns the element-relative ranges of the Sphinx markup so the spellchecker can skip both the role keyword and
   * the referenced names. The ranges are sorted and they do not overlap.
   */
  fun findMarkupRanges(host: PyStringLiteralExpression): List<TextRange> =
    mapValueRanges(host) { text, offset -> DOCSTRING_MARKUP_REGEX.findAll(text).map { it.toTextRange(offset) }.toList() }

  fun findMarkupRanges(comment: PsiComment): List<TextRange> =
    COMMENT_MARKUP_REGEX.findAll(comment.text).map { it.toTextRange(0) }.toList()

  /**
   * Applies [mapper] to the raw text of every string element of [host], and returns the concatenated result.
   * An offset in the result is relative to the start of [host]. A range that a language injection covers, such as
   * the body of a `.. code-block:: python` directive or a doctest line, is skipped: Sphinx reads no markup there.
   */
  private fun <T> mapValueRanges(host: PyStringLiteralExpression, mapper: (String, Int) -> List<T>): List<T> {
    val valueRanges = host.stringValueTextRanges
    if (valueRanges.isEmpty()) return emptyList()
    val hostText = host.text
    val injected = injectedRanges(host)
    val result = ArrayList<T>()
    for (valueRange in valueRanges) {
      if (injected.any { it.intersects(valueRange) }) {
        // Split the value around the injected fragments and map each remaining piece on its own.
        for (piece in subtractRanges(valueRange, injected)) {
          result.addAll(mapper(piece.substring(hostText), piece.startOffset))
        }
      }
      else {
        result.addAll(mapper(valueRange.substring(hostText), valueRange.startOffset))
      }
    }
    return result
  }

  private fun injectedRanges(host: PsiElement): List<TextRange> {
    val files = InjectedLanguageManager.getInstance(host.project).getInjectedPsiFiles(host) ?: return emptyList()
    return files.map { it.second }
  }

  private fun subtractRanges(range: TextRange, holes: List<TextRange>): List<TextRange> {
    val overlaps = holes.mapNotNull { range.intersection(it) }.filterNot { it.isEmpty }.sortedBy { it.startOffset }
    val result = ArrayList<TextRange>()
    var current = range.startOffset
    for (hole in overlaps) {
      if (hole.startOffset > current) result.add(TextRange(current, hole.startOffset))
      current = maxOf(current, hole.endOffset)
    }
    if (current < range.endOffset) result.add(TextRange(current, range.endOffset))
    return result
  }

  private fun MatchResult.toTextRange(offset: Int): TextRange = TextRange(range.first + offset, range.last + 1 + offset)

  /** Extracts the dotted target from the content of a role, honoring `~`, `!`, `.` and the `text <target>` form. */
  private fun parseRoleTarget(content: String, contentStart: Int): Target? {
    var value = content
    var start = contentStart

    // Explicit title: `text <target>` -> reference only the target.
    val lt = value.lastIndexOf('<')
    if (lt >= 0 && value.endsWith(">")) {
      start += lt + 1
      value = value.substring(lt + 1, value.length - 1)
    }

    if (value.startsWith("!")) return null  // suppressed reference

    var isSoft = false
    if (value.startsWith("~")) {
      value = value.substring(1); start += 1
    }
    if (value.startsWith(".")) {
      // Leading dot triggers Sphinx suffix lookup; resolution is best-effort, so do not warn.
      value = value.substring(1); start += 1; isSoft = true
    }
    if (value.endsWith("()")) {
      value = value.substring(0, value.length - 2)
    }
    if (value.isEmpty()) return null
    return Target(value, start, isSoft)
  }

  /** Extracts the dotted name from a directive signature, dropping any parameter or type-parameter list. */
  private fun parseSignatureTarget(signature: String, signatureStart: Int): Target? {
    val end = signature.indexOfFirst { it == '(' || it == '[' }
    val name = if (end >= 0) signature.substring(0, end) else signature
    if (name.isEmpty()) return null
    return Target(name, signatureStart, soft = true)
  }

  private fun buildComponentReferences(host: PsiElement, offset: Int, target: Target): List<PsiReference> {
    val parts = target.name.split('.')
    val names = ArrayList<String>(parts.size)
    val references = ArrayList<PySphinxReference>(parts.size)
    var pos = target.start
    for (part in parts) {
      val start = pos
      pos += part.length + 1  // skip the '.' separator
      if (part.isEmpty() || !PyNames.isIdentifier(part)) return emptyList()
      names.add(part)
      val range = TextRange(start + offset, start + part.length + offset)
      val prefixRange = TextRange(target.start + offset, start + part.length + offset)
      references.add(PySphinxReference(host, range, prefixRange, QualifiedName.fromComponents(names.toList()), target.soft))
    }
    if (references.size > 1) {
      val head = references[0]
      references.forEach { it.head = head }
    }
    return references
  }
}
