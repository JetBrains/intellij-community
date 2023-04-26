/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.uast.test.common.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.uast.*

fun UFile.resolvableWithTargets(
    renderLightElementDifferently: (PsiElement?) -> String = { it.toString() },
) = object : IndentedPrintingVisitor(KtBlockExpression::class) {
    override fun render(element: PsiElement) =
        UastFacade.convertToAlternatives<UExpression>(element, arrayOf(UReferenceExpression::class.java, UCallExpression::class.java))
            .filter {
                when (it) {
                    is UCallExpression -> (it.sourcePsi as? KtCallElement)?.calleeExpression !is KtSimpleNameExpression
                    else -> true
                }
            }.takeIf { it.any() }
            ?.joinTo(StringBuilder(), "\n") { ref ->
                StringBuilder().apply {
                    val parent = ref.uastParent
                    append(parent?.asLogString())
                    if (parent is UCallExpression) {
                        append("(resolves to ${renderLightElementDifferently(parent.resolve())})")
                    }
                    append(" -> ")
                    append(ref.asLogString())
                    append(" -> ")
                    append(renderLightElementDifferently((ref as UResolvable).resolve()))
                    append(": ")
                    append(
                        when (ref) {
                            is UReferenceExpression -> ref.resolvedName
                            is UCallExpression -> ""
                            else -> "<none>"
                        }
                    )
                }
            }
}.visitUFileAndGetResult(this)
