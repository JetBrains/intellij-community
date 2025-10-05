// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.psi.*
import com.intellij.psi.impl.light.LightModifierList
import com.intellij.psi.impl.light.LightParameterListBuilder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.utils.SmartSet
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild

@ApiStatus.Internal
abstract class UastFakeSourceLightMethodBase<T : KtDeclaration>(
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

    private val modalityPart = UastLazyPart<Modality?>()

    private val _modality: Modality?
        get() = modalityPart.getOrBuild {
            baseResolveProviderService.modality(original)
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
                original.hasModifier(KtTokens.ABSTRACT_KEYWORD) ||
                        _modality == Modality.ABSTRACT ||
                        containingClass?.isInterface == true
            }

            PsiModifier.OPEN -> {
                original.hasModifier(KtTokens.OPEN_KEYWORD) ||
                        _modality == Modality.OPEN
            }

            PsiModifier.FINAL -> {
                original.hasModifier(KtTokens.FINAL_KEYWORD) ||
                        _modality == Modality.FINAL
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

    override fun computeNullability(): KaTypeNullability? {
        if (baseResolveProviderService.hasInheritedGenericType(original)) {
            // Inherited generic type: nullity will be determined at use-site
            return null
        }
        if (isSuspendFunction()) {
            // suspend fun returns Any?, which is mapped to @Nullable java.lang.Object
            return KaTypeNullability.NULLABLE
        }
        return baseResolveProviderService.nullability(original)
    }

    override fun computeAnnotations(annotations: SmartSet<PsiAnnotation>) {
        original.annotationEntries.mapNotNullTo(annotations) { entry ->
            // Creation of PsiAnnotation may vary between frontends. For example,
            // SLC doesn't model a declaration with value class in its signature
            // and K2 UAST will fake it, while K1 doesn't need to.
            baseResolveProviderService.convertToPsiAnnotation(entry)
        }
    }

    override fun isConstructor(): Boolean {
        return original is KtConstructor<*>
    }

    private val returnTypePart = UastLazyPart<PsiType?>()

    private val _returnType: PsiType?
        get() = returnTypePart.getOrBuild {
            if (isSuspendFunction()) {
                // suspend fun returns Any?, which is mapped to @Nullable java.lang.Object
                return@getOrBuild PsiType.getJavaLangObject(original.manager, original.resolveScope)
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

    override fun toString(): String = "${this::class.simpleName} of ${original.name}"
}
