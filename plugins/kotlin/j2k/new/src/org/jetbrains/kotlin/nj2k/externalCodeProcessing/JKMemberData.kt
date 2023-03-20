// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.externalCodeProcessing

import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

interface JKMemberData {
    var kotlinElementPointer: SmartPsiElementPointer<KtNamedDeclaration>?
    var isStatic: Boolean
    val fqName: FqName?
    var name: String

    val kotlinElement
        get() = kotlinElementPointer?.element

    val searchInJavaFiles: Boolean
        get() = true
    val searchInKotlinFiles: Boolean
        get() = true

    val searchingNeeded
        get() = kotlinElement?.isPrivate() != true && (searchInJavaFiles || searchInKotlinFiles)
}

interface JKMemberDataCameFromJava<J : PsiMember> : JKMemberData {
    val javaElement: J

    override val fqName
        get() = javaElement.kotlinFqName
}

interface JKFieldData : JKMemberData

data class JKFakeFieldData(
    override var isStatic: Boolean,
    override var kotlinElementPointer: SmartPsiElementPointer<KtNamedDeclaration>? = null,
    override val fqName: FqName?,
    override var name: String
) : JKFieldData {
    override val searchInJavaFiles: Boolean
        get() = false
    override val searchInKotlinFiles: Boolean
        get() = false
}

data class JKFieldDataFromJava(
    override val javaElement: PsiField,
    override var isStatic: Boolean = false,
    override var kotlinElementPointer: SmartPsiElementPointer<KtNamedDeclaration>? = null,
    override var name: String = javaElement.name
) : JKMemberDataCameFromJava<PsiField>, JKFieldData {
    override val searchInKotlinFiles: Boolean
        get() = wasRenamed

    val wasRenamed: Boolean
        get() = javaElement.name != name
}

interface JKMethodData : JKMemberDataCameFromJava<PsiMethod> {
    var usedAsAccessorOfProperty: JKFieldData?
}

data class JKPhysicalMethodData(
    override val javaElement: PsiMethod,
    override var isStatic: Boolean = false,
    override var kotlinElementPointer: SmartPsiElementPointer<KtNamedDeclaration>? = null,
    override var usedAsAccessorOfProperty: JKFieldData? = null
) : JKMethodData {
    override var name: String = javaElement.name
}

data class JKLightMethodData(
    override val javaElement: PsiMethod,
    override var isStatic: Boolean = false,
    override var kotlinElementPointer: SmartPsiElementPointer<KtNamedDeclaration>? = null,
    override var usedAsAccessorOfProperty: JKFieldData? = null
) : JKMethodData {
    override var name: String = javaElement.name
}
