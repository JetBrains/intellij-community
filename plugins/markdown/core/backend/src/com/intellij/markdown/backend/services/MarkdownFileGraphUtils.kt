// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.backend.services

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownLinkDestination
import org.intellij.plugins.markdown.lang.references.paths.resolveReferences
import org.jetbrains.annotations.ApiStatus
import java.util.function.Predicate

@ApiStatus.Experimental
object MarkdownFileGraphUtils {

  /**
   * Collects the connected component of Markdown files that contains [root]. Currently, the dependencies are detected via
   * [MarkdownLinkDestination].
   *
   * Markdown links are treated as undirected links: the result includes files reachable from [root] through outgoing links,
   * and files that link back to any already discovered file. Cycles and duplicate links are ignored, so each file
   * appears in the result at most once.
   *
   * The computed dependency list is cached for every file in the set. A later call with any of those files as [root] returns
   * the same cached list until the project PSI changes.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun getDependencySet(root: PsiFile, filter: Predicate<PsiFile>): Set<VirtualFile> {
    val scope = GlobalSearchScope.getScopeRestrictedByFileTypes(
      GlobalSearchScope.projectScope(root.project),
      MarkdownFileType.INSTANCE
    )

    val result = HashSet<VirtualFile>()
    val queue = ArrayDeque<PsiFile>()

    fun enqueue(file: PsiFile) {
      if (!filter.test(file)) return
      val vFile = file.virtualFile ?: return
      if (!result.add(vFile)) return

      queue.add(file)
    }

    enqueue(root)

    while (queue.isNotEmpty()) {
      val file = queue.removeFirst()

      file.accept(object : PsiRecursiveElementWalkingVisitor() {
        override fun visitElement(element: PsiElement) {
          if (element is MarkdownLinkDestination) {
            ProgressManager.checkCanceled()
            resolveReferences(element)
              .mapNotNull { it.target }
              .mapNotNull { it.containingFile }
              .forEach { enqueue(it) }
          }
          super.visitElement(element)
        }
      })

      ReferencesSearch.search(file, scope).forEach { reference ->
        enqueue(reference.element.containingFile)
      }
    }

    return result
  }
}
