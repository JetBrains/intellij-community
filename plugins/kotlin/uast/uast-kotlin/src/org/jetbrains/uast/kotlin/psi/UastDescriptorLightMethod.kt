// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin.psi

import com.intellij.psi.*
import com.intellij.psi.impl.light.*
import org.jetbrains.kotlin.asJava.elements.KotlinLightTypeParameterListBuilder
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.uast.kotlin.PsiTypeConversionConfiguration
import org.jetbrains.uast.kotlin.TypeOwnerKind
import org.jetbrains.uast.kotlin.lz
import org.jetbrains.uast.kotlin.toPsiType

internal class UastDescriptorLightMethod(
    original: SimpleFunctionDescriptor,
    containingClass: PsiClass,
    context: KtElement,
) : UastDescriptorLightMethodBase<SimpleFunctionDescriptor>(original, containingClass, context) {

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
                                            this@UastDescriptorLightMethod,
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
            override fun getParent(): PsiElement = this@UastDescriptorLightMethod
            override fun getContainingFile(): PsiFile = parent.containingFile

            init {
                val parameterList = this

                original.extensionReceiverParameter?.let { receiver ->
                    this.addParameter(
                        UastDescriptorLightParameterBase(
                            "\$this\$${original.name.identifier}",
                            receiver.type.toPsiType(
                                this@UastDescriptorLightMethod,
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
                                this@UastDescriptorLightMethod,
                                context,
                                PsiTypeConversionConfiguration(TypeOwnerKind.DECLARATION)
                            ),
                            parameterList,
                            p
                        )
                    )
                }
            }
        }
    }

    override fun getParameterList(): PsiParameterList = paramsList
}

internal abstract class UastDescriptorLightMethodBase<T: CallableMemberDescriptor>(
    internal val original: T,
    containingClass: PsiClass,
    protected val context: KtElement,
) : LightMethodBuilder(
    containingClass.manager, containingClass.language, original.name.identifier,
    LightParameterListBuilder(containingClass.manager, containingClass.language),
    LightModifierList(containingClass.manager)
) {

    init {
        this.containingClass = containingClass
        if (original.dispatchReceiverParameter == null) {
            addModifier(PsiModifier.STATIC)
        }
    }

    override fun getReturnType(): PsiType? {
        return original.returnType?.toPsiType(
            this,
            context,
            PsiTypeConversionConfiguration(TypeOwnerKind.DECLARATION)
        )
    }

    override fun getParent(): PsiElement? = containingClass

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UastDescriptorLightMethodBase<*>

        if (original != other.original) return false

        return true
    }

    override fun hashCode(): Int = original.hashCode()
}
