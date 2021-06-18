// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForScript
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.psi.*
import org.jetbrains.uast.*

class KotlinUClass(
    psi: KtLightClass,
    givenParent: UElement?
) : BaseKotlinUClass(psi, givenParent) {
    override fun buildPrimaryConstructorUMethod(ktClass: KtClassOrObject?, psi: KtLightMethod, givenParent: UElement?): UMethod {
        return KotlinConstructorUMethod(ktClass, psi, givenParent)
    }

    override fun buildSecondaryConstructorUMethod(ktClass: KtClassOrObject?, psi: KtLightMethod, givenParent: UElement?): UMethod {
        return KotlinSecondaryConstructorWithInitializersUMethod(ktClass, psi, givenParent)
    }

    companion object {
        fun create(psi: KtLightClass, containingElement: UElement?): UClass = when (psi) {
            is PsiAnonymousClass -> KotlinUAnonymousClass(psi, containingElement)
            is KtLightClassForScript -> KotlinScriptUClass(psi, containingElement)
            else -> KotlinUClass(psi, containingElement)
        }
    }

}

class KotlinUAnonymousClass(
        psi: PsiAnonymousClass,
        givenParent: UElement?
) : AbstractKotlinUClass(givenParent), UAnonymousClass, PsiAnonymousClass by psi {

    override val psi: PsiAnonymousClass = unwrap<UAnonymousClass, PsiAnonymousClass>(psi)

    override val javaPsi: PsiAnonymousClass = psi

    override val sourcePsi: KtClassOrObject? = ktClass

    override fun getOriginalElement(): PsiElement? = super<AbstractKotlinUClass>.getOriginalElement()

    override fun getSuperClass(): UClass? = super<AbstractKotlinUClass>.getSuperClass()
    override fun getFields(): Array<UField> = super<AbstractKotlinUClass>.getFields()
    override fun getMethods(): Array<UMethod> = super<AbstractKotlinUClass>.getMethods()
    override fun getInitializers(): Array<UClassInitializer> = super<AbstractKotlinUClass>.getInitializers()
    override fun getInnerClasses(): Array<UClass> = super<AbstractKotlinUClass>.getInnerClasses()

    override fun getContainingFile(): PsiFile = unwrapFakeFileForLightClass(psi.containingFile)

    override val uastAnchor by lazy {
        val ktClassOrObject = (psi.originalElement as? KtLightClass)?.kotlinOrigin as? KtObjectDeclaration ?: return@lazy null
        KotlinUIdentifier(ktClassOrObject.getObjectKeyword(), this)
        }

}

class KotlinScriptUClass(
        psi: KtLightClassForScript,
        givenParent: UElement?
) : AbstractKotlinUClass(givenParent), PsiClass by psi {
    override fun getContainingFile(): PsiFile = unwrapFakeFileForLightClass(psi.containingFile)

    override fun getNameIdentifier(): PsiIdentifier? = UastLightIdentifier(psi, psi.kotlinOrigin)

    override val uastAnchor by lazy { KotlinUIdentifier(nameIdentifier, sourcePsi?.nameIdentifier, this) }

    override val javaPsi: PsiClass = psi

    override val sourcePsi: KtClassOrObject? = psi.kotlinOrigin

    override val psi = unwrap<UClass, KtLightClassForScript>(psi)

    override fun getSuperClass(): UClass? = super.getSuperClass()

    override fun getFields(): Array<UField> = super.getFields()

    override fun getInitializers(): Array<UClassInitializer> = super.getInitializers()

    override fun getInnerClasses(): Array<UClass> =
            psi.innerClasses.mapNotNull { getLanguagePlugin().convertOpt<UClass>(it, this) }.toTypedArray()

    override fun getMethods(): Array<UMethod> = psi.methods.map(this::createUMethod).toTypedArray()

    private fun createUMethod(method: PsiMethod): UMethod {
        return if (method.isConstructor) {
            KotlinScriptConstructorUMethod(psi.script, method as KtLightMethod, this)
        }
        else {
            getLanguagePlugin().convertOpt(method, this) ?: reportConvertFailure(method)
        }
    }

    override fun getOriginalElement(): PsiElement? = psi.originalElement

    class KotlinScriptConstructorUMethod(
        script: KtScript,
        override val psi: KtLightMethod,
        givenParent: UElement?
    ) : KotlinUMethod(psi, psi.kotlinOrigin, givenParent) {
        override val uastBody: UExpression? by lz {
            val initializers = script.declarations.filterIsInstance<KtScriptInitializer>()
            KotlinLazyUBlockExpression.create(initializers, this)
        }
        override val javaPsi = psi
    }
}
