// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForScript
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.uast.*

@ApiStatus.Internal
class KotlinUClass(
    psi: KtLightClass,
    givenParent: UElement?
) : AbstractKotlinUClass(givenParent), PsiClass by psi {
    private val uastAnchorPart = UastLazyPart<UIdentifier?>()

    override val ktClass = psi.kotlinOrigin

    override val javaPsi: KtLightClass = psi

    override val sourcePsi: KtClassOrObject? = ktClass

    override val psi = unwrap<UClass, PsiClass>(psi)

    override fun getSourceElement() = sourcePsi ?: this

    override fun getOriginalElement(): PsiElement? = super.getOriginalElement()

    override fun getNameIdentifier(): PsiIdentifier = UastLightIdentifier(psi, ktClass)

    override fun getContainingFile(): PsiFile = unwrapFakeFileForLightClass(psi.containingFile)

    override val uastAnchor: UIdentifier?
        get() = uastAnchorPart.getOrBuild {
            getIdentifierSourcePsi()?.let { KotlinUIdentifier(nameIdentifier, it, this) }
        }

    private fun getIdentifierSourcePsi(): PsiElement? {
        ktClass?.nameIdentifier?.let { return it }
        (ktClass as? KtObjectDeclaration)?.getObjectKeyword()?.let { return it }
        return null
    }

    override fun getInnerClasses(): Array<UClass> {
        // filter DefaultImpls to avoid processing same methods from original interface multiple times
        // filter Enum entry classes to avoid duplication with PsiEnumConstant initializer class
        return psi.innerClasses.filter {
            it.name != JvmAbi.DEFAULT_IMPLS_CLASS_NAME && !it.isEnumEntryLightClass
        }.mapNotNull {
            languagePlugin?.convertOpt<UClass>(it, this)
        }.toTypedArray()
    }

    override fun getSuperClass(): UClass? = super.getSuperClass()
    override fun getFields(): Array<UField> = super.getFields()
    override fun getInitializers(): Array<UClassInitializer> = super.getInitializers()

    override fun getMethods(): Array<UMethod> = computeMethods()

    companion object {
        fun create(psi: KtLightClass, containingElement: UElement?): UClass = when (psi) {
            is PsiAnonymousClass -> KotlinUAnonymousClass(psi, containingElement)
            is KtLightClassForScript -> KotlinScriptUClass(psi, containingElement)
            else -> KotlinUClass(psi, containingElement)
        }
    }
}
