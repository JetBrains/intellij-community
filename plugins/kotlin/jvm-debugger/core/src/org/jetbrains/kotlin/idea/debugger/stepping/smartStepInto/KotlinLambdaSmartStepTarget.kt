// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.engine.MethodFilter
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.Range
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import javax.swing.Icon

class KotlinLambdaSmartStepTarget(
    highlightElement: KtFunction,
    declaration: KtDeclaration,
    lines: Range<Int>,
    private val lambdaInfo: KotlinLambdaInfo
) : KotlinSmartStepTarget(lambdaInfo.getLabel(), highlightElement, true, lines) {
    private val declarationPtr = declaration.createSmartPointer()

    override fun createMethodFilter(): MethodFilter {
        val lambdaMethodFilter = KotlinLambdaMethodFilter(
            highlightElement as KtFunction,
            callingExpressionLines,
            lambdaInfo
        )

        if (!lambdaInfo.isSuspend && !lambdaInfo.callerMethodInfo.isInline && Registry.get("debugger.async.smart.step.into").asBoolean()) {
            val declaration = declarationPtr.getElementInReadAction()
            return KotlinLambdaAsyncMethodFilter(
                declaration,
                callingExpressionLines,
                lambdaInfo,
                lambdaMethodFilter
            )
        }
        return lambdaMethodFilter
    }

    override fun getIcon(): Icon = KotlinIcons.LAMBDA
}
