// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.LightClassUtil.PropertyAccessorsPsiMethods
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.uast.*
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightAccessor
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightDefaultAccessor
import org.jetbrains.uast.visitor.UastVisitor

@ApiStatus.Internal
abstract class AbstractKotlinUClass(
    givenParent: UElement?
) : KotlinAbstractUElement(givenParent), UClass, UAnchorOwner {

    private val uastDeclarationsPart = UastLazyPart<List<UDeclaration>>()
    private val delegateExpressionsPart = UastLazyPart<List<UExpression>>()
    private val uAnnotationsPart = UastLazyPart<List<UAnnotation>>()

    override val uastDeclarations: List<UDeclaration>
        get() = uastDeclarationsPart.getOrBuild {
            mutableListOf<UDeclaration>().apply {
                addAll(fields)
                addAll(initializers)
                addAll(methods)
                addAll(innerClasses)
            }
        }

    protected open val ktClass: KtClassOrObject?
        get() = (psi as? KtLightClass)?.kotlinOrigin

    override val uastSuperTypes: List<UTypeReferenceExpression>
        get() = ktClass?.superTypeListEntries.orEmpty().mapNotNull { it.typeReference }.map {
            KotlinUTypeReferenceExpression(it, this)
        }

    private val delegateExpressions: List<UExpression>
        get() = delegateExpressionsPart.getOrBuild {
            ktClass?.superTypeListEntries.orEmpty()
                .filterIsInstance<KtDelegatedSuperTypeEntry>()
                .map { KotlinSupertypeDelegationUExpression(it, this) }
        }

    protected fun computeMethods(): Array<UMethod> {
        val hasPrimaryConstructor = ktClass?.hasPrimaryConstructor() ?: false
        var secondaryConstructorsCount = 0

        fun createUMethod(psiMethod: PsiMethod): UMethod? {
            return if (psiMethod is KtLightMethod && psiMethod.isConstructor) {
                if (!hasPrimaryConstructor && secondaryConstructorsCount++ == 0)
                    KotlinSecondaryConstructorWithInitializersUMethod(ktClass, psiMethod, this)
                else
                    KotlinConstructorUMethod(ktClass, psiMethod, this)
            } else {
                languagePlugin?.convertOpt(psiMethod, this)
            }
        }

        fun isDelegatedMethod(psiMethod: PsiMethod) = psiMethod is KtLightMethod && psiMethod.isDelegated

        val result = ArrayList<UMethod>(javaPsi.methods.size)
        val handledKtDeclarations = mutableSetOf<PsiElement>()

        for (lightMethod in javaPsi.methods) {
            if (isDelegatedMethod(lightMethod)) continue
            val uMethod = createUMethod(lightMethod) ?: continue
            result.add(uMethod)

            // Ensure we pick the main Kotlin origin, not the auxiliary one
            val kotlinOrigin = (lightMethod as? KtLightMethod)?.kotlinOrigin ?: uMethod.sourcePsi
            // Property accessors' Kotlin origin is [KtProperty].
            // Seeing one accessor and skipping the containing [KtProperty] may cause to miss
            // the other accessor that could be potentially deprecated-hidden.
            if (kotlinOrigin is KtProperty) continue
            handledKtDeclarations.addIfNotNull(kotlinOrigin)
        }

        val ktDeclarations: List<KtDeclaration> = run ktDeclarations@{
            ktClass?.let { return@ktDeclarations it.declarations }
            (javaPsi as? KtLightClassForFacade)?.let { facade ->
                return@ktDeclarations facade.files.flatMap { file -> file.declarations }
            }
            emptyList()
        }

        fun convert(psiElement: PsiElement) =
            baseResolveProviderService.baseKotlinConverter.convertDeclaration(psiElement, this, arrayOf(UElement::class.java))

        ktDeclarations.asSequence()
            .filterNot { handledKtDeclarations.contains(it) }
            .flatMapTo(result) { ktDeclaration ->
                // [KtDeclaration] that doesn't have a corresponding LC element for some reason.
                when (ktDeclaration) {
                    is KtProperty -> {
                        // properties that are deprecated-hidden (property itself or accessors)
                        val (maybeFakeGetter, maybeFakeSetter) =
                            LightClassUtil.getLightClassPropertyMethods(ktDeclaration).fakeAccessors(ktDeclaration)
                                ?: return@flatMapTo emptyList()
                        listOfNotNull(
                            maybeFakeGetter?.let { convert(it) as? UMethod },
                            maybeFakeSetter?.let { convert(it) as? UMethod },
                        )
                    }
                    else -> {
                        // functions that are deprecated-hidden, `inline` w/ `reified` parameter, etc.
                        listOfNotNull(
                            convert(ktDeclaration) as? UMethod
                        )
                    }
                }
            }

        return result.toTypedArray()
    }

    private fun PropertyAccessorsPsiMethods.fakeAccessors(property: KtProperty): Pair<PsiMethod?, PsiMethod?>? {
        val (needsFakeGetter, needsFakeSetter) = needsFakeAccessors(property)
        if (!needsFakeGetter && !needsFakeSetter) return null
        val ktLightClass = getContainingLightClass(property) ?: return null
        val maybeFakeGetter = if (needsFakeGetter) {
            property.getter?.let { UastFakeSourceLightAccessor(it, ktLightClass) }
                ?: UastFakeSourceLightDefaultAccessor(property, ktLightClass, isSetter = false)
        } else null
        val maybeFakeSetter = if (needsFakeSetter) {
            property.setter?.let { UastFakeSourceLightAccessor(it, ktLightClass) }
                ?: UastFakeSourceLightDefaultAccessor(property, ktLightClass, isSetter = true)
        } else null
        return maybeFakeGetter to maybeFakeSetter
    }

    private fun PropertyAccessorsPsiMethods.needsFakeAccessors(property: KtProperty): Pair<Boolean, Boolean> {
        if (property.annotationEntries.isEmpty()) return false to false
        var (needsFakeGetter, needsFakeSetter) = false to false
        for (entry in property.annotationEntries) {
            if (baseResolveProviderService.qualifiedAnnotationName(entry)?.endsWith("Deprecated") != true) {
                continue
            }
            // Instead of checking attribute value for deprecation level, use if LC generates accessors or not.
            when (entry.useSiteTarget?.getAnnotationUseSiteTarget()) {
                AnnotationUseSiteTarget.PROPERTY_GETTER -> {
                    needsFakeGetter = getter == null
                }
                AnnotationUseSiteTarget.PROPERTY_SETTER -> {
                    needsFakeSetter = property.isVar && setter == null
                }
                null -> {
                    needsFakeGetter = getter == null
                    needsFakeSetter = property.isVar && setter == null
                }
                else -> {}
            }
        }
        return needsFakeGetter to needsFakeSetter
    }

    override fun accept(visitor: UastVisitor) {
        if (visitor.visitClass(this)) return
        delegateExpressions.acceptList(visitor)
        uAnnotations.acceptList(visitor)
        uastDeclarations.acceptList(visitor)
        visitor.afterVisitClass(this)
    }

    override val uAnnotations: List<UAnnotation>
        get() = uAnnotationsPart.getOrBuild {
            (sourcePsi as? KtModifierListOwner)?.annotationEntries.orEmpty().map {
                baseResolveProviderService.baseKotlinConverter.convertAnnotation(it, this)
            }
        }

    override fun equals(other: Any?) = other is AbstractKotlinUClass && psi == other.psi
    override fun hashCode() = psi.hashCode()
}
