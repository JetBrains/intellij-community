// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.changeSignature

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.util.PsiSuperMethodUtil
import com.intellij.refactoring.changeSignature.*
import com.intellij.refactoring.util.CanonicalTypes
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.*

fun fromJavaChangeInfo(changeInfo: ChangeInfo, usageInfo: UsageInfo): KotlinChangeInfoBase? {
    return when (changeInfo) {
        is KotlinChangeInfoBase -> changeInfo
        is JavaChangeInfo -> JavaChangeInfoConverters.findConverter(KotlinLanguage.INSTANCE)?.fromJavaChangeInfo(changeInfo, usageInfo) as? KotlinChangeInfoBase
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

        val target = usage.reference?.resolve() ?: usage.element
        val unwrappedKotlinBase = (target?.unwrapped as? KtCallableDeclaration
            ?: (usage as? OverriderUsageInfo)?.baseMethod?.unwrapped as? KtCallableDeclaration)

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
            val ktCallableDeclaration = changeInfo.method
            val isProperty = ktCallableDeclaration is KtParameter || ktCallableDeclaration is KtProperty
            val isJvmOverloads = if (ktCallableDeclaration is KtFunction) {
                ktCallableDeclaration.annotationEntries.any {
                    it.calleeExpression?.constructorReferenceExpression?.getReferencedName() ==
                            JvmOverloads::class.java.simpleName
                }
            } else {
                false
            }
            javaChangeInfos = ktCallableDeclaration.toLightMethods().map {
                createJavaInfoForLightMethod(ktCallableDeclaration as KtCallableDeclaration, it, changeInfo, isJvmOverloads, isProperty)
            }
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
        method: KtCallableDeclaration,
        lightMethod: PsiMethod,
        changeInfo: KotlinChangeInfo,
        isJvmOverloads: Boolean,
        isProperty: Boolean
    ): JavaChangeInfo {
        val params = mapKotlinParametersToJava(method, lightMethod, changeInfo, isJvmOverloads)

        val afterReceiverIdx = if (method.receiverTypeReference != null) 1 else 0
        if (isProperty && lightMethod.parameters.size > afterReceiverIdx) {
            //additional parameter for setter
            params += ParameterInfoImpl(
                afterReceiverIdx,
                "value",
                createPsiType(changeInfo.newReturnTypeInfo.text!!, method)
            )
        }

        val returnType = if (changeInfo.newReturnTypeInfo.text != null && !(isProperty && lightMethod.parameters.size > afterReceiverIdx))
            createPsiType(changeInfo.newReturnTypeInfo.text!!, method)
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
            newName,
            CanonicalTypes.createTypeWrapper(returnType),
            params.toTypedArray(),
            emptyArray(),
            mutableSetOf(),
            mutableSetOf()
        )
    }

    private fun createPsiType(ktTypeText: String, originalFunction: KtCallableDeclaration): PsiType {
        val project = originalFunction.project
        val codeFragment = KtPsiFactory(project).createExpressionCodeFragment("p as $ktTypeText", originalFunction)
        return analyze(codeFragment) {
            val ktType = codeFragment.getContentElement()!!.getKtType()!!
            val type = ktType.asPsiType(originalFunction, true)!!
            if (type is PsiPrimitiveType) return@analyze type
            val anno = when (ktType.nullability) {
                KtTypeNullability.NON_NULLABLE -> AnnotationUtil.NOT_NULL
                KtTypeNullability.NULLABLE -> AnnotationUtil.NULLABLE
                KtTypeNullability.UNKNOWN -> null
            }
            if (anno != null && !type.hasAnnotation(anno)) {
                val nullabilityAnno = JavaPsiFacade.getElementFactory(project).createAnnotationFromText("@$anno", originalFunction)
                type.annotate(TypeAnnotationProvider.Static.create(arrayOf(nullabilityAnno, *type.annotations)))
            }
            else type
        }
    }

    private fun mapKotlinParametersToJava(
        originalFunction: KtCallableDeclaration,
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
        val receiverOffset = if (originalFunction.receiverTypeReference != null) 1 else 0
        val oldParameterCount = originalFunction.valueParameters.count() + receiverOffset
        val oldIndexMap = mutableMapOf<Int, Int>()
        var orderChanged = false
        if (isJvmOverloads) {
            originalFunction.valueParameters.forEachIndexed { oldIdx, p ->
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

    private fun mapJavaParameterToKotlin(
        changeInfo: JavaChangeInfo,
        p: JavaParameterInfo,
        useSiteKtElement: KtElement
    ): KotlinParameterInfo {
        val psiMethod = changeInfo.method
        val oldName = if (p.oldIndex >= 0) changeInfo.oldParameterNames[p.oldIndex] else p.name
        return KotlinParameterInfo(
            p.oldIndex,
            KotlinTypeInfo(p.typeWrapper.getType(psiMethod).canonicalText, useSiteKtElement),
            oldName,
            KotlinValVar.None,
            p.defaultValue?.let {
                try {
                    KtPsiFactory(psiMethod.project).createExpression(it)
                } catch (e: Throwable) {
                    null
                }
            },
            false,
            null,
            useSiteKtElement
        ).apply {
            name = p.name
        }
    }

    override fun fromJavaChangeInfo(
      changeInfo: JavaChangeInfo,
      usageInfo: UsageInfo
    ): KotlinChangeInfoBase {
        val useSiteKtElement = usageInfo.element?.unwrapped as KtElement

        val kotlinParameterInfos =
            changeInfo.newParameters.map { p -> mapJavaParameterToKotlin(changeInfo, p, useSiteKtElement) }.toTypedArray()

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

}