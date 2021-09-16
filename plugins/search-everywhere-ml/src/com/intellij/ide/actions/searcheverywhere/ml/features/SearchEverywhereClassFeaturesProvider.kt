package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

class SearchEverywhereClassFeaturesProvider : SearchEverywhereElementFeaturesProvider(ClassSearchEverywhereContributor::class.java) {
  companion object {
    private const val PRIORITY = "priority"
    private const val IS_TEST_CLASS = "isTestClass"
    private const val IS_LIBRARY = "isLibrary"
  }

  override fun getElementFeatures(element: Any, currentTime: Long, searchQuery: String, elementPriority: Int, cache: Any?): Map<String, Any> {
    val item = when (element) {
      is PsiElement -> element
      is PSIPresentationBgRendererWrapper.PsiItemWithPresentation -> element.item
      else -> return emptyMap()
    }

    val project = item.project
    val containingVirtualFile = item.containingFile.virtualFile

    return hashMapOf<String, Any>(
      PRIORITY to elementPriority,
      IS_TEST_CLASS to isTestClass(containingVirtualFile, project),
      IS_LIBRARY to isLibrary(containingVirtualFile, project),
    )
  }

  private fun isTestClass(containingFile: VirtualFile, project: Project): Boolean {
    return ReadAction.compute<Boolean, Nothing> {
      TestSourcesFilter.isTestSources(containingFile, project)
    }
  }

  private fun isLibrary(containingFile: VirtualFile, project: Project): Boolean {
    return ReadAction.compute<Boolean, Nothing> {
      ProjectFileIndex.getInstance(project).isInLibrary(containingFile)
    }
  }
}
