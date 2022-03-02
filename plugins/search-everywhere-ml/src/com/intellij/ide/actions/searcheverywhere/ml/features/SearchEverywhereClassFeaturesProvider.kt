package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement

class SearchEverywhereClassFeaturesProvider : SearchEverywhereClassOrFileFeaturesProvider(ClassSearchEverywhereContributor::class.java) {
  companion object {
    private const val PRIORITY = "priority"
    private const val IS_DEPRECATED = "isDeprecated"
    private const val IS_ACCESSIBLE_FROM_MODULE = "isAccessibleFromModule"
  }

  override fun getElementFeatures(element: PsiElement,
                                  presentation: TargetPresentation?,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: Cache?): Map<String, Any> {
    val data = hashMapOf<String, Any>(
      PRIORITY to elementPriority,
    )

    ReadAction.run<Nothing> {
      (element as? PsiNamedElement)?.name?.let { elementName ->
        data.putAll(getNameMatchingFeatures(elementName, searchQuery))
      }
    }

    data.putIfValueNotNull(IS_DEPRECATED, isDeprecated(presentation))
    data.putIfValueNotNull(IS_ACCESSIBLE_FROM_MODULE, isAccessibleFromModule(element, cache?.openedFile))
    return data
  }

  private fun isDeprecated(presentation: TargetPresentation?): Boolean? {
    if (presentation == null) {
      return null
    }

    val effectType = presentation.presentableTextAttributes?.effectType ?: return false
    return effectType == EffectType.STRIKEOUT
  }

  private fun isAccessibleFromModule(element: PsiElement, openedFile: VirtualFile?): Boolean? {
    if (openedFile == null) {
      return null
    }

    val (openedFileModule, elementModule) = ReadAction.compute<Pair<com.intellij.openapi.module.Module?,
      com.intellij.openapi.module.Module?>, Nothing> {
      val elementFile = element.containingFile?.virtualFile ?: return@compute Pair(null, null)
      val fileIndex = ProjectRootManager.getInstance(element.project).fileIndex
      return@compute Pair(fileIndex.getModuleForFile(openedFile), fileIndex.getModuleForFile(elementFile))
    }

    if (openedFileModule == null || elementModule == null) {
      return null
    }

    return elementModule.name in ModuleRootManager.getInstance(openedFileModule).dependencyModuleNames
  }
}
