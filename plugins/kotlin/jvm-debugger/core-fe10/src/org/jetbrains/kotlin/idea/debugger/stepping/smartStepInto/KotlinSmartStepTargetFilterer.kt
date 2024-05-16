// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.idea.debugger.base.util.fqnToInternalName
import org.jetbrains.kotlin.idea.debugger.base.util.internalNameToFqn
import org.jetbrains.kotlin.idea.debugger.core.DebuggerUtils.trimIfMangledInBytecode
import org.jetbrains.kotlin.idea.debugger.core.getJvmInternalClassName
import org.jetbrains.kotlin.idea.debugger.core.isInlineClass
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import org.jetbrains.org.objectweb.asm.Type
import java.util.*

class KotlinSmartStepTargetFilterer(
    private val targets: List<KotlinMethodSmartStepTarget>,
    private val debugProcess: DebugProcessImpl
) {
    private val functionCounter = mutableMapOf<String, Int>()
    private val targetWasVisited = BooleanArray(targets.size) { false }

    fun visitInlineFunction(function: KtNamedFunction) {
        val label = analyze(function) {
            val symbol = function.getSymbol()
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
            if (target.shouldBeVisited(owner, name, signature, currentCount)) {
                targetWasVisited[i] = true
                break
            }
        }
    }

    private fun KotlinMethodSmartStepTarget.shouldBeVisited(owner: String, name: String, signature: String, currentCount: Int): Boolean {
        val actualName = name.trimIfMangledInBytecode(methodInfo.isNameMangledInBytecode)
        // Inline class constructor argument is injected as the first
        // argument in inline class' functions. This doesn't correspond
        // with the PSI, so we delete the first argument from the signature
        val updatedSignature = if (methodInfo.isInlineClassMember) signature.getSignatureWithoutFirstArgument() else signature
        return matches(owner, actualName, updatedSignature, currentCount)
    }

    private fun KotlinMethodSmartStepTarget.matches(owner: String, name: String, signature: String, currentCount: Int): Boolean {
        if (methodInfo.name == name && ordinal == currentCount) {
            val declaration = getDeclaration() ?: return false
            if (declaration is KtClass) {
                // it means the method is, in fact, the implicit primary constructor
                return primaryConstructorMatches(declaration, owner, name, signature)
            }

            val lightClassMethod by lazy { declaration.getLightClassMethod() }
            if (methodInfo.isInlineClassMember || lightClassMethod == null) {
                // Cannot create light class for functions with inline classes
                return matchesBySignature(declaration, owner, signature)
            }
            return lightClassMethod!!.matches(owner, name, signature, debugProcess)
        }
        return false
    }

    private fun primaryConstructorMatches(declaration: KtClass, owner: String, name: String, signature: String): Boolean {
        return name == "<init>" && signature == "()V" &&
                owner.internalNameToFqn() == declaration.fqName?.asString()
    }

    private fun matchesBySignature(declaration: KtDeclaration, owner: String, signature: String): Boolean {
        analyze(declaration) {
            val symbol = declaration.getSymbol() as? KtFunctionLikeSymbol ?: return false
            return owner == symbol.getJvmInternalClassName() && signature == symbol.getJvmSignature()
        }
    }

    fun getUnvisitedTargets(): List<KotlinMethodSmartStepTarget> =
        targets.filterIndexed { i, _ ->
            !targetWasVisited[i]
        }
}

private fun String.getSignatureWithoutFirstArgument(): String {
    val type = Type.getType(this)
    val arguments = type.argumentTypes.drop(1).joinToString("") { it.descriptor }
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

context(KtAnalysisSession)
private fun KtFunctionLikeSymbol.getJvmSignature(): String? {
    val element = psi ?: return null
    val receiver = receiverType?.jvmName(element) ?: ""
    val parameterTypes = valueParameters.map { it.returnType.jvmName(element) ?: return null }.joinToString("")
    val returnType = returnType.jvmName(element) ?: return null
    return "($receiver$parameterTypes)$returnType"
}

context(KtAnalysisSession)
private fun KtType.jvmName(element: PsiElement): String? {
    if (this !is KtNonErrorClassType) return null
    val psiType = asPsiType(element, allowErrorTypes = false) ?: return null
    if (classSymbol.isInlineClass()) {
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
    return JvmClassName.internalNameByClassId(classId).internalNameToReferenceTypeName()
}

private fun String.internalNameToReferenceTypeName(): String = "L$this;"

