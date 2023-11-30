// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiIdentifier
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.uast.*

@ApiStatus.Internal
class KotlinUAnonymousClass(
    psi: PsiAnonymousClass,
    givenParent: UElement?
) : AbstractKotlinUClass(givenParent), UAnonymousClass, PsiAnonymousClass by psi {

    private val uastAnchorPart = UastLazyPart<UIdentifier?>()

    override val psi: PsiAnonymousClass = unwrap<UAnonymousClass, PsiAnonymousClass>(psi)

    override val javaPsi: PsiAnonymousClass = psi

    override val sourcePsi: KtClassOrObject? = ktClass

    override fun getSourceElement() = sourcePsi ?: this

    override fun getOriginalElement(): PsiElement? = super<AbstractKotlinUClass>.getOriginalElement()

    override fun getNameIdentifier(): PsiIdentifier? {
        return if (sourcePsi is KtEnumEntry)
            UastLightIdentifier(psi, ktClass)
        else
            psi.nameIdentifier
    }

    override fun getSuperClass(): UClass? = super<AbstractKotlinUClass>.getSuperClass()
    override fun getFields(): Array<UField> = super<AbstractKotlinUClass>.getFields()
    override fun getMethods(): Array<UMethod> =
        // TODO: better not discriminate enum entry (but non-trivial to preserve parent tree consistency)
        if (sourcePsi is KtEnumEntry)
            super<AbstractKotlinUClass>.getMethods()
        else
            computeMethods()

    override fun getInitializers(): Array<UClassInitializer> = super<AbstractKotlinUClass>.getInitializers()
    override fun getInnerClasses(): Array<UClass> = super<AbstractKotlinUClass>.getInnerClasses()

    override fun getContainingFile(): PsiFile = unwrapFakeFileForLightClass(psi.containingFile)

    override val uastAnchor: UIdentifier?
        get() = uastAnchorPart.getOrBuild {
            val ktClassOrObject = (psi.originalElement as? KtLightClass)?.kotlinOrigin ?: return@getOrBuild null
            val sourcePsi = when (ktClassOrObject) {
                is KtObjectDeclaration -> ktClassOrObject.getObjectKeyword()
                is KtEnumEntry -> ktClassOrObject.nameIdentifier
                else -> null
            } ?: return@getOrBuild null

            KotlinUIdentifier(sourcePsi, this)
        }
}
