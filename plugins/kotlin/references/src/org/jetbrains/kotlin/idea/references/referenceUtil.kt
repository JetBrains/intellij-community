// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.references

import com.intellij.psi.PsiReference
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtImportAlias
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression


@get:ApiStatus.ScheduledForRemoval
@get:Deprecated("For binary compatibility with AS, see KT-42061", replaceWith = ReplaceWith("mainReference"))
@get:JvmName("getMainReference")
val KtSimpleNameExpression.mainReferenceCompat: KtSimpleNameReference
    get() = mainReference

@get:ApiStatus.ScheduledForRemoval
@get:Deprecated("For binary compatibility with AS, see KT-42061", replaceWith = ReplaceWith("mainReference"))
@get:JvmName("getMainReference")
val KtReferenceExpression.mainReferenceCompat: KtReference
    get() = mainReference

@get:ApiStatus.ScheduledForRemoval
@get:Deprecated("For binary compatibility with AS, see KT-42061", replaceWith = ReplaceWith("mainReference"))
@get:JvmName("getMainReference")
val KtElement.mainReferenceCompat: KtReference?
    get() = mainReference

@ApiStatus.ScheduledForRemoval
@Deprecated("For binary compatibility with K1 plugins")
fun PsiReference.getImportAlias(): KtImportAlias? {
    return (this as? KtSimpleNameReference)?.getImportAlias()
}
