// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.lang.jvm.JvmModifier.STATIC
import com.intellij.psi.PsiModifier
import org.jetbrains.kotlin.codegen.kotlinType
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.nj2k.NewJ2kConverterContext
import org.jetbrains.kotlin.nj2k.RecursiveApplicableConversionBase
import org.jetbrains.kotlin.nj2k.nullIfStubExpression
import org.jetbrains.kotlin.nj2k.qualified
import org.jetbrains.kotlin.nj2k.symbols.*
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.types.*
import org.jetbrains.kotlin.psi.KtObjectDeclaration

import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

private const val RECEIVER_NAME: String = "obj"

internal class MethodReferenceToLambdaConversion(context: NewJ2kConverterContext) : RecursiveApplicableConversionBase(context) {
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKMethodReferenceExpression) return recurse(element)

        val symbol = element.identifier
        val parameterTypesByFunctionalInterface = element
            .functionalType
            .type
            .safeAs<JKClassType>()
            ?.singleFunctionParameterTypes()

        val receiverParameter = createReceiverParameter(element, parameterTypesByFunctionalInterface)
        val explicitParameterTypesByFunctionalInterface =
            if (receiverParameter != null) parameterTypesByFunctionalInterface?.drop(1)
            else parameterTypesByFunctionalInterface

        val parameters =
            if (symbol is JKMethodSymbol) {
                val parameterNames = symbol.parameterNames ?: return recurse(element)
                val parameterTypes = explicitParameterTypesByFunctionalInterface ?: symbol.parameterTypes ?: return recurse(element)
                parameterNames.zip(parameterTypes) { name, type ->
                    JKParameter(JKTypeElement(type), JKNameIdentifier(name))
                }
            } else emptyList()

        val arguments = parameters.map { parameter ->
            val parameterSymbol = symbolProvider.provideUniverseSymbol(parameter)
            JKArgumentImpl(JKFieldAccessExpression(parameterSymbol))
        }

        val callExpression = when (symbol) {
            is JKMethodSymbol -> JKCallExpressionImpl(symbol, JKArgumentList(arguments))
            is JKClassSymbol -> JKNewExpression(symbol, JKArgumentList())
            else -> return recurse(element)
        }

        val qualifier = when {
            receiverParameter != null -> JKFieldAccessExpression(symbolProvider.provideUniverseSymbol(receiverParameter))
            element.isConstructorCall -> element.qualifier.safeAs<JKQualifiedExpression>()?.let { it::receiver.detached() }
            else -> element::qualifier.detached().nullIfStubExpression()
        }

        val returnType = when (symbol) {
            is JKMethodSymbol -> symbol.returnType ?: JKNoType
            is JKClassSymbol -> JKClassType(symbol)
            else -> return recurse(element)
        }

        val lambda = JKLambdaExpression(
            statement = JKExpressionStatement(callExpression.qualified(qualifier)),
            parameters = listOfNotNull(receiverParameter) + parameters,
            functionalType = element::functionalType.detached(),
            returnType = JKTypeElement(returnType)
        )

        return recurse(lambda)
    }

    private fun createReceiverParameter(
        methodReference: JKMethodReferenceExpression,
        parameterTypesByFunctionalInterface: List<JKType>?
    ): JKParameter? {
        val methodSymbol = methodReference.identifier.safeAs<JKMethodSymbol>()
        if (methodSymbol?.isStatic != false || methodReference.isConstructorCall) return null

        val type = when (val qualifierExpression = methodReference.qualifier.nullIfStubExpression()) {
            is JKTypeQualifierExpression -> qualifierExpression.type
            is JKClassAccessExpression -> JKClassType(qualifierExpression.identifier)
            else -> return null
        }

        return JKParameter(
            JKTypeElement(parameterTypesByFunctionalInterface?.firstOrNull() ?: type),
            JKNameIdentifier(RECEIVER_NAME)
        )
    }

    private fun JKType.substituteTypeParameters(classType: JKClassType): JKType = applyRecursive { type ->
        if (type is JKTypeParameterType && type.identifier.declaredIn == classType.classReference) {
            classType.parameters.getOrNull(type.identifier.index)
        } else null
    }

    private fun JKClassType.singleFunctionParameterTypes(): List<JKType>? {
        return when (val reference = classReference) {
            is JKMultiverseClassSymbol -> {
                val method = reference.target.methods.firstOrNull { !it.hasModifier(STATIC) }
                val parameters = method?.parameterList?.parameters
                parameters?.map { typeFactory.fromPsiType(it.type).substituteTypeParameters(this) }
            }

            is JKMultiverseKtClassSymbol -> {
                val function = reference.target.body?.functions?.singleOrNull()
                function?.valueParameters?.map { param ->
                    val type = param.kotlinType(param.analyze()) ?: return null
                    typeFactory.fromKotlinType(type).substituteTypeParameters(this)
                }
            }

            is JKUniverseClassSymbol -> {
                val method = reference.target.classBody.declarations.firstIsInstanceOrNull<JKMethod>()
                method?.parameters?.map { it.type.type.substituteTypeParameters(this) }
            }

            else -> null
        }
    }

    private val JKMethodSymbol.isStatic: Boolean
        get() = when (this) {
            is JKMultiverseFunctionSymbol -> target.parent is KtObjectDeclaration
            is JKMultiverseMethodSymbol -> target.hasModifierProperty(PsiModifier.STATIC)
            is JKUniverseMethodSymbol -> target.parent?.parent?.safeAs<JKClass>()?.classKind == JKClass.ClassKind.COMPANION
            is JKUnresolvedMethod -> false
            is KtClassImplicitConstructorSymbol -> false
        }

    private val JKMethodSymbol.parameterNames: List<String>?
        get() = when (this) {
            is JKMultiverseFunctionSymbol -> target.valueParameters.map { it.name ?: return null }
            is JKMultiverseMethodSymbol -> target.parameters.map { it.name ?: return null }
            is JKUniverseMethodSymbol -> target.parameters.map { it.name.value }
            is JKUnresolvedMethod -> null
            is KtClassImplicitConstructorSymbol -> null
        }
}
