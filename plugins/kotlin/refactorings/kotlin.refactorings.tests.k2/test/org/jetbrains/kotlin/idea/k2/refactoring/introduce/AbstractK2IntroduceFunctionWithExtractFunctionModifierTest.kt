// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.introduce

import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractFunctionDescriptorModifier
import org.jetbrains.kotlin.idea.k2.refactoring.extractFunction.ExtractableCodeDescriptor
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument

private val MY_COMPOSABLE_CLASS_ID = ClassId(FqName("com.example"), FqName("MyComposable"), false)

abstract class AbstractK2IntroduceFunctionWithExtractFunctionModifierTest: AbstractK2IntroduceFunctionTest() {
    override fun setUp() {
        super.setUp()
        ExtractFunctionDescriptorModifier.EP_NAME.point.registerExtension(
            MyComposeExtractFunctionDescriptorModifier(), testRootDisposable
        )
    }
}

private class MyComposeExtractFunctionDescriptorModifier : ExtractFunctionDescriptorModifier {
    private fun KtLambdaArgument.isComposable(): Boolean {
        val callExpression = parent as KtCallExpression
        val lambdaExpression = getLambdaExpression() ?: return false
        analyze(callExpression) {
            val call = callExpression.resolveToCall()?.singleFunctionCallOrNull() ?: return false
            val parameterTypeForLambda = call.argumentMapping[lambdaExpression]?.returnType ?: return false
            return parameterTypeForLambda.annotations.classIds.any { it == MY_COMPOSABLE_CLASS_ID }
        }
    }

    override fun modifyDescriptor(descriptor: ExtractableCodeDescriptor): ExtractableCodeDescriptor {
        val sourceFunction = descriptor.extractionData.targetSibling
        if (sourceFunction is KtAnnotated) {
            sourceFunction.findAnnotation(MY_COMPOSABLE_CLASS_ID)?.let {
                return descriptor.copy(
                    renderedAnnotations = descriptor.renderedAnnotations + "@${MY_COMPOSABLE_CLASS_ID.asFqNameString()}\n"
                )
            }
        }
        val outsideLambda = descriptor.extractionData.commonParent.parentOfType<KtLambdaArgument>(true) ?: return descriptor
        return if (outsideLambda.isComposable()) {
            descriptor.copy(renderedAnnotations = descriptor.renderedAnnotations + "@${MY_COMPOSABLE_CLASS_ID.asFqNameString()}\n")
        } else {
            descriptor
        }
    }
}