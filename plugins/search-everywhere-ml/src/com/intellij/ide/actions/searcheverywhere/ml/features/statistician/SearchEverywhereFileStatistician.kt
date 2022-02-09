package com.intellij.ide.actions.searcheverywhere.ml.features.statistician

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper.PsiItemWithPresentation
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.statistics.StatisticsInfo

internal class SearchEverywhereFileStatistician : SearchEverywhereStatistician<Any>() {
  override fun serialize(element: Any, location: String): StatisticsInfo? {
    val file = getFile(element) ?: return null

    val context = getContext(file) ?: return null
    val value = file.virtualFile?.path ?: return null
    return StatisticsInfo(context, value)
  }

  private fun getFile(element: Any): PsiFileSystemItem? = when (element) {
    is PsiItemWithPresentation -> (element.item as? PsiFileSystemItem)
    is PsiFileSystemItem -> element
    else -> null
  }

  private fun getModule(file: PsiFileSystemItem): Module? {
    return ReadAction.compute<Module?, Nothing> {
      val fileIndex = ProjectRootManager.getInstance(file.project).fileIndex
      fileIndex.getModuleForFile(file.virtualFile)
    }
  }

  override fun getContext(element: Any): String? {
    val module = getFile(element)?.let { getModule(it) } ?: return null
    return "searchEverywhere#${module.name}"
  }
}
