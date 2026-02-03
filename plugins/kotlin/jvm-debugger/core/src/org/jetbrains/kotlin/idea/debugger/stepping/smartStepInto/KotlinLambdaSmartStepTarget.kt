// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.engine.MethodFilter
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer
import com.intellij.util.Range
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.psi.KtFunction
import javax.swing.Icon

class KotlinLambdaSmartStepTarget(
    highlightElement: KtFunction,
    element: PsiElement,
    lines: Range<Int>,
    private val lambdaInfo: KotlinLambdaInfo
) : KotlinSmartStepTarget(lambdaInfo.getLabel(), highlightElement, true, lines) {
    private val elementPtr = element.createSmartPointer()

    override fun createMethodFilter(): MethodFilter {
        val lambdaMethodFilter = KotlinLambdaMethodFilter(
            highlightElement as KtFunction,
            callingExpressionLines,
            lambdaInfo
        )

        if (!lambdaInfo.callerMethodInfo.isInline
            && !lambdaInfo.isSamSuspendMethod
            && Registry.get("debugger.async.smart.step.into").asBoolean()
        ) {
            val element = elementPtr.getElementInReadAction()
            return KotlinLambdaAsyncMethodFilter(
                element,
                callingExpressionLines,
                lambdaInfo,
                lambdaMethodFilter
            )
        }
        return lambdaMethodFilter
    }

    override fun getIcon(): Icon = KotlinIcons.LAMBDA
}
