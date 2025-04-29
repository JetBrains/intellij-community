// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.refactoring.changeSignature.ChangeInfo
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.psiUtil.allChildren

interface KotlinChangeInfoBase: ChangeInfo {
    fun isReceiverTypeChanged(): Boolean

    fun isVisibilityChanged(): Boolean

    fun getOldParameterIndex(oldParameterName: String): Int?

    val aNewReturnType: String?

    val aNewVisibility: Visibility
    val oldReceiverInfo: KotlinParameterInfo?

    var receiverParameterInfo: KotlinParameterInfo?
    var primaryPropagationTargets: Collection<PsiElement>

    override fun getNewParameters(): Array<out KotlinParameterInfo>

    fun getNewParametersSignatureWithoutParentheses(
        inheritedCallable: KtCallableDeclaration?,
        baseFunction: PsiElement,
        isInherited: Boolean
    ): String {
        val signatureParameters = newParameters.filter { it != receiverParameterInfo && !it.isContextParameter }

        val isLambda = inheritedCallable is KtFunctionLiteral
        if (isLambda && signatureParameters.size == 1 && !signatureParameters[0].requiresExplicitType(inheritedCallable)) {
            return signatureParameters[0].getDeclarationSignature(inheritedCallable, baseFunction, isInherited).text
        }

        return buildString {
            val indices = signatureParameters.indices
            val lastIndex = indices.last
            indices.forEach { index ->
                val parameter = signatureParameters[index].getDeclarationSignature(inheritedCallable, baseFunction, isInherited)
                if (index == lastIndex) {
                    append(parameter.text)
                } else {
                    val lastCommentsOrWhiteSpaces =
                        parameter.allChildren.toList().reversed().takeWhile { it is PsiComment || it is PsiWhiteSpace }
                    if (lastCommentsOrWhiteSpaces.any { it is PsiComment }) {
                        val commentsText = lastCommentsOrWhiteSpaces.reversed().joinToString(separator = "") { it.text }
                        lastCommentsOrWhiteSpaces.forEach { it.delete() }
                        append("${parameter.text},$commentsText\n")
                    } else {
                        append("${parameter.text}, ")
                    }
                }
            }
        }
    }
}