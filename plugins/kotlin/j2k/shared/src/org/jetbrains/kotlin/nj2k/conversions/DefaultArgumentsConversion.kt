// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.conversions

import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.Nullability
import org.jetbrains.kotlin.j2k.Nullability.NotNull
import org.jetbrains.kotlin.load.java.propertyNameByGetMethodName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.symbols.JKSymbol
import org.jetbrains.kotlin.nj2k.symbols.JKUniverseMethodSymbol
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKClass.ClassKind.ANNOTATION
import org.jetbrains.kotlin.nj2k.tree.Modality.ABSTRACT
import org.jetbrains.kotlin.nj2k.tree.OtherModifier.*
import org.jetbrains.kotlin.nj2k.tree.Visibility.INTERNAL
import org.jetbrains.kotlin.nj2k.tree.Visibility.PUBLIC
import org.jetbrains.kotlin.nj2k.types.JKClassType
import org.jetbrains.kotlin.nj2k.types.JKNoType
import org.jetbrains.kotlin.nj2k.types.JKType

/**
 * This conversion tries to merge sets of overloaded methods or constructors into a single method/constructor
 * with default parameters and `JvmOverloads` annotation.
 */
class DefaultArgumentsConversion(context: ConverterContext) : RecursiveConversion(context) {
    context(_: KaSession)
    override fun applyToElement(element: JKTreeElement): JKTreeElement {
        if (element !is JKClassBody) return recurse(element)
        val methods = element.declarations.filterIsInstance<JKMethod>().sortedBy { it.parameters.size }
        for (method in methods) {
            processMethod(method, element)
        }
        addJvmOverloadsAnnotations(element)
        return recurse(element)
    }

    private fun processMethod(method: JKMethod, classBody: JKClassBody) {
        val singleStatement = method.block.statements.singleOrNull() ?: return
        val callExpression = extractCallExpression(singleStatement) ?: return
        val calledMethod = (callExpression.identifier as? JKUniverseMethodSymbol)?.target ?: return
        val arguments = callExpression.arguments.arguments

        if (!checkSignatureCompatibility(method, calledMethod, arguments)) return
        if (method.canNotBeMerged() || calledMethod.canNotBeMerged()) return
        migrateParameterDefaultValues(method, calledMethod)
        if (!checkArgumentsMatchParameterDefaultValues(method, calledMethod, arguments)) return
        callExpression.arguments.invalidate()

        val defaultPairs: List<Pair<JKExpression, JKParameter>> =
            arguments.map { it::value.detached() }.zip(calledMethod.parameters).drop(method.parameters.size)

        fun JKSymbol.needsExplicitThisReceiver(): Boolean {
            val parameters = defaultPairs.map { it.second }
            val propertyNameByGetMethodName = propertyNameByGetMethodName(Name.identifier(this.name))?.asString()
            return parameters.any { it.name.value == this.name || it.name.value == propertyNameByGetMethodName }
                    && classBody.declarations.any { it == this.target }
        }

        fun remapParameterSymbol(on: JKTreeElement): JKTreeElement {
            when {
                on is JKQualifiedExpression && on.receiver is JKThisExpression ->
                    return on

                on is JKFieldAccessExpression -> {
                    val target = on.identifier.target
                    if (target is JKParameter && target.parent == method) {
                        val newSymbol = symbolProvider.provideUniverseSymbol(calledMethod.parameters[method.parameters.indexOf(target)])
                        return JKFieldAccessExpression(newSymbol)
                    } else if (on.identifier.needsExplicitThisReceiver()) {
                        return JKQualifiedExpression(JKThisExpression(JKLabelEmpty(), JKNoType), JKFieldAccessExpression(on.identifier))
                    }
                }

                on is JKCallExpression && on.identifier.needsExplicitThisReceiver() -> {
                    val selector = applyRecursive(on, ::remapParameterSymbol)
                    return JKQualifiedExpression(JKThisExpression(JKLabelEmpty(), JKNoType), selector)
                }
            }

            return applyRecursive(on, ::remapParameterSymbol)
        }

        for ((defaultValue, parameter) in defaultPairs) {
            parameter.initializer = remapParameterSymbol(defaultValue) as JKExpression
        }

        classBody.declarations -= method
        calledMethod.withFormattingFrom(method)
    }

    private fun JKMethod.canNotBeMerged(): Boolean =
        modality == ABSTRACT
                || hasOtherModifier(OVERRIDE)
                || hasOtherModifier(NATIVE)
                || hasOtherModifier(SYNCHRONIZED)
                || hasAnnotations
                || name.value.canBeGetterOrSetterName()
                || psi<PsiMethod>()?.let { context.converter.referenceSearcher.hasOverrides(it) } == true

    private fun areEqualElements(first: JKElement, second: JKElement): Boolean {
        if (first::class != second::class) return false
        if (first is JKNameIdentifier && second is JKNameIdentifier) return first.value == second.value
        if (first is JKLiteralExpression && second is JKLiteralExpression) return first.literal == second.literal
        if (first is JKFieldAccessExpression && second is JKFieldAccessExpression && first.identifier != second.identifier) return false
        if (first is JKCallExpression && second is JKCallExpression && first.identifier != second.identifier) return false
        if (first !is JKTreeElement || second !is JKTreeElement) return false

        return first.children.zip(second.children) { childOfFirst, childOfSecond ->
            when {
                childOfFirst is JKTreeElement && childOfSecond is JKTreeElement ->
                    areEqualElements(childOfFirst, childOfSecond)

                childOfFirst is List<*> && childOfSecond is List<*> -> {
                    childOfFirst.zip(childOfSecond) { child1, child2 ->
                        areEqualElements(child1 as JKElement, child2 as JKElement)
                    }.fold(true, Boolean::and)
                }

                else -> false
            }
        }.fold(true, Boolean::and)
    }

    private fun extractCallExpression(statement: JKStatement): JKCallExpression? {
        val expression = when (statement) {
            is JKExpressionStatement -> statement.expression
            is JKReturnStatement -> statement.expression
            else -> null
        }
        return when (expression) {
            is JKCallExpression -> expression
            is JKQualifiedExpression -> {
                if (expression.receiver !is JKThisExpression) return null
                expression.selector as? JKCallExpression
            }

            else -> null
        }
    }

    private fun checkSignatureCompatibility(method: JKMethod, calledMethod: JKMethod, arguments: List<JKArgument>): Boolean {
        if (calledMethod.visibility != method.visibility) return false
        if (calledMethod.parent != method.parent) return false
        if (calledMethod.name.value != method.name.value) return false
        if (calledMethod.returnType.type != method.returnType.type) return false

        if (arguments.size <= method.parameters.size) return false
        // `calledMethod` has a varargs parameter or call expression has errors
        if (arguments.size < calledMethod.parameters.size) return false
        if (calledMethod.parameters.any(JKParameter::isVarArgs)) return false

        for (i in method.parameters.indices) {
            val parameter = method.parameters[i]
            val targetParameter = calledMethod.parameters[i]
            val argument = arguments[i].value

            if (parameter.name.value != targetParameter.name.value) return false
            if (!parameter.type.type.isCompatible(targetParameter.type.type)) return false
            if (argument !is JKFieldAccessExpression || argument.identifier.target != parameter) return false

            // parameter default values must be identical
            if (parameter.initializer !is JKStubExpression
                && targetParameter.initializer !is JKStubExpression
                && !areEqualElements(targetParameter.initializer, parameter.initializer)
            ) return false
        }

        return true
    }

    private fun JKType.isCompatible(other: JKType): Boolean {
        fun Nullability.isCompatible(other: Nullability): Boolean =
            // If the source is not-null, target nullability can be anything.
            // Otherwise, the source may be null, so the target should _not_ be non-null.
            this == NotNull || other != NotNull

        if (this is JKClassType && other is JKClassType) {
            return classReference == other.classReference &&
                    parameters == other.parameters &&
                    nullability.isCompatible(other.nullability)
        }

        return this == other
    }

    private fun migrateParameterDefaultValues(method: JKMethod, calledMethod: JKMethod) {
        for (i in method.parameters.indices) {
            val parameter = method.parameters[i]
            val targetParameter = calledMethod.parameters[i]
            if (parameter.initializer !is JKStubExpression && targetParameter.initializer is JKStubExpression) {
                targetParameter.initializer = parameter.initializer.copyTreeAndDetach()
            }
        }
    }

    private fun checkArgumentsMatchParameterDefaultValues(method: JKMethod, calledMethod: JKMethod, arguments: List<JKArgument>): Boolean {
        for (index in (method.parameters.lastIndex + 1)..calledMethod.parameters.lastIndex) {
            val argumentValue = arguments[index].value
            val defaultArgument = calledMethod.parameters[index].initializer.takeIf { it !is JKStubExpression } ?: continue
            if (!areEqualElements(argumentValue, defaultArgument)) return false
        }
        return true
    }

    private fun addJvmOverloadsAnnotations(classBody: JKClassBody) {
        fun JKMethod.hasParametersWithDefaultValues(): Boolean =
            parameters.any { it.initializer !is JKStubExpression }

        if (classBody.parentOfType<JKClass>()?.classKind == ANNOTATION) return
        for (method in classBody.declarations) {
            if (method !is JKMethod) continue
            if (method.hasParametersWithDefaultValues() && (method.visibility == PUBLIC || method.visibility == INTERNAL)) {
                method.annotationList.annotations += jvmAnnotation("JvmOverloads", symbolProvider)
            }
        }
    }
}
