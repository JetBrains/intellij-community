package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
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
    internal val IS_DEPRECATED = EventFields.Boolean("isDeprecated")
    internal val IS_ACCESSIBLE_FROM_MODULE = EventFields.Boolean("isAccessibleFromModule")
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    val features = arrayListOf<EventField<*>>(IS_DEPRECATED, IS_ACCESSIBLE_FROM_MODULE)
    features.addAll(super.getFeaturesDeclarations())
    return features
  }

  override fun getElementFeatures(element: PsiElement,
                                  presentation: TargetPresentation?,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: Cache?): List<EventPair<*>> {
    val data = arrayListOf<EventPair<*>>()

    ReadAction.run<Nothing> {
      (element as? PsiNamedElement)?.name?.let { elementName ->
        data.addAll(getNameMatchingFeatures(elementName, searchQuery))
      }
    }

    data.putIfValueNotNull(IS_DEPRECATED, isDeprecated(presentation))
    data.addAll(isAccessibleFromModule(element, cache?.openedFile))
    return data
  }

  private fun isDeprecated(presentation: TargetPresentation?): Boolean? {
    if (presentation == null) {
      return null
    }

    val effectType = presentation.presentableTextAttributes?.effectType ?: return false
    return effectType == EffectType.STRIKEOUT
  }

  private fun isAccessibleFromModule(element: PsiElement, openedFile: VirtualFile?): List<EventPair<*>> {
    return openedFile?.let {
      ReadAction.compute<List<EventPair<*>>, Nothing> {
        if (!element.isValid) return@compute arrayListOf(IS_INVALID_DATA_KEY.with(true))

        val elementFile = element.containingFile?.virtualFile ?: return@compute emptyList()
        val fileIndex = ProjectRootManager.getInstance(element.project).fileIndex

        val openedFileModule = fileIndex.getModuleForFile(it)
        val elementModule = fileIndex.getModuleForFile(elementFile)

        if (openedFileModule == null || elementModule == null) return@compute emptyList()

        return@compute arrayListOf(
          IS_ACCESSIBLE_FROM_MODULE.with(elementModule.name in ModuleRootManager.getInstance(openedFileModule).dependencyModuleNames)
        )
      }
    } ?: emptyList()
  }
}
