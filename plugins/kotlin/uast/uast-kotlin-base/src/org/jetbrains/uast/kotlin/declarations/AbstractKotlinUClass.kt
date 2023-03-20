// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDelegatedSuperTypeEntry
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.uast.*
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.visitor.UastVisitor

@ApiStatus.Internal
abstract class AbstractKotlinUClass(
    givenParent: UElement?
) : KotlinAbstractUElement(givenParent), UClass, UAnchorOwner {

    override val uastDeclarations by lz {
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

    private val delegateExpressions: List<UExpression> by lz {
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
            handledKtDeclarations.addIfNotNull(kotlinOrigin)
        }

        val ktDeclarations: List<KtDeclaration> = run ktDeclarations@{
            ktClass?.let { return@ktDeclarations it.declarations }
            (javaPsi as? KtLightClassForFacade)?.let { facade ->
                return@ktDeclarations facade.files.flatMap { file -> file.declarations }
            }
            emptyList()
        }

        ktDeclarations.asSequence()
            .filterNot { handledKtDeclarations.contains(it) }
            .mapNotNullTo(result) {
                baseResolveProviderService.baseKotlinConverter.convertDeclaration(it, this, arrayOf(UElement::class.java)) as? UMethod
            }

        return result.toTypedArray()
    }

    override fun accept(visitor: UastVisitor) {
        if (visitor.visitClass(this)) return
        delegateExpressions.acceptList(visitor)
        uAnnotations.acceptList(visitor)
        uastDeclarations.acceptList(visitor)
        visitor.afterVisitClass(this)
    }

    override val uAnnotations: List<UAnnotation> by lz {
        (sourcePsi as? KtModifierListOwner)?.annotationEntries.orEmpty().map {
            baseResolveProviderService.baseKotlinConverter.convertAnnotation(it, this)
        }
    }

    override fun equals(other: Any?) = other is AbstractKotlinUClass && psi == other.psi
    override fun hashCode() = psi.hashCode()
}
