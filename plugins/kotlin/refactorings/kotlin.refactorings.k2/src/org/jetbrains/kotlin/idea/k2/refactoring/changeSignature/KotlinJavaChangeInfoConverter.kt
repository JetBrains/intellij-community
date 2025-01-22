// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.util.PsiSuperMethodUtil
import com.intellij.refactoring.changeSignature.*
import com.intellij.refactoring.util.CanonicalTypes
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.idea.refactoring.changeSignature.toValVar
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.Variance

fun fromJavaChangeInfo(changeInfo: ChangeInfo, usageInfo: UsageInfo, beforeMethodChange: Boolean): KotlinChangeInfoBase? {
    return when (changeInfo) {
        is KotlinChangeInfoBase -> changeInfo
        is JavaChangeInfo -> JavaChangeInfoConverters.findConverter(KotlinLanguage.INSTANCE)?.fromJavaChangeInfo(changeInfo, usageInfo, beforeMethodChange) as? KotlinChangeInfoBase
        else -> null
    }
}

class KotlinJavaChangeInfoConverter: JavaChangeInfoConverter {
    private val javaChangeInfoKey: Key<List<JavaChangeInfo>> = Key.create("WRAPPED")
    private val javaChangeInfoPerUsageKey: Key<MutableMap<UsageInfo, JavaChangeInfo>> = Key.create("Usages")

    override fun toJavaChangeInfo(
      changeInfo: ChangeInfo,
      usage: UsageInfo
    ): JavaChangeInfo? {
        if (changeInfo !is KotlinChangeInfo) return null

        (changeInfo.getUserData(javaChangeInfoPerUsageKey) ?: emptyMap())[usage]?.let { return it }

        val target = (usage.element?.parent as? PsiNewExpression)?.resolveConstructor() ?: usage.reference?.resolve() ?: usage.element
        val unwrappedKotlinBase = (target?.unwrapped as? KtNamedDeclaration
            ?: (usage as? OverriderUsageInfo)?.baseMethod?.unwrapped as? KtNamedDeclaration)

        if (unwrappedKotlinBase != null) {
            val propertyChangeInfo = changeInfo.dependentProperties[unwrappedKotlinBase]
            if (propertyChangeInfo != null) {
                val javaChangeInfo = toJavaChangeInfo(propertyChangeInfo, usage)
                return javaChangeInfo?.apply {
                    //remember javaChange info based on propertyChaneInfo on top level
                    rememberJavaInfo(changeInfo, javaChangeInfo, usage)
                }
            }
        }

        var javaChangeInfos = changeInfo.getUserData(javaChangeInfoKey)
        if (javaChangeInfos == null) {
            val ktCallableDeclaration = changeInfo.method.takeUnless { it.isExpectDeclaration() } ?: unwrappedKotlinBase
            val isProperty = ktCallableDeclaration is KtParameter || ktCallableDeclaration is KtProperty
            val isJvmOverloads = isJvmAnnotated(ktCallableDeclaration, JvmOverloads::class.java.simpleName)
            javaChangeInfos = ktCallableDeclaration?.toLightMethods()?.map {
                createJavaInfoForLightMethod(ktCallableDeclaration, it, changeInfo, isJvmOverloads, isProperty)
            } ?: emptyList()
            changeInfo.putUserData(javaChangeInfoKey, javaChangeInfos)
        }

        val javaChangeInfo = javaChangeInfos.firstOrNull { info ->
            val superMethod = info.method
            if (target is PsiClass) {
                target.constructors.any { it == superMethod }
            }
            else {
              target == superMethod ||
              target is PsiMethod && PsiSuperMethodUtil.isSuperMethod(target, superMethod) ||
              target is PsiMethod && superMethod.isConstructor && superMethod.parameters.isEmpty() ||
              target is PsiFunctionalExpression && LambdaUtil.getFunctionalInterfaceMethod(target) == superMethod
            }
        }

        return javaChangeInfo?.apply {
            rememberJavaInfo(changeInfo, javaChangeInfo, usage)
        }
    }

    private fun isJvmAnnotated(ktCallableDeclaration: KtNamedDeclaration?, annotationName: String): Boolean =
        if (ktCallableDeclaration is KtFunction) {
            ktCallableDeclaration.annotationEntries.any {
                it.calleeExpression?.constructorReferenceExpression?.getReferencedName() ==
                        annotationName
            }
        } else {
            false
        }

    private fun rememberJavaInfo(
        changeInfo: KotlinChangeInfo,
        javaChangeInfo: JavaChangeInfo,
        usage: UsageInfo
    ) {
        var map = changeInfo.getUserData(javaChangeInfoPerUsageKey)
        if (map == null) {
            map = mutableMapOf()
            changeInfo.putUserData(javaChangeInfoPerUsageKey, map)
        }
        map[usage] = javaChangeInfo
    }

    private fun createJavaInfoForLightMethod(
        method: KtNamedDeclaration,
        lightMethod: PsiMethod,
        changeInfo: KotlinChangeInfo,
        isJvmOverloads: Boolean,
        isProperty: Boolean
    ): JavaChangeInfo {
        val params = mapKotlinParametersToJava(method, lightMethod, changeInfo, isJvmOverloads)

        val afterReceiverIdx = if ((method as? KtCallableDeclaration)?.receiverTypeReference != null) 1 else 0
        if (isProperty && lightMethod.parameters.size > afterReceiverIdx) {
            //additional parameter for setter
            params += ParameterInfoImpl(
                afterReceiverIdx,
                "value",
                createPsiType(changeInfo.newReturnTypeInfo.text!!, method)
            )
        }

        val returnType = if (changeInfo.newReturnTypeInfo.text != null && !(isProperty && lightMethod.parameters.size > afterReceiverIdx))
            createPsiType(changeInfo.newReturnTypeInfo.text!!, method, true)
        else PsiTypes.voidType()

        var newName = changeInfo.newName
        if (isProperty) {
            if (JvmAbi.isGetterName(lightMethod.name)) {
                newName = JvmAbi.getterName(newName)
            } else {
                newName = JvmAbi.setterName(newName)
            }
        }
        val visibility = when (changeInfo.aNewVisibility) {
            Visibilities.Private -> PsiModifier.PRIVATE
            Visibilities.Internal -> PsiModifier.PACKAGE_LOCAL
            Visibilities.Protected -> PsiModifier.PROTECTED
            else -> PsiModifier.PUBLIC
        }
        return JavaChangeInfoImpl.generateChangeInfo(
            lightMethod,
            false,
            false,
            visibility,
            newName.takeUnless { isJvmAnnotated(method, JvmName::class.java.simpleName) } ?: lightMethod.name,
            CanonicalTypes.createTypeWrapper(returnType),
            params.toTypedArray(),
            emptyArray(),
            mutableSetOf(),
            mutableSetOf()
        )
    }

    @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class, KaExperimentalApi::class)
    private fun createPsiType(ktTypeText: String, originalFunction: PsiElement, unitToVoid: Boolean = false): PsiType {
        val project = originalFunction.project
        val codeFragment = KtPsiFactory(project).createTypeCodeFragment(ktTypeText, originalFunction)
        return allowAnalysisOnEdt {
            allowAnalysisFromWriteAction {
                analyze(codeFragment) {
                    val ktType = codeFragment.getContentElement()?.type!!
                    if (unitToVoid && ktType.isUnitType) PsiTypes.voidType() else ktType.asPsiType(originalFunction, true)!!
                }
            }
        }
    }

    private fun mapKotlinParametersToJava(
        originalFunction: KtNamedDeclaration,
        currentPsiMethod: PsiMethod,
        changeInfoBase: KotlinChangeInfoBase,
        isJvmOverloads: Boolean
    ): MutableList<ParameterInfoImpl> {
        fun createParamInfo(paramInfo: KotlinParameterInfo, originalIndex: Int): ParameterInfoImpl {
            val ktParameter = paramInfo.getDeclarationSignature(originalFunction, originalFunction, false)
            val qName = ktParameter.typeReference!!.getTypeText()
            val classType = createPsiType(qName, originalFunction)
            return ParameterInfoImpl(
                originalIndex,
                paramInfo.name,
                classType,
                (paramInfo.defaultValue ?: paramInfo.defaultValueForCall)?.text ?: ""
            )
        }

        val newParameterList = changeInfoBase.newParameters
        val receiverOffset = if ((originalFunction as? KtCallableDeclaration)?.receiverTypeReference != null) 1 else 0
        val oldParameterCount = ((originalFunction as? KtCallableDeclaration)?.valueParameters?.count() ?: 0) + receiverOffset
        val oldIndexMap = mutableMapOf<Int, Int>()
        var orderChanged = false
        if (isJvmOverloads) {
            (originalFunction as? KtCallableDeclaration)?.valueParameters?.forEachIndexed { oldIdx, p ->
                for ((idx, pp) in currentPsiMethod.parameterList.parameters.withIndex()) {
                    if (pp.unwrapped == p) {
                        oldIndexMap[oldIdx] = idx
                        break
                    }
                }
            }

            if (newParameterList.size == oldParameterCount) {
                for ((i, p) in newParameterList.withIndex()) {
                    if (p.isNewParameter) continue
                    if (i != p.oldIndex && (oldIndexMap.containsKey(p.oldIndex) || oldIndexMap.containsKey(i))) {
                        orderChanged = true
                        break
                    }
                }
            }
        }

        val params = mutableListOf<ParameterInfoImpl>()

        newParameterList.forEachIndexed { i, info ->
            if (info.defaultValue != null && isJvmOverloads && !oldIndexMap.containsKey(info.oldIndex)) {
                if (!info.isNewParameter && !orderChanged) {
                    return@forEachIndexed
                }

                if (info.isNewParameter) {
                    var allNextDefaultMissed = true
                    for ((nestedIdx, pp) in newParameterList.withIndex()) {
                        if (nestedIdx > i && pp.defaultValue != null && oldIndexMap.containsKey(pp.oldIndex)) {
                            allNextDefaultMissed = false
                            break
                        }
                    }
                    if (allNextDefaultMissed) {
                        return@forEachIndexed
                    }
                }
            }

            val oldIndex = if (isJvmOverloads) (oldIndexMap[info.oldIndex] ?: -1) else info.oldIndex
            val param = createParamInfo(info, oldIndex)
            if (info == changeInfoBase.receiverParameterInfo) {
                params.add(0, param)
            } else {
                params.add(param)
            }
        }

        return params
    }

    override fun fromJavaChangeInfo(
        changeInfo: JavaChangeInfo,
        usageInfo: UsageInfo,
        beforeMethodChanged: Boolean
    ): KotlinChangeInfoBase {
        val useSiteKtElement = usageInfo.element?.unwrapped as KtElement

        val kotlinParameterInfos =
            changeInfo.newParameters.map { p -> mapJavaParameterToKotlin(changeInfo, p, useSiteKtElement, beforeMethodChanged) }.toTypedArray()

        val returnType = changeInfo.newReturnType?.getType(changeInfo.method)?.getCanonicalText()
        return object : KotlinChangeInfoBase, ChangeInfo by changeInfo {
            override var receiverParameterInfo: KotlinParameterInfo? = null

            override val oldReceiverInfo: KotlinParameterInfo? = null

            override fun isReceiverTypeChanged(): Boolean = false

            override var primaryPropagationTargets: Collection<PsiElement> = changeInfo.methodsToPropagateParameters

            override fun isVisibilityChanged(): Boolean = changeInfo.isVisibilityChanged

            override fun getOldParameterIndex(oldParameterName: String): Int? {
                return changeInfo.oldParameterNames.indexOf(oldParameterName).takeIf { it >= 0 }
            }

            override fun getNewParameters(): Array<out KotlinParameterInfo> {
                return kotlinParameterInfos            }

            override val aNewReturnType: String? = returnType

            override val aNewVisibility: Visibility
                get() {
                    return when (changeInfo.newVisibility) {
                        PsiModifier.PRIVATE -> Visibilities.Private
                        PsiModifier.PACKAGE_LOCAL -> Visibilities.Internal
                        PsiModifier.PROTECTED -> Visibilities.Protected
                        else -> Visibilities.Public
                    }
                }
        }
    }

    private fun mapJavaParameterToKotlin(
        changeInfo: JavaChangeInfo,
        p: JavaParameterInfo,
        useSiteKtElement: KtElement,
        beforeMethodChanged: Boolean
    ): KotlinParameterInfo {
        val psiMethod = changeInfo.method
        val oldName = if (p.oldIndex >= 0) changeInfo.oldParameterNames[p.oldIndex] else p.name
        val oldValVar =
            if (p.oldIndex >= 0 && beforeMethodChanged) (psiMethod.parameterList.parameters[p.oldIndex].unwrapped as? KtParameter)?.valOrVarKeyword?.toValVar() else null
        val originalType = createKotlinTypeInfo(p, psiMethod, useSiteKtElement)
        return KotlinParameterInfo(
            originalIndex = p.oldIndex,
            originalType = originalType,
            name = oldName,
            valOrVar = oldValVar ?: KotlinValVar.None,
            defaultValueForCall = p.defaultValue?.let {
                try {
                    KtPsiFactory(psiMethod.project).createExpression(it)
                } catch (_: Throwable) {
                    null
                }
            },
            defaultValueAsDefaultParameter = false,
            defaultValue = null,
            context = useSiteKtElement
        ).apply {
            name = p.name
        }
    }

    @OptIn(KaExperimentalApi::class, KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
    private fun createKotlinTypeInfo(
        p: JavaParameterInfo,
        psiMethod: PsiMethod,
        useSiteKtElement: KtElement
    ): KotlinTypeInfo {
        val unwrapped = psiMethod.unwrapped
        val psiType = p.typeWrapper.getType(psiMethod)
        val typeText = if (unwrapped is KtNamedDeclaration) {
            allowAnalysisFromWriteAction {
                allowAnalysisOnEdt {
                    analyze(unwrapped) {
                        psiType.asKaType(unwrapped)?.render(position = Variance.IN_VARIANCE)
                    }
                }
            }
        } else {
            null
        }
        return KotlinTypeInfo(
            typeText ?: psiType.getCanonicalText(),
            useSiteKtElement
        )
    }
}