package com.intellij.ide.actions.searcheverywhere.ml.kotlin.features

import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.ml.features.FeaturesProviderCache
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereElementFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywherePsiElementFeaturesProviderUtils
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.psi.psiUtil.isProtected
import org.jetbrains.kotlin.psi.psiUtil.isPublic


class SearchEverywhereKotlinClassFeaturesProvider : SearchEverywhereElementFeaturesProvider(ClassSearchEverywhereContributor::class.java) {
  companion object {
    private val IS_INTERFACE_KEY = EventFields.Boolean("kotlinIsInterface")
    private val IS_ABSTRACT_KEY = EventFields.Boolean("kotlinIsAbstract")
    private val IS_LOCAL_OR_ANONYMOUS_KEY = EventFields.Boolean("kotlinIsLocalOrAnon")

    private val IS_PUBLIC_KEY = EventFields.Boolean("kotlinIsPublic")
    private val IS_PROTECTED_KEY = EventFields.Boolean("kotlinIsProtected")
    private val IS_PRIVATE_KEY = EventFields.Boolean("kotlinIsPrivate")
    private val IS_INTERNAL_CLASS_KEY = EventFields.Boolean("kotlinIsInternal")

    private val IS_INNER_CLASS_KEY = EventFields.Boolean("kotlinIsInner")
    private val IS_OPEN_CLASS_KEY = EventFields.Boolean("kotlinIsOpen")
    private val IS_ENUM_CLASS_KEY = EventFields.Boolean("kotlinIsEnum")
    private val IS_SEALED_CLASS_KEY = EventFields.Boolean("kotlinIsSealed")
    private val IS_INLINE_CLASS_KEY = EventFields.Boolean("kotlinIsInline")
    private val IS_VALUE_CLASS_KEY = EventFields.Boolean("kotlinIsValue")
    private val IS_OBJECT_CLASS_KEY = EventFields.Boolean("kotlinIsObject")
    private val IS_DATA_CLASS_KEY = EventFields.Boolean("kotlinIsData")
    private val IS_SAM_CLASS_KEY = EventFields.Boolean("kotlinIsSAM")

    private val NUMBER_OF_SUPER_CLASSES_KEY = EventFields.Int("kotlinNumberOfSupers")
    private val NUMBER_OF_DECLARATIONS = EventFields.Int("kotlinNumberOfDeclarations")
    private val NUMBER_OF_PROPERTIES = EventFields.Int("kotlinNumberOfProperties")
    private val NUMBER_OF_METHODS = EventFields.Int("kotlinNumberOfMethods")
    private val NUMBER_OF_OVERRIDDEN_DECLARATIONS = EventFields.Int("kotlinNumberOfOverridden")
    private val NUMBER_OF_CONTEXT_RECEIVERS = EventFields.Int("kotlinNumberOfReceivers")
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> = listOf(
    IS_INTERFACE_KEY, IS_ABSTRACT_KEY, IS_LOCAL_OR_ANONYMOUS_KEY,
    IS_PUBLIC_KEY, IS_PROTECTED_KEY, IS_PRIVATE_KEY, IS_INTERNAL_CLASS_KEY,
    IS_INNER_CLASS_KEY, IS_OPEN_CLASS_KEY, IS_ENUM_CLASS_KEY, IS_SEALED_CLASS_KEY, IS_INLINE_CLASS_KEY,
    IS_VALUE_CLASS_KEY, IS_OBJECT_CLASS_KEY, IS_DATA_CLASS_KEY, IS_SAM_CLASS_KEY,
    NUMBER_OF_SUPER_CLASSES_KEY, NUMBER_OF_DECLARATIONS, NUMBER_OF_PROPERTIES,
    NUMBER_OF_METHODS, NUMBER_OF_OVERRIDDEN_DECLARATIONS, NUMBER_OF_CONTEXT_RECEIVERS
  )

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?): List<EventPair<*>> {
    val psiElement = SearchEverywherePsiElementFeaturesProviderUtils.getPsiElement(element) ?: return emptyList()
    val psiClass = when (psiElement) {
                     is KtClassOrObject -> psiElement
                     is PsiClass -> psiElement.unwrapped
                     else -> null
                   } as? KtClassOrObject ?: return emptyList()

    return buildList {
      runReadAction {
        if (psiClass is KtClass) {
          add(IS_INTERFACE_KEY.with(psiClass.isInterface()))
          add(IS_ABSTRACT_KEY.with(psiClass.isAbstract()))
          add(IS_INNER_CLASS_KEY.with(psiClass.isInner()))
          add(IS_OPEN_CLASS_KEY.with(psiClass.hasModifier(KtTokens.OPEN_KEYWORD)))
          add(IS_ENUM_CLASS_KEY.with(psiClass.isEnum()))
          add(IS_SEALED_CLASS_KEY.with(psiClass.isSealed()))
          add(IS_INLINE_CLASS_KEY.with(psiClass.isInline()))
          add(IS_VALUE_CLASS_KEY.with(psiClass.isValue()))
          add(IS_SAM_CLASS_KEY.with(psiClass.isInterface() && psiClass.hasModifier(KtTokens.FUN_KEYWORD)))
        }

        add(IS_INTERNAL_CLASS_KEY.with(psiClass.hasModifier(KtTokens.INTERNAL_KEYWORD)))

        add(IS_LOCAL_OR_ANONYMOUS_KEY.with(psiClass.isLocal || psiClass.name == null))

        add(IS_OBJECT_CLASS_KEY.with(psiClass is KtObjectDeclaration))

        add(IS_PUBLIC_KEY.with(psiClass.isPublic))
        add(IS_PROTECTED_KEY.with(psiClass.isProtected()))
        add(IS_PRIVATE_KEY.with(psiClass.isPrivate()))

        add(IS_DATA_CLASS_KEY.with(psiClass.isData()))

        add(NUMBER_OF_SUPER_CLASSES_KEY.with(psiClass.superTypeListEntries.size))

        val declarations = psiClass.getStructureDeclarations()
        add(NUMBER_OF_DECLARATIONS.with(declarations.size))
        add(NUMBER_OF_METHODS.with(declarations.filterIsInstance<KtFunction>().size))
        add(NUMBER_OF_PROPERTIES.with(declarations.filterIsInstance<KtProperty>().size))
        add(NUMBER_OF_OVERRIDDEN_DECLARATIONS.with(declarations.count { it.hasModifier(KtTokens.OVERRIDE_KEYWORD) }))
        add(NUMBER_OF_CONTEXT_RECEIVERS.with(psiClass.contextReceivers.size))
      }
    }
  }

  private fun KtClassOrObject.getStructureDeclarations(): List<KtDeclaration> = buildList {
    primaryConstructor?.let { add(it) }
    primaryConstructorParameters.filterTo(this) { it.hasValOrVar() }
    addAll(declarations)
  }
}
