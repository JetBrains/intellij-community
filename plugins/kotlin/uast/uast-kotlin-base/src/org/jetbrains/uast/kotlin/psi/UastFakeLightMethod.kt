// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin.psi

import com.intellij.openapi.components.ServiceManager
import com.intellij.psi.*
import com.intellij.psi.impl.light.*
import org.jetbrains.kotlin.asJava.elements.KotlinLightTypeParameterListBuilder
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.uast.UastErrorType
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.lz

class UastFakeLightMethod(
    original: KtFunction,
    containingClass: PsiClass,
) : UastFakeLightMethodBase<KtFunction>(original, containingClass) {

    private val _typeParameterList by lz {
        KotlinLightTypeParameterListBuilder(this).also { paramList ->
            for ((i, p) in original.typeParameters.withIndex()) {
                paramList.addParameter(
                    object : LightTypeParameterBuilder(
                        p.name ?: "__no_name__",
                        this,
                        i
                    ) {
                        private val myExtendsList by lz {
                            super.getExtendsList().apply {
                                p.extendsBound?.let { extendsBound ->
                                    baseResolveProviderService.resolveToType(extendsBound, this@UastFakeLightMethod)
                                        ?.safeAs<PsiClassType>()
                                        ?.let { addReference(it) }
                                }
                            }
                        }

                        override fun getExtendsList(): LightReferenceListBuilder = myExtendsList
                    }
                )
            }
        }
    }

    override fun getTypeParameterList(): PsiTypeParameterList = _typeParameterList

    private val _parameterList: PsiParameterList by lz {
        object : LightParameterListBuilder(original.manager, original.language) {
            override fun getParent(): PsiElement = this@UastFakeLightMethod
            override fun getContainingFile(): PsiFile = parent.containingFile

            init {
                val parameterList = this

                original.receiverTypeReference?.let { receiver ->
                    this.addParameter(
                        UastKotlinPsiParameterBase(
                            "\$this\$${original.name}",
                            baseResolveProviderService.resolveToType(receiver, this@UastFakeLightMethod)
                                ?: UastErrorType,
                            parameterList,
                            receiver
                        )
                    )
                }

                for ((i, p) in original.valueParameters.withIndex()) {
                    this.addParameter(
                        UastKotlinPsiParameter(
                            p.name ?: "p$i",
                            p.typeReference?.let { typeReference ->
                                baseResolveProviderService.resolveToType(typeReference, this@UastFakeLightMethod)
                            } ?: UastErrorType,
                            parameterList,
                            original.language,
                            p.isVarArg,
                            p.defaultValue,
                            p
                        )
                    )
                }
            }
        }
    }

    override fun getParameterList(): PsiParameterList = _parameterList
}

class UastFakeLightPrimaryConstructor(
    original: KtClassOrObject,
    lightClass: PsiClass,
) : UastFakeLightMethodBase<KtClassOrObject>(original, lightClass) {
    override fun isConstructor(): Boolean = true
}

abstract class UastFakeLightMethodBase<T: KtDeclaration>(
    val original: T,
    containingClass: PsiClass,
) : LightMethodBuilder(
    original.manager,
    original.language,
    original.name ?: "<no name provided>",
    LightParameterListBuilder(original.manager, original.language),
    LightModifierList(original.manager)
) {

    init {
        this.containingClass = containingClass
        if (original.safeAs<KtNamedFunction>()?.isTopLevel == true) {
            addModifier(PsiModifier.STATIC)
        }
    }

    protected val baseResolveProviderService: BaseKotlinUastResolveProviderService by lz {
        ServiceManager.getService(BaseKotlinUastResolveProviderService::class.java)
            ?: error("${BaseKotlinUastResolveProviderService::class.java.name} is not available for ${this::class.simpleName}")
    }

    override fun getReturnType(): PsiType? {
        return baseResolveProviderService.getType(original, this)
    }

    override fun getParent(): PsiElement? = containingClass

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UastFakeLightMethodBase<*>

        if (original != other.original) return false

        return true
    }

    override fun hashCode(): Int = original.hashCode()
}
