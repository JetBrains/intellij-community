// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.fe10.binding

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.components.KtConstantEvaluationMode
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.fir.fe10.KtSymbolBasedClassDescriptor
import org.jetbrains.kotlin.idea.fir.fe10.toConstantValue
import org.jetbrains.kotlin.idea.fir.fe10.toDeclarationDescriptor
import org.jetbrains.kotlin.idea.fir.fe10.toKotlinType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.constants.CompileTimeConstant
import org.jetbrains.kotlin.resolve.constants.TypedCompileTimeConstant
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class MiscBindingContextValueProvider(bindingContext: KtSymbolBasedBindingContext) {
    private val context = bindingContext.context

    init {
        bindingContext.registerGetterByKey(BindingContext.TYPE, this::getType)
        bindingContext.registerGetterByKey(BindingContext.THIS_TYPE_FOR_SUPER_EXPRESSION, this::getThisTypeForSuperExpression)
        bindingContext.registerGetterByKey(BindingContext.COMPILE_TIME_VALUE, this::getCompileTimeValue)
        bindingContext.registerGetterByKey(BindingContext.DATA_CLASS_COPY_FUNCTION, this::getDataClassCopyFunction)
        bindingContext.registerGetterByKey(BindingContext.EXPECTED_EXPRESSION_TYPE, this::getExpectedExpressionType)
        bindingContext.registerGetterByKey(BindingContext.LABEL_TARGET, this::getLabelTarget)
    }

    private fun getType(ktTypeReference: KtTypeReference): KotlinType {
        return context.withAnalysisSession {
            ktTypeReference.getKtType()
        }.toKotlinType(context)
    }

    private fun getThisTypeForSuperExpression(superExpression: KtSuperExpression): KotlinType =
        context.withAnalysisSession { superExpression.getKtType() }?.toKotlinType(context) ?: context.errorHandling()

    private fun getCompileTimeValue(key: KtExpression): CompileTimeConstant<*>? {
        val ktConstantValue = context.withAnalysisSession { key.evaluate(KtConstantEvaluationMode.CONSTANT_LIKE_EXPRESSION_EVALUATION) }
            ?: return null
        val constantValue = ktConstantValue.toConstantValue()
        // only usesNonConstValAsConstant seems to be used in IDE code
        val parameters = CompileTimeConstant.Parameters(
            canBeUsedInAnnotation = false,
            isPure = false,
            isUnsignedNumberLiteral = false,
            isUnsignedLongNumberLiteral = false,
            usesVariableAsConstant = false,
            usesNonConstValAsConstant = false,
            isConvertableConstVal = false
        )
        return TypedCompileTimeConstant(constantValue, context.moduleDescriptor, parameters)
    }

    // copy function could be only generated, and DATA_CLASS_COPY_FUNCTION works only for function from sources
    private fun getDataClassCopyFunction(classDescriptor: ClassDescriptor): FunctionDescriptor? {
        if (classDescriptor !is KtSymbolBasedClassDescriptor) return null
        val classSymbol = classDescriptor.ktSymbol

        val copyFunction: KtCallableSymbol? = context.withAnalysisSession {
            classSymbol.getMemberScope().getCallableSymbols { it == Name.identifier("copy") }.singleOrNull {
                it.origin == KtSymbolOrigin.SOURCE_MEMBER_GENERATED
            }
        }

        return copyFunction.safeAs<KtFunctionLikeSymbol>()?.toDeclarationDescriptor(context)
    }

    private fun getExpectedExpressionType(ktExpression: KtExpression): KotlinType? =
        context.withAnalysisSession {
            ktExpression.getExpectedType()
        }?.toKotlinType(context)

    private fun getLabelTarget(ktExpression: KtReferenceExpression): PsiElement? {
        val potentiallyParentReturn = ktExpression.parent.parent
        if (potentiallyParentReturn is KtReturnExpression) {
            return context.withAnalysisSession {
                potentiallyParentReturn.getReturnTargetSymbol()?.psi
            }
        }

        // other cases
        return context.incorrectImplementation { null }
    }
}
