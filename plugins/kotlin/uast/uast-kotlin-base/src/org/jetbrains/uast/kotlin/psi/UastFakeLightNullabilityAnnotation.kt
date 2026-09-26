// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiAnnotationParameterList
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaCodeReferenceElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.kotlin.asJava.classes.cannotModify
import org.jetbrains.kotlin.asJava.elements.KtLightAbstractAnnotation
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.uast.analysis.UNullability

@ApiStatus.Internal
class UastFakeLightNullabilityAnnotation(
    private val nullability: UNullability,
    parent: PsiElement
) : KtLightAbstractAnnotation(parent) {

    override val kotlinOrigin: KtCallElement?
        get() = null

    override fun findAttributeValue(attributeName: String?): PsiAnnotationMemberValue? = null

    override fun findDeclaredAttributeValue(attributeName: String?): PsiAnnotationMemberValue? = null

    override fun getNameReferenceElement(): PsiJavaCodeReferenceElement? = null

    override fun getParameterList(): PsiAnnotationParameterList = KtLightEmptyAnnotationParameterList(this)

    override fun getQualifiedName(): String? =
        when (nullability) {
            UNullability.NOT_NULL -> NotNull::class.qualifiedName
            UNullability.NULLABLE -> Nullable::class.qualifiedName
            else -> null
        }

    override fun toString(): String = "@$qualifiedName"

    override fun <T : PsiAnnotationMemberValue?> setDeclaredAttributeValue(attributeName: String?, value: T?): T = cannotModify()
}