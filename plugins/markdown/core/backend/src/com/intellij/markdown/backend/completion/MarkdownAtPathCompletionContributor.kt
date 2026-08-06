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
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.intellij.util.text.nullize
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownAtPath

internal class MarkdownAtPathCompletionContributor : CompletionContributor() {
  private val maxIndexedItems = 200
  private val maxNames = 1000

  init {
    extend(CompletionType.BASIC, psiElement(), object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
      ) {
        val file = parameters.position.containingFile
        val element = PsiTreeUtil.getParentOfType(
          file.findElementAt(parameters.offset),
          MarkdownAtPath::class.java,
        ) ?: return
        val elementStart = element.textRange.startOffset
        val pathPrefix = element.text.substring(1, parameters.offset - elementStart)

        val baseDirectory = BaseProjectDirectories.getInstance(parameters.position.project)
          .getBaseDirectoryFor(parameters.originalFile.virtualFile ?: return) ?: return
        val matcher = PathMatcher(pathPrefix)
        val addedPaths = hashSetOf<String>()

        if (pathPrefix.isEmpty()) {
          addHistoryItems(parameters, result, baseDirectory, matcher, pathPrefix, elementStart, addedPaths)
          return
        }

        val scope = GlobalSearchScope.projectScope(parameters.position.project)
        val names = linkedSetOf<String>()
        FilenameIndex.processAllFileNames({ name ->
          if (matcher.matchesName(name)) names.add(name)
          names.size < maxNames
        }, scope, null)

        var added = 0
        FilenameIndex.processFilesByNames(names, true, scope, null) { file ->
          ProgressManager.checkCanceled()
          val relativePath = VfsUtilCore.getRelativePath(file, baseDirectory, '/')?.nullize()
          if (file.isValid && relativePath != null && addedPaths.add(relativePath) && matcher.matchesPath(file.name, relativePath)) {
            result.addElement(createLookupElement(file, relativePath, elementStart))
            added++
          }
          added < maxIndexedItems
        }
      }
    })
  }

  private fun addHistoryItems(
    parameters: CompletionParameters,
    result: CompletionResultSet,
    baseDirectory: VirtualFile,
    matcher: PathMatcher,
    pathPrefix: String,
    elementStart: Int,
    addedPaths: MutableSet<String>,
  ) {
    val history = EditorHistoryManager.getInstance(parameters.position.project).fileList
      .takeLast(100)
      .asReversed()
    history.forEach { file ->
      if (!file.isValid) return@forEach
      val relativePath = VfsUtilCore.getRelativePath(file, baseDirectory, '/')?.nullize() ?: return@forEach
      if (pathPrefix.isNotEmpty() && !matcher.matchesPath(file.name, relativePath)) return@forEach
      if (!addedPaths.add(relativePath)) return@forEach
      result.addElement(createLookupElement(file, relativePath, elementStart))
    }
  }

  private fun createLookupElement(file: VirtualFile, relativePath: String, elementStart: Int): LookupElementBuilder =
    LookupElementBuilder.create(relativePath, file.name)
      .withLookupString(relativePath)
      .withPresentableText(relativePath)
      .withIcon(VirtualFilePresentation.getIcon(file))
      .withInsertHandler(PathInsertHandler(elementStart))

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

  private class PathInsertHandler(private val startOffset: Int) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
      val endOffset = context.editor.caretModel.offset
      val path = item.`object` as? String ?: return
      context.document.replaceString(startOffset, endOffset, "@$path")
      context.editor.caretModel.moveToOffset(startOffset + path.length + 1)
    }
  }
}
