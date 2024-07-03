// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.idea.debugger.base.util.fqnToInternalName
import org.jetbrains.kotlin.idea.debugger.base.util.internalNameToFqn
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.trimIfMangledInBytecode
import org.jetbrains.kotlin.idea.debugger.core.getJvmInternalClassName
import org.jetbrains.kotlin.idea.debugger.core.getJvmInternalName
import org.jetbrains.kotlin.idea.debugger.core.isInlineClass
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import org.jetbrains.org.objectweb.asm.Type

class KotlinSmartStepTargetFilterer(
    private val targets: List<KotlinMethodSmartStepTarget>,
    private val debugProcess: DebugProcessImpl
) {
    private val functionCounter = mutableMapOf<String, Int>()
    private val targetWasVisited = BooleanArray(targets.size) { false }

    fun visitInlineFunction(function: KtNamedFunction) {
        val label = analyze(function) {
            val symbol = function.symbol
            KotlinMethodSmartStepTarget.calcLabel(symbol)
        }
        val currentCount = functionCounter.increment(label) - 1
        val matchedSteppingTargetIndex = targets.indexOfFirst {
            it.getDeclaration() === function && it.ordinal == currentCount
        }
        if (matchedSteppingTargetIndex < 0) return
        targetWasVisited[matchedSteppingTargetIndex] = true
    }

    fun visitOrdinaryFunction(owner: String, name: String, signature: String) {
        val currentCount = functionCounter.increment("$owner.$name$signature") - 1
        for ((i, target) in targets.withIndex()) {
            if (targetWasVisited[i]) continue
            if (target.shouldBeVisited(owner, name, signature, currentCount)) {
                targetWasVisited[i] = true
                break
            }
        }
    }

    private fun KotlinMethodSmartStepTarget.shouldBeVisited(owner: String, name: String, signature: String, currentCount: Int): Boolean {
        val (updatedOwner, updatedName, updatedSignature) = BytecodeSignature(owner, name, signature)
            .handleMangling(methodInfo)
            .handleValueClassMethods(methodInfo)
            .handleDefaultArgs()
            .handleDefaultInterfaces()
        return matches(updatedOwner, updatedName, updatedSignature, currentCount)
    }

    private fun KotlinMethodSmartStepTarget.matches(owner: String, name: String, signature: String, currentCount: Int): Boolean {
        if (ordinal != currentCount) return false
        val nameMatches = if (methodInfo.isInternalMethod) internalNameMatches(name, methodInfo.name) else name == methodInfo.name
        if (!nameMatches) return false
        // Declaration may be empty only for invoke functions
        // In this case, there is only one possible signature, so it should match
        val declaration = getDeclaration() ?: return methodInfo.isInvoke
        if (declaration is KtClass) {
            // it means the method is, in fact, the implicit primary constructor
            analyze(declaration) {
                return primaryConstructorMatches(declaration, owner, name, signature)
            }
        }

        val lightClassMethod by lazy { declaration.getLightClassMethod() }
        // Cannot create light class for functions with inline classes
        // Internal name check fails with light method
        if (methodInfo.isInlineClassMember || methodInfo.isInternalMethod || lightClassMethod == null) {
            return matchesBySignature(declaration, owner, signature)
        }
        return lightClassMethod!!.matches(owner, name, signature, debugProcess)
    }

    context(KaSession)
    private fun primaryConstructorMatches(declaration: KtClass, owner: String, name: String, signature: String): Boolean {
        if (name != "<init>" || signature != "()V") return false
        val symbol = declaration.symbol as? KaClassSymbol ?: return false
        val internalClassName = symbol.getJvmInternalName()
        return owner == internalClassName
    }

    private fun matchesBySignature(declaration: KtDeclaration, owner: String, signature: String): Boolean {
        analyze(declaration) {
            val symbol = declaration.symbol as? KaFunctionSymbol ?: return false
            val declarationSignature = symbol.getJvmSignature()
            val declarationInternalName = symbol.getJvmInternalClassName()
            return signature == declarationSignature && owner.isSubClassOf(declarationInternalName)
        }
    }

    fun getUnvisitedTargets(): List<KotlinMethodSmartStepTarget> =
        targets.filterIndexed { i, _ ->
            !targetWasVisited[i]
        }
}

private data class BytecodeSignature(val owner: String, val name: String, val signature: String)

private fun BytecodeSignature.handleMangling(methodInfo: CallableMemberInfo): BytecodeSignature {
    if (!methodInfo.isNameMangledInBytecode) return this
    return copy(name = name.trimIfMangledInBytecode(true))
}

/**
 * Inline class constructor argument is injected as the first
 * argument in inline class' functions. This doesn't correspond
 * with the PSI, so we delete the first argument from the signature
 */
private fun BytecodeSignature.handleValueClassMethods(methodInfo: CallableMemberInfo): BytecodeSignature {
    if (!methodInfo.isInlineClassMember) return this
    return copy(signature = buildSignature(signature, 1, fromStart = true))
}

/**
 * Find the number of parameters in the source method.
 * <p>
 * If there are k params in the source method then in the modified method there are
 * z = f(k) = k + 1 + ceil(k / 32) parameters as several int flags and one Object are added as parameters.
 * This is the inverse function of f.
 *
 * @param z the number of parameters in the modified method
 * @return the number of parameters in the source method
 */
private fun sourceParametersCount(z: Int): Int {
    return z - 1 - (z - 1 + 32) / 33;
}

private fun BytecodeSignature.handleDefaultArgs(): BytecodeSignature {
    if (!name.endsWith("\$default")) return this
    val type = Type.getType(signature)
    val parametersCount = type.argumentCount
    val sourceParametersCount = sourceParametersCount(parametersCount)
    return copy(
        name = name.substringBefore("\$default"),
        signature = buildSignature(signature, parametersCount - sourceParametersCount, fromStart = false)
    )
}

private fun BytecodeSignature.handleDefaultInterfaces(): BytecodeSignature {
    if (!owner.endsWith("\$DefaultImpls")) return this
    return copy(
        owner = owner.removeSuffix("\$DefaultImpls"),
        signature = buildSignature(signature, 1, fromStart = true)
    )
}

private fun buildSignature(
    originalSignature: String,
    dropCount: Int,
    fromStart: Boolean,
): String {
    val type = Type.getType(originalSignature)
    val argumentTypes = type.argumentTypes
    val remainingArgumentTypes = if (fromStart) argumentTypes.drop(dropCount) else argumentTypes.dropLast(dropCount)
    val arguments = remainingArgumentTypes.joinToString("") { it.descriptor }
    return "($arguments)${type.returnType.descriptor}"
}

private fun KtDeclaration.getLightClassMethod(): PsiMethod? =
    when (this) {
        is KtFunction -> LightClassUtil.getLightClassMethod(this)
        is KtPropertyAccessor -> LightClassUtil.getLightClassPropertyMethods(property).getter
        else -> null
    }

private fun PsiMethod.matches(className: String, methodName: String, signature: String, debugProcess: DebugProcessImpl): Boolean =
    DebuggerUtilsEx.methodMatches(
        this,
        className.internalNameToFqn(),
        methodName,
        signature,
        debugProcess
    )

private fun MutableMap<String, Int>.increment(key: String): Int {
    val newValue = (get(key) ?: 0) + 1
    put(key, newValue)
    return newValue
}

private fun String.isSubClassOf(baseInternalName: String?): Boolean {
    if (baseInternalName == null) return false
    if (this == baseInternalName) return true
    // Only inheritance from Object is checked to support equals/toString methods
    return baseInternalName == "java/lang/Object" || baseInternalName == "kotlin/Any"
}

context(KaSession)
private fun KaFunctionSymbol.getJvmSignature(): String? {
    val element = psi ?: return null
    val receiver = receiverType?.jvmName(element) ?: ""
    val parameterTypes = valueParameters.map { it.returnType.jvmName(element) ?: return null }.joinToString("")
    val returnType = returnType.jvmName(element) ?: return null
    return "($receiver$parameterTypes)$returnType"
}

context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun KaType.jvmName(element: PsiElement): String? {
    if (this is KaTypeParameterType) return "Ljava/lang/Object;"
    if (this !is KaClassType) return null
    val psiType = asPsiType(element, allowErrorTypes = false) ?: return null
    if (symbol.isInlineClass()) {
        // handle wrapped types
        if (psiType.canonicalText == "java.lang.Object") return "Ljava/lang/Object;"
        if (psiType is PsiPrimitiveType) {
            return psiType.kind.binaryName
        }
    }
    if (isPrimitive) {
        return if (psiType is PsiPrimitiveType) psiType.kind.binaryName
        else psiType.canonicalText.fqnToInternalName().internalNameToReferenceTypeName()
    }
    if (psiType.canonicalText == "kotlin.Unit") return "V"
    val psiTypeInternalName = psiType.canonicalText.substringBefore("<").fqnToInternalName()
    if (psiTypeInternalName.startsWith("kotlin/jvm/") || psiTypeInternalName.startsWith("java/")) {
        return psiTypeInternalName.internalNameToReferenceTypeName()
    }
    val ktTypeInternalName = JvmClassName.internalNameByClassId(classId)
    return ktTypeInternalName.internalNameToReferenceTypeName()
}

private fun String.internalNameToReferenceTypeName(): String = "L$this;"

