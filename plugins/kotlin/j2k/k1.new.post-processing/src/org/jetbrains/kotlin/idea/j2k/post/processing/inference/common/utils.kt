// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.inference.common

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

fun PsiElement.getInferenceLabel(): JKElementInfoLabel? =
    prevSibling?.safeAs<PsiComment>()?.text?.asInferenceLabel()
        ?: parent?.safeAs<KtTypeProjection>()?.getInferenceLabel()

fun PsiElement.elementInfo(converterContext: ConverterContext): List<JKElementInfo>? =
    getInferenceLabel()?.let { label ->
        converterContext.elementsInfoStorage.getInfoForLabel(label)
    }


inline fun KtTypeReference.hasUnknownLabel(context: ConverterContext, isUnknownLabel: (JKTypeInfo) -> Boolean) =
    getInferenceLabel()?.let { label ->
        context.elementsInfoStorage.getInfoForLabel(label)?.any { it.safeAs<JKTypeInfo>()?.let(isUnknownLabel) == true }
    } ?: false

inline fun KtTypeElement.hasUnknownLabel(context: ConverterContext, isUnknownLabel: (JKTypeInfo) -> Boolean) =
    parent
        ?.safeAs<KtTypeReference>()
        ?.hasUnknownLabel(context, isUnknownLabel) == true
