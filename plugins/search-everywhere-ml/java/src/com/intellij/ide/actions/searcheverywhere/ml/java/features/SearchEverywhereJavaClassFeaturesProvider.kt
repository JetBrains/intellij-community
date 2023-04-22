package com.intellij.ide.actions.searcheverywhere.ml.java.features

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.ml.features.FeaturesProviderCache
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereElementFeaturesProvider
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.PsiUtil

class SearchEverywhereJavaClassFeaturesProvider : SearchEverywhereElementFeaturesProvider(ClassSearchEverywhereContributor::class.java) {
  companion object {
    private val IS_INTERFACE_KEY = EventFields.Boolean("javaIsInterface")
    private val IS_ABSTRACT_KEY = EventFields.Boolean("javaIsAbstract")
    private val IS_LOCAL_OR_ANONYMOUS_KEY = EventFields.Boolean("javaIsLocalOrAnonymous")
    private val IS_INSTANTIATABLE_KEY = EventFields.Boolean("javaIsInstantiatable")
    private val IS_INNER_CLASS_KEY = EventFields.Boolean("javaIsInner")
    private val IS_PUBLIC_KEY = EventFields.Boolean("javaIsPublic")
    private val IS_PROTECTED_KEY = EventFields.Boolean("javaIsProtected")
    private val IS_PRIVATE_KEY = EventFields.Boolean("javaIsPrivate")
    private val IS_STATIC_KEY = EventFields.Boolean("javaIsStatic")
    private val NUMBER_OF_SUPERS_KEY = EventFields.Int("javaNumberOfSupers")
    private val NUMBER_OF_FIELDS = EventFields.Int("javaNumberOfFields")
    private val NUMBER_OF_METHODS = EventFields.Int("javaNumberOfMethods")
    private val NUMBER_OF_ANNOTATIONS = EventFields.Int("javaNumberOfAnnotations")

    private fun getPsiElement(element: Any) = when (element) {
      is PSIPresentationBgRendererWrapper.PsiItemWithPresentation -> element.item
      is PsiElement -> element
      else -> null
    }
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> = listOf(
    IS_INTERFACE_KEY, IS_ABSTRACT_KEY, IS_LOCAL_OR_ANONYMOUS_KEY,
    IS_INSTANTIATABLE_KEY, IS_INNER_CLASS_KEY, IS_STATIC_KEY,
    IS_PUBLIC_KEY, IS_PROTECTED_KEY, IS_PRIVATE_KEY,
    NUMBER_OF_SUPERS_KEY, NUMBER_OF_FIELDS, NUMBER_OF_METHODS,
    NUMBER_OF_ANNOTATIONS
  )


  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?): List<EventPair<*>> {
    val psiClass = (getPsiElement(element) as? PsiClass) ?: return emptyList()

    if (DumbService.isDumb(psiClass.project)) return emptyList()

    return buildList {
      add(IS_INTERFACE_KEY.with(psiClass.isInterface))
      add(IS_ABSTRACT_KEY.with(PsiUtil.isAbstractClass(psiClass)))

      add(IS_LOCAL_OR_ANONYMOUS_KEY.with(PsiUtil.isLocalOrAnonymousClass(psiClass)))

      add(IS_INSTANTIATABLE_KEY.with(PsiUtil.isInstantiatable(psiClass)))

      add(IS_INNER_CLASS_KEY.with(PsiUtil.isInnerClass(psiClass)))

      add(IS_STATIC_KEY.with(psiClass.hasModifierProperty(PsiModifier.STATIC)))

      add(IS_PUBLIC_KEY.with(psiClass.hasModifierProperty(PsiModifier.PUBLIC)))
      add(IS_PROTECTED_KEY.with(psiClass.hasModifierProperty(PsiModifier.PROTECTED)))
      add(IS_PRIVATE_KEY.with(psiClass.hasModifierProperty(PsiModifier.PRIVATE)))

      add(NUMBER_OF_SUPERS_KEY.with(psiClass.extendsListTypes.size + psiClass.implementsListTypes.size))

      val classFields = psiClass.fields
      add(NUMBER_OF_FIELDS.with(classFields.size))

      val classMethods = psiClass.methods
      add(NUMBER_OF_METHODS.with(classMethods.size))

      add(NUMBER_OF_ANNOTATIONS.with(psiClass.annotations.size))
    }
  }
}