package com.intellij.searchEverywhereMl.ranking.features.statistician

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper.PsiItemWithPresentation
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiFileSystemItem

private class SearchEverywhereFileStatistician : SearchEverywhereStatistician<Any>(PsiFileSystemItem::class.java,
                                                                                   PsiItemWithPresentation::class.java) {
  override fun getValue(element: Any, location: String) = getFileWithVirtualFile(element)
    ?.virtualFile
    ?.path

  override fun getContext(element: Any): String? = getFileWithVirtualFile(element)
    ?.let { getModule(it) }
    ?.let { "$contextPrefix#${it.name}" }

  private fun getModule(file: PsiFileSystemItem): Module? {
    val fileIndex = ProjectRootManager.getInstance(file.project).fileIndex
    return fileIndex.getModuleForFile(file.virtualFile)
  }

  private fun getFileWithVirtualFile(element: Any): PsiFileSystemItem? = when (element) {
    is PsiItemWithPresentation -> (element.item as? PsiFileSystemItem)
    is PsiFileSystemItem -> element
    else -> null
  }?.takeIf { it.virtualFile != null }
}
