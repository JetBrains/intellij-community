// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin.psi

import com.intellij.psi.*
import com.intellij.psi.impl.light.LightModifierList
import com.intellij.psi.impl.light.LightParameterListBuilder
import org.jetbrains.kotlin.analysis.api.annotations.annotations
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.asJava.toLightAnnotation
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.uast.UastErrorType
import org.jetbrains.uast.UastLazyPart
import org.jetbrains.uast.getOrBuild
import org.jetbrains.uast.kotlin.internal.analyzeForUast

/**
 * A fake light method from binary, which is not materialized for some reason (e.g., `inline`)
 *
 * Due to its origin, BINARY, we don't have source PSI, but at least we have a pointer to
 * Analysis API symbol if it's resolved.
 */
internal class UastFakeDeserializedSymbolLightMethod(
    private val original: KtSymbolPointer<KtFunctionSymbol>,
    name: String,
    containingClass: PsiClass,
    private val context: KtElement,
) : UastFakeLightMethodBase(
    context.manager,
    context.language,
    name,
    LightParameterListBuilder(context.manager, context.language),
    LightModifierList(context.manager),
    containingClass
) {
    private val _isSuspend = UastLazyPart<Boolean>()

    override fun isSuspendFunction(): Boolean =
        _isSuspend.getOrBuild {
            analyzeForUast(context) {
                val functionSymbol = original.restoreSymbol() ?: return@analyzeForUast false
                functionSymbol.isSuspend
            }
        }

    private val _isUnit = UastLazyPart<Boolean>()

    override fun isUnitFunction(): Boolean =
        _isUnit.getOrBuild {
            analyzeForUast(context) {
                val functionSymbol = original.restoreSymbol() ?: return@analyzeForUast false
                functionSymbol.returnType.isUnit
            }
        }

    override fun computeNullability(): KtTypeNullability? {
        return analyzeForUast(context) {
            val functionSymbol = original.restoreSymbol() ?: return@analyzeForUast null
            functionSymbol.returnType.nullability
        }
    }

    override fun computeAnnotations(annotations: SmartList<PsiAnnotation>) {
        analyzeForUast(context) {
            val functionSymbol = original.restoreSymbol() ?: return
            functionSymbol.annotations.forEach { annoApp ->
                annoApp.psi?.toLightAnnotation()?.let { annotations.add(it) }
            }
        }
    }

    private val parameterListPart = UastLazyPart<PsiParameterList>()

    override fun getParameterList(): PsiParameterList =
        parameterListPart.getOrBuild {
            object : LightParameterListBuilder(context.manager, context.language) {
                override fun getParent(): PsiElement = this@UastFakeDeserializedSymbolLightMethod
                override fun getContainingFile(): PsiFile = parent.containingFile

                init {
                    val parameterList = this
                    val context = this@UastFakeDeserializedSymbolLightMethod.context

                    analyzeForUast(context) l@{
                        val functionSymbol = original.restoreSymbol() ?: return@l
                        (functionSymbol.receiverParameter?.psi as? KtTypeReference)?.let { receiver ->
                            parameterList.addParameter(
                                UastKotlinPsiParameterBase(
                                    "\$this\$$name",
                                    functionSymbol.receiverType?.asPsiType(context, allowErrorTypes = true) ?: UastErrorType,
                                    parameterList,
                                    receiver
                                )
                            )
                        }

                        for (p in functionSymbol.valueParameters) {
                            val type = p.returnType.asPsiType(context, allowErrorTypes = true) ?: UastErrorType
                            val adjustedType = if (p.isVararg && type is PsiArrayType)
                                PsiEllipsisType(type.componentType, type.annotationProvider)
                            else type
                            parameterList.addParameter(
                                UastKotlinPsiParameterBase(
                                    p.name.identifier,
                                    adjustedType,
                                    parameterList,
                                    (p.psi as? KtElement) ?: context
                                )
                            )
                        }
                    }
                }
            }
        }
}
