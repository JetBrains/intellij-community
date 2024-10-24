// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.fileClasses.internalNameWithoutInnerClasses
import org.jetbrains.kotlin.idea.debugger.base.util.KotlinDebuggerConstants
import org.jetbrains.kotlin.idea.debugger.base.util.fqnToInternalName
import org.jetbrains.kotlin.idea.debugger.base.util.internalNameToFqn
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.trimIfMangledInBytecode
import org.jetbrains.kotlin.idea.debugger.core.getJvmInternalClassName
import org.jetbrains.kotlin.idea.debugger.core.getJvmInternalName
import org.jetbrains.kotlin.idea.debugger.core.isInlineClass
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import org.jetbrains.org.objectweb.asm.Type

class KotlinSmartStepTargetFilterer(
    private val targets: List<KotlinMethodSmartStepTarget>,
    private val debugProcess: DebugProcessImpl
) {
    private val functionCounter = mutableMapOf<String, Int>()
    private val targetWasVisited = BooleanArray(targets.size) { false }

    suspend fun visitInlineFunction(function: KtNamedFunction) {
        val label = readAction {
            analyze(function) {
                val symbol = function.symbol
                KotlinMethodSmartStepTarget.calcLabel(symbol)
            }
        }
        val currentCount = functionCounter.increment(label) - 1
        val matchedSteppingTargetIndex = targets.indexOfFirst {
            it.getDeclaration() === function && it.ordinal == currentCount
        }
        if (matchedSteppingTargetIndex < 0) return
        targetWasVisited[matchedSteppingTargetIndex] = true
    }

    fun visitInlineInvokeCall() {
        val matchedSteppingTargetIndex = targets.indexOfFirst {
            it.methodInfo.isInvoke && it.ordinal == functionCounter.getOrDefault(it.label, 0)
        }
        if (matchedSteppingTargetIndex < 0) return
        val labelFound = targets[matchedSteppingTargetIndex].label
        if (labelFound != null) functionCounter.increment(labelFound)

        targetWasVisited[matchedSteppingTargetIndex] = true
    }

    suspend fun visitOrdinaryFunction(owner: String, name: String, signature: String) {
        val currentCount = functionCounter.increment("$owner.$name$signature") - 1
        for ((i, target) in targets.withIndex()) {
            if (targetWasVisited[i]) continue
            if (target.shouldBeVisited(owner, name, signature, currentCount)) {
                targetWasVisited[i] = true
                break
            }
        }
    }

    private suspend fun KotlinMethodSmartStepTarget.shouldBeVisited(owner: String, name: String, signature: String, currentCount: Int): Boolean {
        val (updatedOwner, updatedName, updatedSignature) = BytecodeSignature(owner, name, signature)
            .handleMangling(methodInfo)
            .handleValueClassMethods(methodInfo)
            .handleDefaultArgs()
            .handleDefaultConstructorMarker()
            .handleDefaultInterfaces()
            .handleAccessMethods()
            .handleInvokeSuspend(methodInfo)
        return matches(updatedOwner, updatedName, updatedSignature, currentCount)
    }

    private suspend fun KotlinMethodSmartStepTarget.matches(owner: String, name: String, signature: String, currentCount: Int): Boolean {
        if (ordinal != currentCount) return false
        val nameMatches = methodNameMatches(methodInfo, name)
        if (!nameMatches) return false
        // Declaration may be empty only for invoke functions
        // In this case, there is only one possible signature, so it should match
        val declaration = getDeclaration() ?: return methodInfo.isInvoke
        if (declaration is KtClass) {
            // Standard methods may be also resolved to a class
            when (name) {
                "toString" -> return signature == "()Ljava/lang/String;"
                "hashCode" -> return signature == "()I"
                "equals" -> return signature == "(Ljava/lang/Object;)Z"
            }
            // it means the method is, in fact, the implicit primary constructor
            return readAction {
                analyze(declaration) {
                    primaryConstructorMatches(declaration, owner, name, signature)
                }
            }
        }

        if (!methodInfo.isInlineClassMember) {
            // Cannot create light class for functions with inline classes
            val lightMethod = readAction { declaration.getLightClassMethod() }
            // Do not match by name, as it was already checked
            val lightMethodMatch = runReadAction { lightMethod?.matches(owner, signature, debugProcess) }
            // Light method match still can fail in some Kotlin-specific cases (e.g., setter/getter signature)
            if (lightMethodMatch == true) {
                return true
            }
        }
        return matchesBySignature(declaration, owner, signature)
    }

    context(KaSession)
    private fun primaryConstructorMatches(declaration: KtClass, owner: String, name: String, signature: String): Boolean {
        if (name != JVMNameUtil.CONSTRUCTOR_NAME || signature != "()V") return false
        val symbol = declaration.symbol as? KaClassSymbol ?: return false
        val internalClassName = symbol.getJvmInternalName()
        return owner == internalClassName
    }

    private suspend fun matchesBySignature(declaration: KtDeclaration, owner: String, signature: String): Boolean =
        readAction {
            analyze(declaration) {
                val symbol = declaration.symbol as? KaCallableSymbol ?: return@analyze false
                val declarationSignature = symbol.getJvmSignature()
                val declarationInternalName = symbol.getJvmInternalClassName()
                signature == declarationSignature && owner.isSubClassOf(declarationInternalName)
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
    if (!name.endsWith(JvmAbi.DEFAULT_PARAMS_IMPL_SUFFIX)) return this
    val type = Type.getType(signature)
    val parametersCount = type.argumentCount
    val sourceParametersCount = sourceParametersCount(parametersCount)
    return copy(
        name = name.substringBefore(JvmAbi.DEFAULT_PARAMS_IMPL_SUFFIX),
        signature = buildSignature(signature, parametersCount - sourceParametersCount, fromStart = false)
    )
}

private fun BytecodeSignature.handleDefaultConstructorMarker(): BytecodeSignature {
    if (name != JVMNameUtil.CONSTRUCTOR_NAME) return this
    val type = Type.getType(signature)
    val defaultMarkerDescriptor = KotlinDebuggerConstants.DEFAULT_CONSTRUCTOR_MARKER_FQ_NAME
        .internalNameWithoutInnerClasses
        .internalNameToReferenceTypeName()
    if (type.argumentTypes.lastOrNull()?.descriptor != defaultMarkerDescriptor) return this
    return copy(signature = buildSignature(signature, dropCount = 1, fromStart = false))
}

private fun BytecodeSignature.handleDefaultInterfaces(): BytecodeSignature {
    if (!owner.endsWith(JvmAbi.DEFAULT_IMPLS_SUFFIX)) return this
    return copy(
        owner = owner.removeSuffix(JvmAbi.DEFAULT_IMPLS_SUFFIX),
        signature = buildSignature(signature, 1, fromStart = true)
    )
}

private fun BytecodeSignature.handleAccessMethods(): BytecodeSignature {
    if (!name.startsWith("access\$")) return this
    return copy(name = name.removePrefix("access\$"))
}

private fun BytecodeSignature.handleInvokeSuspend(methodInfo: CallableMemberInfo): BytecodeSignature {
    if (!methodInfo.isSuspend || !methodInfo.isInvoke) return this
    if (methodInfo.name != KotlinDebuggerConstants.INVOKE_SUSPEND_METHOD_NAME || name != "invoke") return this
    return copy(name = KotlinDebuggerConstants.INVOKE_SUSPEND_METHOD_NAME)
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

private fun PsiMethod.matches(className: String, signature: String, debugProcess: DebugProcessImpl): Boolean =
    DebuggerUtilsEx.methodMatches(
        this,
        className.internalNameToFqn(),
        null,
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
@OptIn(KaExperimentalApi::class)
private fun KaCallableSymbol.getJvmSignature(): String? {
    val element = psi ?: return null
    val contextReceivers = contextReceivers.mapNotNull { it.type.jvmName(element) }.joinToString("")
    val receiver = receiverType?.jvmName(element) ?: ""
    val isSuspend = this is KaFunctionSymbol && isSuspend()
    val parameterTypes = if (this is KaFunctionSymbol) {
        valueParameters.map {
            val typeName = it.returnType.jvmName(element) ?: return null
            if (it.isVararg) "[$typeName" else typeName
        }.joinToString("")
    } else ""
    val returnType = when {
        isSuspend -> "Ljava/lang/Object;"
        else -> returnType.jvmName(element) ?: return null
    }
    val continuationParameter = if (isSuspend) "Lkotlin/coroutines/Continuation;" else ""
    return "($contextReceivers$receiver$parameterTypes$continuationParameter)$returnType"
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

