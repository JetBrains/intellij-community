// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin.psi

import com.intellij.psi.*
import com.intellij.psi.impl.light.LightModifierList
import com.intellij.psi.impl.light.LightParameterListBuilder
import com.intellij.psi.impl.light.LightReferenceListBuilder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.asJava.elements.KotlinLightTypeParameterBuilder
import org.jetbrains.kotlin.asJava.elements.KotlinLightTypeParameterListBuilder
import org.jetbrains.kotlin.asJava.elements.KtLightAnnotationForSourceEntry
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.uast.UastErrorType
import org.jetbrains.uast.kotlin.lz

@ApiStatus.Internal
open class UastFakeSourceLightMethod(
    original: KtFunction,
    containingClass: PsiClass,
) : UastFakeSourceLightMethodBase<KtFunction>(original, containingClass) {

    private val _typeParameterList by lz {
        KotlinLightTypeParameterListBuilder(this).also { paramList ->
            for ((i, p) in original.typeParameters.withIndex()) {
                paramList.addParameter(
                    object : KotlinLightTypeParameterBuilder(
                        p.name ?: "__no_name__",
                        this,
                        i,
                        p
                    ) {
                        private val myExtendsList by lz {
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

                        override fun getExtendsList(): LightReferenceListBuilder = myExtendsList
                    }
                )
            }
        }
    }

    override fun getTypeParameterList(): PsiTypeParameterList = _typeParameterList

    private val _parameterList: PsiParameterList by lz {
        object : LightParameterListBuilder(original.manager, original.language) {
            override fun getParent(): PsiElement = this@UastFakeSourceLightMethod
            override fun getContainingFile(): PsiFile = parent.containingFile

            init {
                val parameterList = this

                original.receiverTypeReference?.let { receiver ->
                    this.addParameter(
                        UastKotlinPsiParameterBase(
                            "\$this\$${original.name}",
                            baseResolveProviderService.resolveToType(receiver, this@UastFakeSourceLightMethod)
                                ?: UastErrorType,
                            parameterList,
                            receiver
                        )
                    )
                }

                for ((i, p) in original.valueParameters.withIndex()) {
                    val type = baseResolveProviderService.getType(p, this@UastFakeSourceLightMethod, isForFake = true)
                        ?: UastErrorType
                    val adjustedType = if (p.isVarArg && type is PsiArrayType)
                        PsiEllipsisType(type.componentType, type.annotationProvider)
                    else type
                    this.addParameter(
                        UastKotlinPsiParameter(
                            baseResolveProviderService,
                            p.name ?: "p$i",
                            adjustedType,
                            parameterList,
                            original.language,
                            p.isVarArg,
                            p.defaultValue,
                            p
                        )
                    )
                }

                if (isSuspendFunction()) {
                    this.addParameter(
                        UastKotlinPsiSuspendContinuationParameter.create(this@UastFakeSourceLightMethod, original)
                    )
                }
            }
        }
    }

    override fun getParameterList(): PsiParameterList = _parameterList
}

@ApiStatus.Internal
class UastFakeSourceLightPrimaryConstructor(
    original: KtClassOrObject,
    lightClass: PsiClass,
) : UastFakeSourceLightMethodBase<KtClassOrObject>(original, lightClass) {
    override fun isConstructor(): Boolean = true
}

@ApiStatus.Internal
abstract class UastFakeSourceLightMethodBase<T: KtDeclaration>(
    internal val original: T,
    containingClass: PsiClass,
) : UastFakeLightMethodBase(
    original.manager,
    original.language,
    original.name ?: "<no name provided>",
    LightParameterListBuilder(original.manager, original.language),
    LightModifierList(original.manager),
    containingClass
) {

    init {
        if ((original as? KtNamedFunction)?.isTopLevel == true) {
            addModifier(PsiModifier.STATIC)
        }
    }

    override fun hasModifierProperty(name: String): Boolean {
        return when (name) {
            PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PRIVATE -> {
                if (original.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
                    return name == PsiModifier.PRIVATE
                }
                if (original.hasModifier(KtTokens.PROTECTED_KEYWORD)) {
                    return name == PsiModifier.PROTECTED
                }

                // TODO: inherited via override

                name == PsiModifier.PUBLIC
            }
            PsiModifier.ABSTRACT -> {
                original.hasModifier(KtTokens.ABSTRACT_KEYWORD) || containingClass?.isInterface == true
            }
            PsiModifier.OPEN -> {
                original.hasModifier(KtTokens.OPEN_KEYWORD)
            }
            PsiModifier.FINAL -> {
                // TODO: top-level / unspecified declaration / inside final containingClass?
                original.hasModifier(KtTokens.FINAL_KEYWORD)
            }
            // TODO: special keywords, such as strictfp, synchronized, external, native, etc.
            else -> super.hasModifierProperty(name)
        }
    }

    override fun isSuspendFunction(): Boolean {
        return original.hasModifier(KtTokens.SUSPEND_KEYWORD)
    }

    override fun isUnitFunction(): Boolean {
        return original is KtFunction && _returnType == PsiTypes.voidType()
    }

    override fun computeNullability(): KtTypeNullability? {
        if (isSuspendFunction()) {
            // suspend fun returns Any?, which is mapped to @Nullable java.lang.Object
            return KtTypeNullability.NULLABLE
        }
        return baseResolveProviderService.nullability(original)
    }

    override fun computeAnnotations(annotations: SmartList<PsiAnnotation>) {
        original.annotationEntries.mapTo(annotations) { entry ->
            KtLightAnnotationForSourceEntry(
                name = entry.shortName?.identifier,
                lazyQualifiedName = { baseResolveProviderService.qualifiedAnnotationName(entry) },
                kotlinOrigin = entry,
                parent = original,
            )
        }
    }

    override fun isConstructor(): Boolean {
        return original is KtConstructor<*>
    }

    private val _returnType: PsiType? by lz {
        if (isSuspendFunction()) {
            // suspend fun returns Any?, which is mapped to @Nullable java.lang.Object
            return@lz PsiType.getJavaLangObject(original.manager, original.resolveScope)
        }
        baseResolveProviderService.getType(original, this, isForFake = true)
    }

    override fun getReturnType(): PsiType? {
        return _returnType
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UastFakeSourceLightMethodBase<*>

        return original == other.original
    }

    override fun hashCode(): Int = original.hashCode()
}
