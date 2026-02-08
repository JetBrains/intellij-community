// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin.psi

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameterList
import com.intellij.psi.PsiTypes
import com.intellij.psi.impl.light.LightModifierList
import com.intellij.psi.impl.light.LightParameterListBuilder
import com.intellij.psi.impl.light.LightReferenceListBuilder
import com.intellij.psi.impl.light.LightTypeParameterBuilder
import org.jetbrains.kotlin.analysis.api.types.KaTypeMappingMode
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.asJava.classes.toLightAnnotation
import org.jetbrains.kotlin.asJava.elements.KotlinLightTypeParameterListBuilder
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.types.typeUtil.TypeNullability
import org.jetbrains.kotlin.types.typeUtil.nullability
import org.jetbrains.kotlin.utils.SmartSet
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild
import org.jetbrains.uast.kotlin.PsiTypeConversionConfiguration
import org.jetbrains.uast.kotlin.TypeOwnerKind
import org.jetbrains.uast.kotlin.toPsiType

internal class UastFakeDescriptorLightMethod(
    private val originalDescriptor: SimpleFunctionDescriptor,
    private val containingClass: PsiClass,
    context: KtElement,
) : UastFakeDescriptorLightMethodBase<SimpleFunctionDescriptor>(originalDescriptor, containingClass, context) {

    private val buildTypeParameterListPart = UastLazyPart<PsiTypeParameterList>()
    private val paramsListPart = UastLazyPart<PsiParameterList>()

    private val _buildTypeParameterList: PsiTypeParameterList
        get() = buildTypeParameterListPart.getOrBuild {
            KotlinLightTypeParameterListBuilder(this).also { paramList ->
                for ((i, p) in originalDescriptor.typeParameters.withIndex()) {
                    paramList.addParameter(
                        object : LightTypeParameterBuilder(
                            p.name.identifier,
                            this,
                            i
                        ) {
                            private val myExtendsListPart = UastLazyPart<LightReferenceListBuilder>()

                            override fun getExtendsList(): LightReferenceListBuilder {
                                return myExtendsListPart.getOrBuild {
                                    super.getExtendsList().apply {
                                        for (bound in p.upperBounds) {
                                            val psiType =
                                                bound.toPsiType(
                                                    this@UastFakeDescriptorLightMethod,
                                                    this@UastFakeDescriptorLightMethod.context,
                                                    PsiTypeConversionConfiguration(TypeOwnerKind.DECLARATION)
                                                )
                                            (psiType as? PsiClassType)?.let { addReference(it) }
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

    override fun getTypeParameterList(): PsiTypeParameterList = _buildTypeParameterList

    private val paramsList: PsiParameterList
        get() = paramsListPart.getOrBuild {
            object : LightParameterListBuilder(containingClass.manager, containingClass.language) {
                override fun getParent(): PsiElement = this@UastFakeDescriptorLightMethod
                override fun getContainingFile(): PsiFile = parent.containingFile

                init {
                    val parameterList = this

                    originalDescriptor.extensionReceiverParameter?.let { receiver ->
                        this.addParameter(
                            UastDescriptorLightParameterBase(
                                "\$this\$${originalDescriptor.name.identifier}",
                                receiver.type.toPsiType(
                                    this@UastFakeDescriptorLightMethod,
                                    this@UastFakeDescriptorLightMethod.context,
                                    PsiTypeConversionConfiguration(TypeOwnerKind.DECLARATION)
                                ),
                                parameterList,
                                receiver
                            )
                        )
                    }

                    for (p in originalDescriptor.valueParameters) {
                        this.addParameter(
                            UastDescriptorLightParameter(
                                p.name.identifier,
                                p.type.toPsiType(
                                    this@UastFakeDescriptorLightMethod,
                                    this@UastFakeDescriptorLightMethod.context,
                                    PsiTypeConversionConfiguration(
                                        TypeOwnerKind.DECLARATION,
                                        typeMappingMode = KaTypeMappingMode.VALUE_PARAMETER
                                    )
                                ),
                                parameterList,
                                p
                            )
                        )
                    }

                    if (isSuspendFunction()) {
                        this.addParameter(
                            UastDescriptorLightSuspendContinuationParameter.create(
                                this@UastFakeDescriptorLightMethod,
                                originalDescriptor,
                                this@UastFakeDescriptorLightMethod.context
                            )
                        )
                    }
                }
            }
        }

    override fun getParameterList(): PsiParameterList = paramsList
}

internal abstract class UastFakeDescriptorLightMethodBase<T : CallableMemberDescriptor>(
    protected val original: T,
    containingClass: PsiClass,
    protected val context: KtElement,
) : UastFakeLightMethodBase(
    containingClass.manager,
    containingClass.language,
    original.name.identifier,
    LightParameterListBuilder(containingClass.manager, containingClass.language),
    LightModifierList(containingClass.manager),
    containingClass
) {

    init {
        if (original.dispatchReceiverParameter == null) {
            addModifier(PsiModifier.STATIC)
        }
    }

    private val returnTypePart = UastLazyPart<PsiType?>()

    override fun isSuspendFunction(): Boolean {
        return (original as? FunctionDescriptor)?.isSuspend == true
    }

    override fun isUnitFunction(): Boolean {
        return original is FunctionDescriptor && _returnType == PsiTypes.voidType()
    }

    override fun computeNullability(): KaTypeNullability? {
        if (isSuspendFunction()) {
            // suspend fun returns Any?, which is mapped to @Nullable java.lang.Object
            return KaTypeNullability.NULLABLE
        }
        return when (original.returnType?.nullability()) {
            null -> null
            TypeNullability.NOT_NULL -> KaTypeNullability.NON_NULLABLE
            TypeNullability.NULLABLE -> KaTypeNullability.NULLABLE
            else -> KaTypeNullability.UNKNOWN
        }
    }

    override fun computeAnnotations(annotations: SmartSet<PsiAnnotation>) {
        original.annotations.mapTo(annotations) { annoDescriptor ->
            annoDescriptor.toLightAnnotation(this)
        }
    }

    override fun isConstructor(): Boolean {
        return original is ConstructorDescriptor
    }

    private val _returnType: PsiType?
        get() = returnTypePart.getOrBuild {
            if (isSuspendFunction()) {
                // suspend fun returns Any?, which is mapped to @Nullable java.lang.Object
                return@getOrBuild PsiType.getJavaLangObject(context.manager, context.resolveScope)
            }
            original.returnType?.toPsiType(
                this,
                context,
                PsiTypeConversionConfiguration(
                    TypeOwnerKind.DECLARATION,
                    typeMappingMode = KaTypeMappingMode.RETURN_TYPE
                )
            )
        }

    override fun getReturnType(): PsiType? {
        return _returnType
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UastFakeDescriptorLightMethodBase<*>

        return original == other.original
    }

    override fun hashCode(): Int = original.hashCode()
}
