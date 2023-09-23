// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin.psi

import com.intellij.psi.*
import com.intellij.psi.impl.light.*
import org.jetbrains.kotlin.analysis.api.types.KtTypeMappingMode
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.asJava.classes.toLightAnnotation
import org.jetbrains.kotlin.asJava.elements.KotlinLightTypeParameterListBuilder
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.types.typeUtil.TypeNullability
import org.jetbrains.kotlin.types.typeUtil.nullability
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.uast.kotlin.PsiTypeConversionConfiguration
import org.jetbrains.uast.kotlin.TypeOwnerKind
import org.jetbrains.uast.kotlin.lz
import org.jetbrains.uast.kotlin.toPsiType

internal class UastFakeDescriptorLightMethod(
    original: SimpleFunctionDescriptor,
    containingClass: PsiClass,
    context: KtElement,
) : UastFakeDescriptorLightMethodBase<SimpleFunctionDescriptor>(original, containingClass, context) {

    private val _buildTypeParameterList by lz {
        KotlinLightTypeParameterListBuilder(this).also { paramList ->
            for ((i, p) in original.typeParameters.withIndex()) {
                paramList.addParameter(
                    object : LightTypeParameterBuilder(
                        p.name.identifier,
                        this,
                        i
                    ) {
                        private val myExtendsList by lz {
                            super.getExtendsList().apply {
                                p.upperBounds.forEach { bound ->
                                    val psiType =
                                        bound.toPsiType(
                                            this@UastFakeDescriptorLightMethod,
                                            context,
                                            PsiTypeConversionConfiguration(TypeOwnerKind.DECLARATION)
                                        )
                                    (psiType as? PsiClassType)?.let { addReference(it) }
                                }
                            }
                        }

                        override fun getExtendsList(): LightReferenceListBuilder = myExtendsList
                    }
                )
            }
        }
    }

    override fun getTypeParameterList(): PsiTypeParameterList = _buildTypeParameterList

    private val paramsList: PsiParameterList by lz {
        object : LightParameterListBuilder(containingClass.manager, containingClass.language) {
            override fun getParent(): PsiElement = this@UastFakeDescriptorLightMethod
            override fun getContainingFile(): PsiFile = parent.containingFile

            init {
                val parameterList = this

                original.extensionReceiverParameter?.let { receiver ->
                    this.addParameter(
                        UastDescriptorLightParameterBase(
                            "\$this\$${original.name.identifier}",
                            receiver.type.toPsiType(
                                this@UastFakeDescriptorLightMethod,
                                context,
                                PsiTypeConversionConfiguration(TypeOwnerKind.DECLARATION)
                            ),
                            parameterList,
                            receiver
                        )
                    )
                }

                for (p in original.valueParameters) {
                    this.addParameter(
                        UastDescriptorLightParameter(
                            p.name.identifier,
                            p.type.toPsiType(
                                this@UastFakeDescriptorLightMethod,
                                context,
                                PsiTypeConversionConfiguration(
                                    TypeOwnerKind.DECLARATION,
                                    typeMappingMode = KtTypeMappingMode.VALUE_PARAMETER
                                )
                            ),
                            parameterList,
                            p
                        )
                    )
                }

                if (isSuspendFunction()) {
                    this.addParameter(
                        UastDescriptorLightSuspendContinuationParameter.create(this@UastFakeDescriptorLightMethod, original, context)
                    )
                }
            }
        }
    }

    override fun getParameterList(): PsiParameterList = paramsList
}

internal abstract class UastFakeDescriptorLightMethodBase<T: CallableMemberDescriptor>(
    private val original: T,
    containingClass: PsiClass,
    private val context: KtElement,
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

    override fun isSuspendFunction(): Boolean {
        return (original as? FunctionDescriptor)?.isSuspend == true
    }

    override fun isUnitFunction(): Boolean {
        return original is FunctionDescriptor && _returnType == PsiTypes.voidType()
    }

    override fun computeNullability(): KtTypeNullability? {
        if (isSuspendFunction()) {
            // suspend fun returns Any?, which is mapped to @Nullable java.lang.Object
            return KtTypeNullability.NULLABLE
        }
        return when (original.returnType?.nullability()) {
            null -> null
            TypeNullability.NOT_NULL -> KtTypeNullability.NON_NULLABLE
            TypeNullability.NULLABLE -> KtTypeNullability.NULLABLE
            else -> KtTypeNullability.UNKNOWN
        }
    }

    override fun computeAnnotations(annotations: SmartList<PsiAnnotation>) {
        original.annotations.mapTo(annotations) { annoDescriptor ->
            annoDescriptor.toLightAnnotation(this)
        }
    }

    override fun isConstructor(): Boolean {
        return original is ConstructorDescriptor
    }

    private val _returnType: PsiType? by lz {
        if (isSuspendFunction()) {
            // suspend fun returns Any?, which is mapped to @Nullable java.lang.Object
            return@lz PsiType.getJavaLangObject(context.manager, context.resolveScope)
        }
        original.returnType?.toPsiType(
            this,
            context,
            PsiTypeConversionConfiguration(
                TypeOwnerKind.DECLARATION,
                typeMappingMode = KtTypeMappingMode.RETURN_TYPE
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
