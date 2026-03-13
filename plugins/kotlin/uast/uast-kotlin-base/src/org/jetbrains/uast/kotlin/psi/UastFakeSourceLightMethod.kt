// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin.psi

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiTypeParameterList
import com.intellij.psi.impl.light.LightParameterListBuilder
import com.intellij.psi.impl.light.LightReferenceListBuilder
import com.intellij.psi.impl.light.LightTypeParameterBuilder
import com.intellij.psi.impl.light.LightTypeParameterListBuilder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.elements.PsiElementWithOrigin
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.uast.UastErrorType
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild

@ApiStatus.Internal
open class UastFakeSourceLightMethod(
    original: KtFunction,
    containingClass: PsiClass,
) : UastFakeSourceLightMethodBase<KtFunction>(original, containingClass) {

    private val typeParameterListPart = UastLazyPart<PsiTypeParameterList>()
    private val parameterListPart = UastLazyPart<PsiParameterList>()

    override fun getTypeParameterList(): PsiTypeParameterList {
        return typeParameterListPart.getOrBuild {
            val parameterListBuilder = object : LightTypeParameterListBuilder(this.manager, KotlinLanguage.INSTANCE) {
                override fun getParent(): PsiElement = this@UastFakeSourceLightMethod
                override fun getContainingFile(): PsiFile = parent.containingFile
            }
            parameterListBuilder.also { paramList ->
                for ((i, p) in original.typeParameters.withIndex()) {
                    paramList.addParameter(
                        object : LightTypeParameterBuilder(
                            p.name ?: "__no_name__",
                            this,
                            i,
                        ), PsiElementWithOrigin<KtTypeParameter> {
                            private val myExtendsListPart = UastLazyPart<LightReferenceListBuilder>()

                            override val origin: KtTypeParameter = p

                            override fun getExtendsList(): LightReferenceListBuilder {
                                return myExtendsListPart.getOrBuild {
                                    super.getExtendsList().apply {
                                        p.extendsBound?.let { extendsBound ->
                                            val psiType = baseResolveProviderService.resolveToType(
                                                extendsBound,
                                                this@UastFakeSourceLightMethod
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
    }

    override fun getParameterList(): PsiParameterList {
        return parameterListPart.getOrBuild {
            object : LightParameterListBuilder(original.manager, original.language) {
                override fun getParent(): PsiElement = this@UastFakeSourceLightMethod
                override fun getContainingFile(): PsiFile = parent.containingFile

                init {
                    val parameterList = this

                    original.receiverTypeReference?.let { receiver ->
                        addParameter(
                            UastKotlinPsiParameterBase(
                                "\$this\$${original.name}",
                                parameterList,
                                receiver,
                                isVarArgs = false,
                                ktDefaultValue = null,
                            ) {
                                baseResolveProviderService.resolveToType(receiver, this@UastFakeSourceLightMethod) ?: UastErrorType
                            }
                        )
                    }

                    for ((i, p) in original.valueParameters.withIndex()) {
                        addParameter(
                            UastKotlinPsiParameter(
                                p.name ?: "p$i",
                                parameterList,
                                p.isVarArg,
                                p.defaultValue,
                                p
                            )
                        )
                    }

                    if (isSuspendFunction()) {
                        addParameter(UastKotlinPsiSuspendContinuationParameter(this@UastFakeSourceLightMethod, original))
                    }
                }
            }
        }
    }
}
