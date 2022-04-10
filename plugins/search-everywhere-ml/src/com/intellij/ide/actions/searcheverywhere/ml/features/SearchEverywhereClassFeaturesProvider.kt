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
    data.putAll(isAccessibleFromModule(element, cache?.openedFile))
    return data
  }

  private fun isDeprecated(presentation: TargetPresentation?): Boolean? {
    if (presentation == null) {
      return null
    }

    val effectType = presentation.presentableTextAttributes?.effectType ?: return false
    return effectType == EffectType.STRIKEOUT
  }

  private fun isAccessibleFromModule(element: PsiElement, openedFile: VirtualFile?): Map<String, Any> {
    return openedFile?.let {
      ReadAction.compute<Map<String, Any>, Nothing> {
        if (!element.isValid) return@compute mapOf(IS_INVALID_DATA_KEY to true)

        val elementFile = element.containingFile?.virtualFile ?: return@compute emptyMap()
        val fileIndex = ProjectRootManager.getInstance(element.project).fileIndex

        val openedFileModule = fileIndex.getModuleForFile(it)
        val elementModule = fileIndex.getModuleForFile(elementFile)

        if (openedFileModule == null || elementModule == null) return@compute emptyMap()

        return@compute mapOf(
          IS_ACCESSIBLE_FROM_MODULE to (elementModule.name in ModuleRootManager.getInstance(openedFileModule).dependencyModuleNames)
        )
      }
    } ?: emptyMap()
  }
}
