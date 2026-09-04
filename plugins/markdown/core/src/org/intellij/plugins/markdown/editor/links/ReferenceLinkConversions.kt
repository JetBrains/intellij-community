// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.links

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.parentOfType
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.markerblocks.providers.LinkReferenceDefinitionProvider
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownImage
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownInlineLink
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDefinition
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkLabel
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkText
import org.intellij.plugins.markdown.lang.psi.util.children
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.intellij.plugins.markdown.lang.psi.util.parentOfType
import org.intellij.plugins.markdown.util.isFootnoteLabelText
import org.jetbrains.annotations.ApiStatus

/**
 * Converts a Markdown link between the inline form `[text](url)` and the reference form `[text][label]`.
 *
 * Every method changes the PSI of a single file, so an intention that calls it supports the preview.
 * A conversion walks the file one time and keeps the link elements of that walk, so a lookup starts no second walk.
 * An availability check needs a definition only, so it walks the block level and enters no inline content.
 */
@ApiStatus.Internal
object ReferenceLinkConversions {
  private const val MAX_LABEL_LENGTH = 24
  private const val FALLBACK_LABEL = "ref"

  /** The characters that a link text escapes with a backslash. Each one changes the shape of the link. */
  private const val ESCAPED_CHARACTERS = "\\[]"

  private val referenceLinkTypes = TokenSet.create(
    MarkdownElementTypes.FULL_REFERENCE_LINK,
    MarkdownElementTypes.SHORT_REFERENCE_LINK,
  )

  /**
   * A block that a close marker ends. The parser runs such a block to the end of the file when the marker is missing.
   *
   * A code fence waits for its second fence, and a raw HTML block such as `<!--` or `<script>` waits for its closing tag.
   */
  private val openEndedTypes = TokenSet.create(
    MarkdownElementTypes.CODE_FENCE,
    MarkdownElementTypes.HTML_BLOCK,
  )

  /**
   * A block that holds no other block.
   *
   * A definition is a block, so a search for one enters no such block.
   * A leaf block holds the inline content or the raw text of a file, which is nearly every element of a large file.
   */
  private val leafBlockTypes = TokenSet.orSet(
    MarkdownTokenTypeSets.HEADERS,
    TokenSet.create(
      MarkdownElementTypes.PARAGRAPH,
      MarkdownElementTypes.CODE_FENCE,
      MarkdownElementTypes.CODE_BLOCK,
      MarkdownElementTypes.HTML_BLOCK,
      MarkdownElementTypes.TABLE,
      MarkdownElementTypes.LINK_DEFINITION,
    ),
  )

  /** The token that opens a block quote or a list item. A block that holds only such a token is empty. */
  private val blockMarkerTypes = TokenSet.create(
    MarkdownTokenTypes.BLOCK_QUOTE,
    MarkdownTokenTypes.LIST_BULLET,
    MarkdownTokenTypes.LIST_NUMBER,
  )

  private val separatorRegex = Regex("[^\\p{L}\\p{N}]+")

  private val whitespaceRegex = Regex("\\s+")

  /**
   * Returns the inline link that holds [element].
   *
   * Returns `null` for an image, for a link inside another link, for a link with no destination,
   * and for a link text with an unescaped bracket.
   */
  fun findInlineLink(element: PsiElement): MarkdownInlineLink? {
    val link = element.parentOfType<MarkdownInlineLink>(withSelf = true) ?: return null
    return link.takeIf { it.isConvertible() }
  }

  /**
   * Returns the full or short reference link that holds [element].
   *
   * Returns `null` for a footnote reference, for a collapsed reference link `[label][]`,
   * for a label with no definition in the same file, and for a definition that no inline link holds.
   */
  fun findReferenceLink(element: PsiElement): PsiElement? {
    val link = element.parentOfType(referenceLinkTypes, withSelf = true) ?: return null
    val label = link.referenceLabelText() ?: return null
    val file = link.containingFile ?: return null
    val definition = findDefinitionByLabel(definitions(file), label) ?: return null
    return link.takeIf { it.inlineReplacement(definition) != null }
  }

  /** Returns the definition of [label]. The match normalizes the label, as CommonMark specifies. */
  private fun findDefinitionByLabel(definitions: Sequence<MarkdownLinkDefinition>, label: String): MarkdownLinkDefinition? {
    val normalized = normalizeLabel(label)
    return definitions.find { normalizeLabel(it.linkLabel.labelText) == normalized }
  }

  /**
   * Returns the definition with [destination] and [title] that a new reference link can use.
   *
   * The destination comparison follows the renderer.
   * A definition that an earlier definition with the same label hides is left out, because its label leads to another address.
   */
  private fun findDefinitionByDestination(fileLinks: FileLinks, destination: String, title: String?): MarkdownLinkDefinition? {
    val key = destinationKey(destination)
    return reachableDefinitions(fileLinks).find {
      destinationKey(it.linkDestination.text) == key && it.linkTitle?.text == title
    }
  }

  /** Builds a label from the link text, then from the destination host, then from [FALLBACK_LABEL]. The label is free in the file. */
  private fun suggestLabel(fileLinks: FileLinks, link: MarkdownInlineLink): String {
    val destination = unwrapDestination(link.linkDestination?.text.orEmpty())
    val base = sequenceOf(link.linkTextContent().orEmpty(), destinationHost(destination))
                 .map(::slug)
                 .firstOrNull { it.isNotEmpty() } ?: FALLBACK_LABEL
    return uniqueLabel(fileLinks, base)
  }

  /**
   * Converts [link] and every inline link of the same file with the same destination and the same title.
   *
   * Reuses the label of a definition with the same destination and the same title.
   * Otherwise it appends a new definition at the end of the file, before a block that has no end marker.
   * Returns the label of the converted [link].
   */
  fun convertToReferenceLink(link: MarkdownInlineLink): MarkdownLinkLabel? {
    val file = link.containingFile ?: return null
    val project = file.project
    val destination = link.linkDestination?.text ?: return null
    val title = link.linkTitleText()
    val fileLinks = collectFileLinks(file)
    val definition = findDefinitionByDestination(fileLinks, destination, title)
    val label = definition?.linkLabel?.labelText ?: suggestLabel(fileLinks, link)
    val written = definitionDestination(destination) ?: return null

    val links = collectInlineLinks(fileLinks, destination, title)
    val caretIndex = links.indexOf(link)
    val manager = SmartPointerManager.getInstance(project)
    val pointers = links.map { manager.createSmartPsiElementPointer(it) }

    var caretLabel: MarkdownLinkLabel? = null
    var declaration: PsiElement? = null
    for ((index, pointer) in pointers.withIndex()) {
      val target = pointer.element ?: continue
      val text = target.linkTextContent()?.takeIf { it.isNotEmpty() } ?: escapeLinkText(unwrapDestination(destination))
      val created = MarkdownPsiElementFactory.createLinkDeclarationAndReference(project, written, text, title, label)
      declaration = created.second
      val replacement = created.first.firstChild ?: continue
      val inserted = target.replace(replacement)
      if (index == caretIndex) {
        caretLabel = inserted.children().filterIsInstance<MarkdownLinkLabel>().lastOrNull()
      }
    }

    if (definition == null && declaration != null) {
      appendDefinition(file, declaration)
    }
    return caretLabel
  }

  /**
   * Converts every reference link of the file that uses the label of the reference link at [element], then removes the definition.
   *
   * The file keeps the definition when a collapsed reference link `[label][]` still needs it.
   * A replacement carries an inline link, so it creates no collapsed link, and the check runs before the first change.
   * Returns `false` when [element] holds no reference link, when the label has no definition,
   * or when one reference link takes no inline form.
   * The file then keeps the definition, so every reference link still resolves.
   */
  fun convertToInlineLink(element: PsiElement): Boolean {
    val link = element.parentOfType(referenceLinkTypes, withSelf = true) ?: return false
    val file = link.containingFile ?: return false
    val project = file.project
    val label = link.referenceLabelText() ?: return false
    val fileLinks = collectFileLinks(file)
    val definition = findDefinitionByLabel(fileLinks.definitions.asSequence(), label) ?: return false
    val keepDefinition = hasCollapsedReferenceLink(fileLinks, label)

    val manager = SmartPointerManager.getInstance(project)
    val definitionPointer = manager.createSmartPsiElementPointer(definition)
    val replacements = collectReferenceLinks(fileLinks, label).map { target ->
      val replacement = target.inlineReplacement(definition) ?: return false
      manager.createSmartPsiElementPointer(target) to replacement
    }

    for ((pointer, replacement) in replacements) {
      val target = pointer.element ?: return false
      target.replace(replacement)
    }

    if (!keepDefinition) {
      definitionPointer.element?.let(::removeDefinition)
    }
    return true
  }

  private fun MarkdownInlineLink.isConvertible(): Boolean {
    if (parent is MarkdownImage) return false
    if (parentOfType<MarkdownInlineLink>() != null) return false
    val destination = linkDestination?.text ?: return false
    if (definitionDestination(destination) == null) return false
    val title = linkTitleText()
    if (title != null && !isDefinitionTitle(title)) return false
    return linkTextContent()?.hasUnescapedBracket() != true
  }

  private fun MarkdownInlineLink.linkTextContent(): String? {
    return linkText?.text?.removeSurrounding("[", "]")
  }

  private fun MarkdownInlineLink.linkTitleText(): String? {
    return children().find { it.hasType(MarkdownElementTypes.LINK_TITLE) }?.text
  }

  private fun PsiElement.linkLabelElement(): MarkdownLinkLabel? {
    return children().filterIsInstance<MarkdownLinkLabel>().lastOrNull()
  }

  /** Returns the label of a reference link. Returns `null` for a collapsed link `[label][]`, which the conversion leaves alone. */
  private fun PsiElement.referenceLabelText(): String? {
    if (isCollapsedReferenceLink()) return null
    return linkLabelText()
  }

  /** Returns the label of a reference link of any form. Returns `null` for a footnote reference. */
  private fun PsiElement.linkLabelText(): String? {
    if (!hasType(referenceLinkTypes)) return null
    val label = linkLabelElement() ?: return null
    if (isFootnoteLabelText(label.text)) return null
    return label.labelText.takeIf { it.isNotBlank() }
  }

  /** The parser reads a collapsed link `[label][]` as a short reference link with a trailing `[]` pair. */
  private fun PsiElement.isCollapsedReferenceLink(): Boolean {
    return hasType(MarkdownElementTypes.SHORT_REFERENCE_LINK) && children().count() > 1
  }

  private fun PsiElement.referenceLinkText(): String? {
    val text = children().filterIsInstance<MarkdownLinkText>().firstOrNull()?.text?.removeSurrounding("[", "]")
    return text?.takeIf { it.isNotEmpty() } ?: referenceLabelText()
  }

  /**
   * The link elements of one file, from a single walk of the tree.
   *
   * A conversion reads the lists many times, so it walks the file one time and then reads them here.
   */
  private class FileLinks(
    /** Every definition that a reference link can use. */
    val definitions: List<MarkdownLinkDefinition>,
    val inlineLinks: List<MarkdownInlineLink>,
    /** Every full and short reference link. A collapsed link `[label][]` is a short reference link. */
    val referenceLinks: List<PsiElement>,
    /** Every link label, of a definition and of a reference link alike. */
    val labels: List<MarkdownLinkLabel>,
  )

  /** Walks [file] one time and sorts its link elements. */
  private fun collectFileLinks(file: PsiFile): FileLinks {
    val definitions = mutableListOf<MarkdownLinkDefinition>()
    val inlineLinks = mutableListOf<MarkdownInlineLink>()
    val referenceLinks = mutableListOf<PsiElement>()
    val labels = mutableListOf<MarkdownLinkLabel>()
    for (element in descendants(file)) {
      when {
        element is MarkdownLinkDefinition -> if (element.isUsableDefinition()) definitions.add(element)
        element is MarkdownInlineLink -> inlineLinks.add(element)
        element is MarkdownLinkLabel -> labels.add(element)
        element.hasType(referenceLinkTypes) -> referenceLinks.add(element)
      }
    }
    return FileLinks(definitions, inlineLinks, referenceLinks, labels)
  }

  /**
   * Returns every definition of [file] that a reference link can use.
   *
   * A definition is a block, so the walk enters a block container only, and it leaves out every element below a leaf block.
   * The walk therefore costs the block count of [file], which an availability check can pay.
   */
  private fun definitions(file: PsiFile): Sequence<MarkdownLinkDefinition> {
    return SyntaxTraverser.psiTraverser(file).expand { !it.hasType(leafBlockTypes) }.asSequence()
      .filterIsInstance<MarkdownLinkDefinition>()
      .filter { it.isUsableDefinition() }
  }

  /**
   * Tells whether a reference link can use the definition.
   *
   * A footnote definition is left out, because a footnote label is not a link label.
   * A comment is left out, because it holds a note and no address.
   */
  private fun MarkdownLinkDefinition.isUsableDefinition(): Boolean {
    return !isFootnoteLabelText(linkLabel.text) && !isComment()
  }

  /**
   * Returns every definition that a reference link reaches.
   *
   * CommonMark takes the first definition of a label, so a later definition with the same label reaches no link.
   */
  private fun reachableDefinitions(fileLinks: FileLinks): Sequence<MarkdownLinkDefinition> {
    val labels = mutableSetOf<String>()
    return fileLinks.definitions.asSequence().filter { labels.add(normalizeLabel(it.linkLabel.labelText)) }
  }

  /**
   * Tells a Markdown comment `[//]: # (a note)` from a link definition.
   *
   * The parser wraps the note of a comment in a single element, so a definition such as `[//]: https://example.com` stays a definition.
   */
  private fun MarkdownLinkDefinition.isComment(): Boolean {
    return children().any { it.hasType(MarkdownElementTypes.LINK_COMMENT) }
  }

  private fun collectInlineLinks(fileLinks: FileLinks, destination: String, title: String?): List<MarkdownInlineLink> {
    val key = destinationKey(destination)
    return fileLinks.inlineLinks.filter { candidate ->
      candidate.isConvertible() &&
      destinationKey(candidate.linkDestination?.text.orEmpty()) == key &&
      candidate.linkTitleText() == title
    }
  }

  /** Tells whether a collapsed reference link `[label][]` uses [label]. Such a link keeps the definition alive. */
  private fun hasCollapsedReferenceLink(fileLinks: FileLinks, label: String): Boolean {
    val normalized = normalizeLabel(label)
    return fileLinks.referenceLinks.any {
      it.isCollapsedReferenceLink() && it.linkLabelText()?.let(::normalizeLabel) == normalized
    }
  }

  private fun collectReferenceLinks(fileLinks: FileLinks, label: String): List<PsiElement> {
    val normalized = normalizeLabel(label)
    return fileLinks.referenceLinks.filter { it.referenceLabelText()?.let(::normalizeLabel) == normalized }
  }

  /**
   * Builds the inline link that replaces [this] with the address of [definition].
   *
   * Returns `null` when no inline link holds that address.
   * The definition parser takes a destination such as `foo(bar`, and the inline parser rejects it.
   */
  private fun PsiElement.inlineReplacement(definition: MarkdownLinkDefinition): PsiElement? {
    val text = referenceLinkText() ?: return null
    return createInlineLink(project, text, definition.linkDestination.text, definition.linkTitle?.text)
  }

  /** Builds an inline link, or returns `null` when the parser reads a part of the text as something else. */
  private fun createInlineLink(project: Project, text: String, destination: String, title: String?): PsiElement? {
    val suffix = if (title == null) "" else " $title"
    val content = "[$text]($destination$suffix)"
    val file = MarkdownPsiElementFactory.createFile(project, content)
    val link = descendants(file).filterIsInstance<MarkdownInlineLink>().firstOrNull() ?: return null
    return link.takeIf { it.textLength == content.length }
  }

  private fun appendDefinition(file: PsiFile, declaration: PsiElement) {
    val block = trailingOpenBlock(file, declaration.text)
    if (block == null) {
      addNewLines(file, missingLineBreaks(file.text), anchor = null)
      file.add(declaration)
      return
    }
    addNewLines(file, missingLineBreaks(file.text.substring(0, block.textRange.startOffset)), anchor = block)
    file.addBefore(declaration, block)
    addNewLines(file, 2, anchor = block)
  }

  /**
   * Returns the last block of [file] when that block takes [definitionText] appended after it.
   *
   * A block with no end marker runs to the end of the file, so a definition after it becomes block content.
   * The definition goes before such a block instead.
   *
   * Only a top-level block takes the definition. A block inside a quote or a list ends with its container,
   * because the appended line carries no marker of that container.
   */
  private fun trailingOpenBlock(file: PsiFile, definitionText: String): PsiElement? {
    val last = file.children().lastOrNull { it.text.isNotBlank() } ?: return null
    if (!last.isOpenEnded()) return null
    if (last.hasType(MarkdownElementTypes.CODE_FENCE)) return last
    return last.takeIf { takesDefinition(file.project, it.text, definitionText) }
  }

  /**
   * Tells whether the element runs to the end of the file because its end marker is missing.
   *
   * A code fence shows its end marker in the tree, so the answer comes from the tree alone.
   * A raw HTML block holds its end marker in the text, and the marker depends on the tag.
   */
  private fun PsiElement.isOpenEnded(): Boolean {
    if (!hasType(openEndedTypes)) return false
    if (!hasType(MarkdownElementTypes.CODE_FENCE)) return true
    return children().none { it.hasType(MarkdownTokenTypes.CODE_FENCE_END) }
  }

  /**
   * Tells whether [blockText] takes [definitionText] appended to it, instead of leaving it a definition of its own.
   *
   * [blockText] holds a top-level block, so no block above it stays open, and the text before it cannot change the parse.
   * The probe therefore parses that block alone, and it leaves the rest of the file out.
   */
  private fun takesDefinition(project: Project, blockText: String, definitionText: String): Boolean {
    val text = blockText + "\n".repeat(missingLineBreaks(blockText)) + definitionText
    val probe = MarkdownPsiElementFactory.createFile(project, text)
    return probe.children().lastOrNull { it.text.isNotBlank() } !is MarkdownLinkDefinition
  }

  private fun addNewLines(file: PsiFile, count: Int, anchor: PsiElement?) {
    if (count <= 0) return
    val lineBreaks = MarkdownPsiElementFactory.createNewLines(file.project, count)
    when (anchor) {
      null -> file.addRange(lineBreaks.firstChild, lineBreaks.lastChild)
      else -> file.addRangeBefore(lineBreaks.firstChild, lineBreaks.lastChild, anchor)
    }
  }

  private fun missingLineBreaks(text: String): Int {
    if (text.isEmpty()) return 0
    return (2 - text.takeLastWhile { it == '\n' }.length).coerceAtLeast(0)
  }

  private fun removeDefinition(definition: MarkdownLinkDefinition) {
    val target = deletionTarget(definition)
    when {
      target.parent is PsiFile -> removeBlock(target)
      else -> removeNestedLine(target)
    }
  }

  /** Deletes [target] with the blank lines that separate it from the rest of the file. */
  private fun removeBlock(target: PsiElement) {
    val before = siblingRun(target, forward = false) { it.text.isBlank() }
    val after = siblingRun(target, forward = true) { it.text.isBlank() }
    val last = after.lastOrNull() ?: target
    val hasContentAfter = last.nextSibling != null
    val leading = when {
      hasContentAfter -> emptyList()
      after.isEmpty() -> before
      else -> before.dropLast(1)
    }
    target.parent.deleteChildRange(leading.lastOrNull() ?: target, last)
  }

  /**
   * Deletes the line of [target] inside a block quote or a list.
   *
   * A line break and a marker around [target] carry no text, so they go with it.
   * The marker that opens the line of [target] stays when a line follows, and it then opens that line.
   */
  private fun removeNestedLine(target: PsiElement) {
    val before = siblingRun(target, forward = false) { it.isBlockFiller() }
    val after = siblingRun(target, forward = true) { it.isBlockFiller() }
    val hasContentAfter = (after.lastOrNull() ?: target).nextSibling != null
    val first = if (hasContentAfter) target else before.lastOrNull() ?: target
    target.parent.deleteChildRange(first, after.lastOrNull() ?: target)
  }

  /**
   * Returns the outermost block that holds [definition] and no other content.
   *
   * A block quote and a list item keep their marker when only the definition goes, so the whole block goes instead.
   */
  private fun deletionTarget(definition: MarkdownLinkDefinition): PsiElement {
    var target: PsiElement = definition
    while (true) {
      val parent = target.parent
      if (parent == null || parent is PsiFile) return target
      if (parent.children().any { it !== target && !it.isBlockFiller() }) return target
      target = parent
    }
  }

  /**
   * Returns true when the element holds a block marker or a whitespace. The parser writes a quote marker as either one.
   *
   * Only a leaf fills a block. A composite element carries content, even when its text shows a marker alone.
   */
  private fun PsiElement.isBlockFiller(): Boolean {
    if (firstChild != null) return false
    return hasType(blockMarkerTypes) || text.all { it == '>' || it.isWhitespace() }
  }

  /** Returns the siblings of [element] that [accept] takes, nearest first. The run stops at the first other sibling. */
  private fun siblingRun(element: PsiElement, forward: Boolean, accept: (PsiElement) -> Boolean): List<PsiElement> {
    val siblings = generateSequence(if (forward) element.nextSibling else element.prevSibling) {
      if (forward) it.nextSibling else it.prevSibling
    }
    return siblings.takeWhile(accept).toList()
  }

  private fun uniqueLabel(fileLinks: FileLinks, base: String): String {
    val taken = takenLabels(fileLinks)
    if (normalizeLabel(base) !in taken) {
      return base
    }
    var index = 1
    while (normalizeLabel("$base-$index") in taken) {
      index++
    }
    return "$base-$index"
  }

  private fun takenLabels(fileLinks: FileLinks): Set<String> {
    return fileLinks.labels.asSequence()
      .filterNot { isFootnoteLabelText(it.text) }
      .map { normalizeLabel(it.labelText) }
      .filterTo(HashSet()) { it.isNotBlank() }
  }

  /** Returns [root] and every element below it. The traverser walks the tree with no recursion. */
  private fun descendants(root: PsiElement): Sequence<PsiElement> {
    return SyntaxTraverser.psiTraverser(root).asSequence()
  }

  private fun slug(text: String): String {
    return text.lowercase().replace(separatorRegex, "-").trim('-').take(MAX_LABEL_LENGTH).trim('-')
  }

  private fun destinationHost(destination: String): String {
    return destination.substringAfter("://")
      .substringBefore('/')
      .substringBefore('?')
      .substringBefore('#')
      .substringAfterLast('@')
      .substringBefore(':')
  }

  /**
   * Returns the address that the renderer builds from [destination].
   *
   * The renderer resolves an entity and an escape, then it encodes a space and a Unicode character.
   * Two destinations with one key point at one address, so `<foo bar>` and `foo%20bar` share a definition.
   */
  private fun destinationKey(destination: String): String {
    return LinkMap.normalizeDestination(destination.trim(), true).toString()
  }

  /** Returns [destination] without the `<…>` wrapper. The result keeps the text that the author wrote. */
  private fun unwrapDestination(destination: String): String {
    return destination.trim().removeSurrounding("<", ">")
  }

  /**
   * Returns the text of [destination] that a definition can hold, or `null` when no form of it parses.
   *
   * The definition parser stops at a space, in the bare form and in the `<…>` form alike.
   * A percent escape gives the parser a valid address, and [destinationKey] maps both forms to one key.
   */
  private fun definitionDestination(destination: String): String? {
    val direct = destination.trim()
    val escaped = unwrapDestination(direct).replace(" ", "%20").replace("\t", "%09")
    return sequenceOf(direct, escaped).firstOrNull(::isDefinitionDestination)
  }

  /** Tells whether the definition parser reads the whole of [destination] as one address. */
  private fun isDefinitionDestination(destination: String): Boolean {
    if (destination.isEmpty()) return false
    val range = LinkReferenceDefinitionProvider.matchLinkDestination(destination, 0) ?: return false
    return range.last == destination.lastIndex
  }

  /**
   * Tells whether the definition parser reads the whole of [title] as one title.
   *
   * The inline parser also takes a title that holds a blank line, or one that runs past the parser limit.
   * The definition parser drops such a title, and the definition then holds only a part of the line.
   */
  private fun isDefinitionTitle(title: String): Boolean {
    val range = LinkReferenceDefinitionProvider.matchLinkTitle(title, 0) ?: return false
    return range.last == title.lastIndex
  }

  /**
   * Normalizes [label] as the renderer does: it collapses a space run and folds the case.
   *
   * The renderer keeps the outer spaces of a label, so `[ docs ]` and `[docs]` name two labels.
   */
  private fun normalizeLabel(label: String): String = label.replace(whitespaceRegex, " ").lowercase()

  /**
   * Escapes every bracket and every backslash of [text].
   *
   * An empty link such as `[](foo]bar)` gives the destination as the link text, and a bare bracket then breaks the reference link.
   */
  private fun escapeLinkText(text: String): String {
    return buildString {
      for (character in text) {
        if (character in ESCAPED_CHARACTERS) append('\\')
        append(character)
      }
    }
  }

  private fun String.hasUnescapedBracket(): Boolean {
    var index = 0
    while (index < length) {
      when (this[index]) {
        '\\' -> index++
        '[', ']' -> return true
      }
      index++
    }
    return false
  }
}
