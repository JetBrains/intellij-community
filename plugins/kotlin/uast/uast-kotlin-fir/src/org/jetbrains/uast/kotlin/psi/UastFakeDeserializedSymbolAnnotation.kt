// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.*
import com.intellij.psi.impl.PsiImplUtil
import com.intellij.psi.impl.compiled.ClsJavaCodeReferenceElementImpl
import com.intellij.psi.impl.light.LightIdentifier
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.renderAsSourceCode
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KaSymbolPointer
import org.jetbrains.kotlin.asJava.classes.cannotModify
import org.jetbrains.kotlin.asJava.elements.KtLightAbstractAnnotation
import org.jetbrains.kotlin.asJava.elements.KtLightElementBase
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild
import org.jetbrains.uast.kotlin.internal.analyzeForUast

internal class UastFakeDeserializedSymbolAnnotation(
    private val parentOriginal: KaSymbolPointer<KaDeclarationSymbol>,
    private val classId: ClassId?,
    private val parent: KtElement,
) : KtLightAbstractAnnotation(parent) {
    override val kotlinOrigin: KtCallElement?
        get() = null

    override fun getQualifiedName(): @NlsSafe String? =
        classId?.asFqNameString()

    private val nameReferencePart = UastLazyPart<PsiJavaCodeReferenceElement?>()

    override fun getNameReferenceElement(): PsiJavaCodeReferenceElement? =
        nameReferencePart.getOrBuild {
            classId?.asFqNameString()?.let {
                ClsJavaCodeReferenceElementImpl(parent, it)
            }
        }

    private val parameterListPart = UastLazyPart<PsiAnnotationParameterList>()

    override fun getParameterList(): PsiAnnotationParameterList =
        parameterListPart.getOrBuild {
            ParameterListImpl(this)
        }

    private inner class ParameterListImpl(
        parent: PsiElement,
    ) : KtLightElementBase(parent), PsiAnnotationParameterList {
        override val kotlinOrigin: KtElement?
            get() = null

        private val attributePart = UastLazyPart<Array<out PsiNameValuePair?>>()

        override fun getAttributes(): Array<out PsiNameValuePair?> =
            attributePart.getOrBuild {
                val parent = parent as UastFakeDeserializedSymbolAnnotation
                val classId = parent.classId ?: return@getOrBuild emptyArray()
                analyzeForUast(parent.parent) {
                    val functionSymbol = parentOriginal.restoreSymbol() ?: return@getOrBuild emptyArray()
                    val anno = functionSymbol.annotations[classId].singleOrNull() ?: return@getOrBuild emptyArray()
                    anno.arguments.map { annotationValue ->
                        PsiNameValuePairForAnnotationArgument(
                            annotationValue.name.identifier,
                            annotationValue.expression.toPsiAnnotationMemberValue(),
                            this@ParameterListImpl
                        )
                    }.toTypedArray()
                }
            }
    }

    private class PsiNameValuePairForAnnotationArgument(
        private val _name: String,
        private val _value: PsiAnnotationMemberValue?,
        parent: PsiElement,
    ) : KtLightElementBase(parent), PsiNameValuePair {
        override val kotlinOrigin: KtElement?
            get() = null

        override fun getNameIdentifier(): PsiIdentifier =
            LightIdentifier(parent.manager, _name)

        override fun getName(): @NonNls String =
            _name

        override fun getValue(): PsiAnnotationMemberValue? =
            _value

        override fun getLiteralValue(): String? =
            (_value as? PsiLiteralExpression)?.value?.toString()

        override fun setValue(newValue: PsiAnnotationMemberValue): PsiAnnotationMemberValue =
            cannotModify()
    }

    private fun KaAnnotationValue.toPsiAnnotationMemberValue(): PsiAnnotationMemberValue? {
        val factory = PsiElementFactory.getInstance(project)
        return try {
            factory.createExpressionFromText(this.renderAsSourceCode(), parent)
        } catch (_ : IncorrectOperationException) {
            null
        }
    }

    override fun findAttributeValue(attributeName: @NonNls String?): PsiAnnotationMemberValue? =
        PsiImplUtil.findAttributeValue(this, attributeName)

    override fun findDeclaredAttributeValue(attributeName: @NonNls String?): PsiAnnotationMemberValue? =
        PsiImplUtil.findDeclaredAttributeValue(this, attributeName)

    override fun <T : PsiAnnotationMemberValue?> setDeclaredAttributeValue(attributeName: @NonNls String?, value: T?): T =
        cannotModify()
}