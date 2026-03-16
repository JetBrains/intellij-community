// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.test.common.kotlin

import com.intellij.psi.PsiAnnotation
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UFile

internal fun UFile.findFacade(): UClass? {
    return classes.find { it.psi is KtLightClassForFacade }
}

fun <T> T?.orFail(msg: String): T {
    return this
        ?: throw AssertionError(msg)
}

internal val UAnnotation.isNotNull: Boolean
    get() {
        return qualifiedName?.endsWith("NotNull") == true || javaPsi?.isNotNull == true
    }

internal val UAnnotation.isNullable: Boolean
    get() {
        return qualifiedName?.endsWith("Nullable") == true || javaPsi?.isNullable == true
    }

internal val UAnnotation.isNullnessAnnotation: Boolean
    get() {
        return isNotNull || isNullable
    }

internal val PsiAnnotation.isNotNull: Boolean
    get() {
        return qualifiedName?.endsWith("NotNull") == true
    }

internal val PsiAnnotation.isNullable: Boolean
    get() {
        return qualifiedName?.endsWith("Nullable") == true
    }

internal val PsiAnnotation.isNullnessAnnotation: Boolean
    get() {
        return isNotNull || isNullable
    }