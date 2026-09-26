// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.psi

import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.virtualFile
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.psi.spi.PyProjectTomlPathLocator
import com.intellij.util.ProcessingContext
import org.toml.lang.psi.TomlHeaderOwner
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlKeyValueOwner
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

/**
 * Makes filesystem paths declared in `pyproject.toml` navigable (PY-90384).
 *
 * Which keys hold paths is each tool's own business and is deliberately unknown here: a dependency manager
 * declares its path keys by implementing [PyProjectTomlPathLocator], and this contributor asks the registered
 * ones through [resolvePyProjectTomlPath]. Everything in this file is tool-agnostic — reading the dotted key
 * path out of the PSI, locating the base directory, cutting off glob patterns and building the references —
 * so supporting one more tool means touching that tool's manager, never this class.
 *
 * Dependency *names* (`[project] dependencies`, `dependency-groups`, …) are covered elsewhere: the
 * `Requirements` language is injected into those strings and `RequirementsReferenceContributor`
 * attaches references there. This contributor covers plain TOML path strings, which had no references
 * at all, so Ctrl+Click on them reported "Cannot find declaration to go to".
 */
internal class PyProjectTomlPathReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      psiElement(TomlLiteral::class.java).inVirtualFile(virtualFile().withName(PY_PROJECT_TOML)),
      PyProjectTomlPathReferenceProvider(),
    )
  }
}

/** Path fragments containing one of these are patterns, not names, and can't be resolved to a file. */
private const val GLOB_CHARS = "*?[]"

/** A path-valued position: the directory relative paths are resolved from, and whether files may be targeted too. */
private class PathTarget(val baseDir: PsiDirectory, val acceptFiles: Boolean)

private class PyProjectTomlPathReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<FileReference> {
    val literal = element as? TomlLiteral ?: return emptyArray()
    if (literal.kind !is TomlLiteralKind.String) return emptyArray()
    val target = pathTarget(literal) ?: return emptyArray()
    return PyProjectTomlFileReferenceSet(literal, target).allReferences
  }

  private fun pathTarget(literal: TomlLiteral): PathTarget? {
    val keyPath = tomlKeyPath(literal) ?: return null
    val pathValue = resolvePyProjectTomlPath(keyPath) ?: return null
    val ownDir = literal.containingFile?.originalFile?.containingDirectory ?: return null
    val baseDir = pathValue.baseSiblingKey?.let { siblingDir(literal, ownDir, it) } ?: ownDir
    return PathTarget(baseDir, pathValue.acceptFiles)
  }

  /** Directory named by the [key] entry of the table owning [literal], `null` when there is none. */
  private fun siblingDir(literal: TomlLiteral, ownDir: PsiDirectory, key: String): PsiDirectory? {
    val owner = PsiTreeUtil.getParentOfType(literal, TomlKeyValueOwner::class.java) ?: return null
    val sibling = owner.entries.firstOrNull { it.key.text == key }?.value as? TomlLiteral ?: return null
    val siblingPath = (sibling.kind as? TomlLiteralKind.String)?.value?.takeIf { it.isNotBlank() } ?: return null
    val siblingDir = ownDir.virtualFile.findFileByRelativePath(siblingPath)?.takeIf { it.isDirectory } ?: return null
    return literal.manager.findDirectory(siblingDir)
  }
}

/**
 * Dotted key path of [element]'s position, `null` when a key on the way has no name (broken PSI while typing).
 *
 * Array indices and inline-table nesting are collapsed, so every spelling of one option yields the same path:
 * `[a.b] key = ["x"]`, `[a] b = { key = ["x"] }` and `[a] b.key = ["x"]` all give `["a", "b", "key"]`. A locator
 * therefore matches one key path per option instead of enumerating the ways it can be written down.
 */
private fun tomlKeyPath(element: PsiElement): List<String>? {
  val path = mutableListOf<String>()
  var current: PsiElement? = element
  while (current != null) {
    when (current) {
      is PsiFile -> return path
      is TomlKeyValue -> path.addAll(0, current.key.segments.map { it.name ?: return null })
      is TomlHeaderOwner -> path.addAll(0, (current.header.key ?: return null).segments.map { it.name ?: return null })
      else -> {}
    }
    current = current.parent
  }
  return path
}

/**
 * References for a `pyproject.toml` path string, resolved against [target] alone: a relative path in
 * `pyproject.toml` is relative to the file declaring it, never to a source root, while the inherited default
 * contexts come from [com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceHelper]s and
 * would also resolve paths the tools themselves reject.
 */
private class PyProjectTomlFileReferenceSet private constructor(
  literal: TomlLiteral,
  valueRange: TextRange,
  private val target: PathTarget,
) : FileReferenceSet(
  /* str = */ valueRange.substring(literal.text),
  /* element = */ literal,
  /* startInElement = */ valueRange.startOffset,
  /* provider = */ null,
  /* caseSensitive = */ false,
  /* endingSlashNotAllowed = */ false,
  /* suitableFileTypes = */ null,
  // Parsed from `init` instead: the super constructor would call `reparse()` before `globFound` exists.
  /* init = */ false,
) {
  private var globFound: Boolean = false

  constructor(literal: TomlLiteral, target: PathTarget) : this(literal, ElementManipulators.getValueTextRange(literal), target)

  init {
    reparse()
  }

  override fun reparse() {
    globFound = false
    super.reparse()
  }

  /**
   * Stops at the first glob fragment (the `&#42;` of `members = ["sub-projects/&#42;"]`): the fragments before
   * it are real directories worth navigating to, while a reference for the pattern itself could never resolve
   * and `TomlUnresolvedReferenceInspection` would report it as an unresolved symbol.
   */
  override fun createFileReference(range: TextRange, index: Int, text: String): FileReference? {
    if (!globFound && text.any { it in GLOB_CHARS }) {
      globFound = true
    }
    return if (globFound) null else super.createFileReference(range, index, text)
  }

  override fun computeDefaultContexts(): Collection<PsiFileSystemItem> =
    if (isAbsolutePathReference) super.computeDefaultContexts() else listOf(target.baseDir)

  override fun getReferenceCompletionFilter(): Condition<PsiFileSystemItem> =
    Condition { item -> item is PsiDirectory || target.acceptFiles }
}
