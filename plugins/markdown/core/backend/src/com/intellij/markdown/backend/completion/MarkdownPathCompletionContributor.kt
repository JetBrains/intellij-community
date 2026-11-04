// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.ide.presentation.VirtualFilePresentation
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.ProcessingContext
import com.intellij.util.text.nullize

internal abstract class MarkdownPathCompletionContributor : CompletionContributor() {
  private companion object {
    const val MAX_INDEXED_ITEMS = 200
    const val MAX_NAMES = 1000
  }

  init {
    extend(CompletionType.BASIC, psiElement(), object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
      ) {
        val path = findPath(parameters) ?: return
        val baseDirectory = BaseProjectDirectories.getInstance(parameters.position.project)
          .getBaseDirectoryFor(parameters.originalFile.virtualFile ?: return) ?: return
        val matcher = PathMatcher(path.pathPrefix)
        val addedPaths = hashSetOf<String>()

        if (path.pathPrefix.isEmpty()) {
          addHistoryItems(parameters, result, baseDirectory, matcher, path, addedPaths)
          return
        }

        val scope = GlobalSearchScope.projectScope(parameters.position.project)
        val names = linkedSetOf<String>()
        FilenameIndex.processAllFileNames({ name ->
          if (matcher.matchesName(name)) names.add(name)
          names.size < MAX_NAMES
        }, scope, null)

        FilenameIndex.processFilesByNames(names, true, scope, null) { file ->
          ProgressManager.checkCanceled()
          val relativePath = VfsUtilCore.getRelativePath(file, baseDirectory, '/')?.nullize()
          if (file.isValid && relativePath != null && matcher.matchesPath(file.name, relativePath) && addedPaths.add(relativePath)) {
            result.addElement(createLookupElement(file, relativePath, path))
          }
          addedPaths.size < MAX_INDEXED_ITEMS
        }
      }
    })
  }

  protected abstract fun findPath(parameters: CompletionParameters): PathCompletionContext?

  protected fun findElementAtCaret(parameters: CompletionParameters): PsiElement {
    val file = parameters.position.containingFile
    return PsiUtilCore.getElementAtOffset(file, parameters.offset)
  }

  protected data class PathCompletionContext(
    val pathPrefix: String,
    val startOffset: Int,
    val insertionPrefix: String,
  )

  private fun addHistoryItems(
    parameters: CompletionParameters,
    result: CompletionResultSet,
    baseDirectory: VirtualFile,
    matcher: PathMatcher,
    path: PathCompletionContext,
    addedPaths: MutableSet<String>,
  ) {
    val history = EditorHistoryManager.getInstance(parameters.position.project).fileList
      .takeLast(100)
      .asReversed()
    history.forEach { file ->
      if (!file.isValid) return@forEach
      val relativePath = VfsUtilCore.getRelativePath(file, baseDirectory, '/')?.nullize() ?: return@forEach
      if (path.pathPrefix.isNotEmpty() && !matcher.matchesPath(file.name, relativePath)) return@forEach
      if (!addedPaths.add(relativePath)) return@forEach
      result.addElement(createLookupElement(file, relativePath, path))
    }
  }

  private fun createLookupElement(
    file: VirtualFile,
    relativePath: String,
    path: PathCompletionContext,
  ): LookupElementBuilder =
    LookupElementBuilder.create(relativePath, file.name)
      .withLookupString(relativePath)
      .withPresentableText(relativePath)
      .withIcon(VirtualFilePresentation.getIcon(file))
      .withInsertHandler(PathInsertHandler(path.startOffset, path.insertionPrefix))

  private class PathMatcher(pattern: String) {
    private val parts = pattern.split('/').filter(String::isNotEmpty).map { it.lowercase() }
    private val namePart = parts.lastOrNull().orEmpty()

    fun matchesName(name: String): Boolean = name.contains(namePart, ignoreCase = true)

    fun matchesPath(name: String, path: String): Boolean {
      if (parts.isEmpty()) return false
      if (parts.size == 1) return matchesName(name)
      var nextPart = 0
      for (pathPart in path.split('/')) {
        if (pathPart.contains(parts[nextPart], ignoreCase = true)) {
          nextPart++
          if (nextPart == parts.size) return true
        }
      }
      return false
    }
  }

  private class PathInsertHandler(
    private val startOffset: Int,
    private val insertionPrefix: String,
  ) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
      val endOffset = context.editor.caretModel.offset
      val path = item.`object` as? String ?: return
      context.document.replaceString(startOffset, endOffset, insertionPrefix + path)
      context.editor.caretModel.moveToOffset(startOffset + insertionPrefix.length + path.length)
    }
  }
}
