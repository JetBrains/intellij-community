// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.lang.Language
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinModifiableChangeInfo
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtValVarKeywordOwner

class KotlinChangeInfo(
    val methodDescriptor: KotlinMethodDescriptor,
    parameterInfos: List<KotlinParameterInfo> = methodDescriptor.parameters,
    override var aNewVisibility: Visibility = methodDescriptor.visibility,
    receiver: KotlinParameterInfo? = methodDescriptor.receiver,
    private var name: String = methodDescriptor.name,
    var newReturnTypeInfo: KotlinTypeInfo = KotlinTypeInfo(methodDescriptor.oldReturnType, methodDescriptor.method),
    var checkUsedParameters: Boolean = true
) : KotlinChangeInfoBase, KotlinModifiableChangeInfo<KotlinParameterInfo>, UserDataHolderBase() {

    private val oldName = methodDescriptor.name

    private val oldNameToParameterIndex: Map<String, Int> = HashMap<String, Int>().apply {
        val parameters = (methodDescriptor.method as? KtCallableDeclaration)?.valueParameters
        parameters?.indices?.forEach { i -> this[parameters[i].name ?: ""] = i }
    }

    override fun getOldParameterIndex(oldParameterName: String): Int? {
        return oldNameToParameterIndex[oldParameterName]
    }

    private val newParameters = parameterInfos.toMutableList()
    override fun addParameter(
        parameterInfo: KotlinParameterInfo,
        atIndex: Int
    ) {
        if (atIndex >= 0) {
            newParameters.add(atIndex, parameterInfo)
        } else {
            newParameters.add(parameterInfo)
        }
    }

    override fun removeParameter(index: Int) {
        val parameterInfo = newParameters.removeAt(index)
        if (parameterInfo == receiverParameterInfo) {
            receiverParameterInfo = null
        }
    }

    override fun clearParameters() {
        newParameters.clear()
        receiverParameterInfo = null
    }

    override fun setNewName(value: String) {
        name = value
    }

    override fun setNewParameter(
        index: Int,
        parameterInfo: KotlinParameterInfo
    ) {
        newParameters[index] = parameterInfo
    }

    val parametersToRemove: BooleanArray
        get() {
            val originalReceiver = methodDescriptor.receiver
            val hasReceiver = originalReceiver != null
            val receiverShift = if (hasReceiver) 1 else 0

            val toRemove = BooleanArray(receiverShift + methodDescriptor.parametersCount) { true }
            if (hasReceiver) {
                toRemove[0] = receiverParameterInfo == null && originalReceiver !in getNonReceiverParameters()
            }

            for (parameter in newParameters) {
                if (parameter.wasContextParameter) continue
                parameter.oldIndex.takeIf { it >= 0 }?.let { oldIndex ->
                    toRemove[oldIndex] = false
                }
            }

            return toRemove
        }

    fun getNonReceiverParameters(): List<KotlinParameterInfo> {
        return receiverParameterInfo?.let { receiver -> newParameters.filter { it != receiver } } ?: newParameters
    }

    override fun setNewVisibility(visibility: Visibility) {
        aNewVisibility = visibility
    }
    override fun isVisibilityChanged(): Boolean = aNewVisibility != methodDescriptor.visibility
    override val aNewReturnType: String?
        //keep explicit typealias in signature
        get() = newReturnTypeInfo.text.takeUnless { it == "Unit" || it == "kotlin.Unit"}

    override var receiverParameterInfo: KotlinParameterInfo? = receiver
        set(value) {
            if (value != null && value !in newParameters) {
                newParameters.add(value)
            }
            if (value == null && method is KtValVarKeywordOwner && field in newParameters) {
                newParameters.remove(field)
            }
            field = value
        }

    override val oldReceiverInfo = methodDescriptor.receiver

    override var primaryPropagationTargets: Collection<PsiElement> = emptyList()

    override fun getNewParameters(): Array<KotlinParameterInfo> {
        return newParameters.toTypedArray()
    }

    override fun setType(type: String) {
        newReturnTypeInfo = KotlinTypeInfo(type, methodDescriptor.method)
    }

    private val isParameterSetOrOrderChangedLazy: Boolean by lazy {
        val (contextParameters, signatureParameters) = getNonReceiverParameters().partition { it.isContextParameter }
        methodDescriptor.receiver?.oldIndex != receiverParameterInfo?.oldIndex ||
                signatureParameters.size != methodDescriptor.parametersCount ||
                signatureParameters.indices.any { i -> signatureParameters[i].oldIndex != i } ||
                contextParameters.any { it.isNewParameter || !it.wasContextParameter } ||
                methodDescriptor.parameters.filter { it.isContextParameter }.size != contextParameters.size
    }
    override fun isParameterSetOrOrderChanged(): Boolean {
        return isParameterSetOrOrderChangedLazy
    }

    override fun isParameterTypesChanged(): Boolean {
        return true
    }

    override fun isParameterNamesChanged(): Boolean {
        return false
    }

    override fun isGenerateDelegate(): Boolean {
        return false
    }

    override fun isNameChanged(): Boolean {
        return name != oldName
    }

    override fun getMethod(): KtNamedDeclaration {
        return methodDescriptor.baseDeclaration
    }

    override fun isReturnTypeChanged(): Boolean {
        return newReturnTypeInfo.text != methodDescriptor.oldReturnType
    }

    override fun getNewName(): String {
        return name
    }

    override fun getLanguage(): Language {
        return KotlinLanguage.INSTANCE
    }

    override fun isReceiverTypeChanged(): Boolean {
        val receiverInfo = receiverParameterInfo ?: return (method as? KtCallableDeclaration)?.receiverTypeReference != null
        return (method as? KtCallableDeclaration)?.receiverTypeReference == null || receiverInfo.typeText != methodDescriptor.oldReceiverType
    }

    fun hasAppendedParametersOnly(): Boolean {
        val oldParamCount = (method as? KtCallableDeclaration)?.valueParameters?.size ?: 0
        return newParameters.asSequence().withIndex().all { (i, p) -> if (i < oldParamCount) p.oldIndex == i else p.isNewParameter }
    }

    internal val dependentProperties = mutableMapOf<KtCallableDeclaration, KotlinChangeInfo>()
    internal fun registerPropertyChangeInfo(element: KtCallableDeclaration, propertyChangeInfo: KotlinChangeInfo) {
        dependentProperties[element] = propertyChangeInfo
    }
}