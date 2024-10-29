// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.debugger.engine.MethodFilter
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.serviceOrNull
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.util.Range
import com.intellij.xdebugger.stepping.ForceSmartStepIntoSource
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaCallableReturnTypeFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaDeclarationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.KaDeclarationModifiersRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaModifierListRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererKeywordFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.KaTypeParameterRendererFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaCallableReceiverRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaConstructorSymbolRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KaValueParameterSymbolRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KaFunctionalTypeRenderer
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.debugger.base.util.runDumbAnalyze
import org.jetbrains.kotlin.idea.debugger.core.getClassName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KotlinDeclarationNavigationPolicy
import org.jetbrains.kotlin.psi.KtDeclaration
import javax.swing.Icon

class KotlinMethodSmartStepTarget(
    lines: Range<Int>,
    highlightElement: PsiElement,
    label: String,
    declaration: KtDeclaration?,
    val ordinal: Int,
    val methodInfo: CallableMemberInfo
) : KotlinSmartStepTarget(label, highlightElement, false, lines), ForceSmartStepIntoSource {
    companion object {
        @KaExperimentalApi
        private val renderer = KaDeclarationRendererForSource.WITH_QUALIFIED_NAMES.with {
            annotationRenderer = annotationRenderer.with {
                annotationFilter = KaRendererAnnotationsFilter.NONE
            }
            keywordsRenderer = keywordsRenderer.with {
                keywordFilter = KaRendererKeywordFilter.onlyWith(KtTokens.CONSTRUCTOR_KEYWORD, KtTokens.GET_KEYWORD, KtTokens.SET_KEYWORD)
            }
            modifiersRenderer = modifiersRenderer.with {
                modifierListRenderer = NO_MODIFIER_LIST
            }
            typeRenderer = KaTypeRendererForSource.WITH_SHORT_NAMES.with {
                functionalTypeRenderer = KaFunctionalTypeRenderer.AS_FUNCTIONAL_TYPE
            }
            returnTypeFilter = NO_RETURN_TYPE
            typeParametersFilter = KaTypeParameterRendererFilter { _, _ -> false }
            constructorRenderer = KaConstructorSymbolRenderer.AS_RAW_SIGNATURE
            valueParameterRenderer = KaValueParameterSymbolRenderer.TYPE_ONLY
            callableReceiverRenderer = NO_CALLABLE_RECEIVER
        }

        context(KaSession)
        @OptIn(KaExperimentalApi::class)
        fun calcLabel(symbol: KaDeclarationSymbol): String {
            return symbol.render(renderer)
        }
    }

    private val declarationPtr = declaration?.fetchNavigationElement()?.createSmartPointer()

    init {
        assert(declaration != null || methodInfo.isInvoke)
    }

    override fun getIcon(): Icon = if (methodInfo.isExtension) KotlinIcons.EXTENSION_FUNCTION else KotlinIcons.FUNCTION
    override fun getClassName(): String? {
        val declaration = getDeclaration() ?: return null
        return runDumbAnalyze(declaration, fallback = null) { declaration.getClassName() }
    }

    override fun needForceSmartStepInto() = methodInfo.isInvoke && methodInfo.isSuspend

    fun getDeclaration(): KtDeclaration? =
        declarationPtr.getElementInReadAction()

    override fun createMethodFilter(): MethodFilter {
        val declaration = declarationPtr.getElementInReadAction()
        return KotlinMethodFilter(declaration, callingExpressionLines, methodInfo)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        if (other == null || other !is KotlinMethodSmartStepTarget) return false

        if (methodInfo.isInvoke && other.methodInfo.isInvoke) {
            // Don't allow to choose several invoke targets in smart step into as we can't distinguish them reliably during debug
            return true
        }
        return highlightElement === other.highlightElement
    }

    override fun hashCode(): Int {
        if (methodInfo.isInvoke) {
            // Predefined value to make all FunctionInvokeDescriptor targets equal
            return 42
        }
        return highlightElement.hashCode()
    }
}

internal fun <T : PsiElement> SmartPsiElementPointer<T>?.getElementInReadAction(): T? =
    this?.let { runReadAction { element } }


@KaExperimentalApi
private val NO_RETURN_TYPE = object : KaCallableReturnTypeFilter {
    override fun shouldRenderReturnType(analysisSession: KaSession, type: KaType, symbol: KaCallableSymbol): Boolean = false
}

@KaExperimentalApi
private val NO_CALLABLE_RECEIVER = object : KaCallableReceiverRenderer {
    override fun renderReceiver(
        analysisSession: KaSession,
        symbol: KaReceiverParameterSymbol,
        declarationRenderer: KaDeclarationRenderer,
        printer: PrettyPrinter
    ) {}
}

@KaExperimentalApi
private val NO_MODIFIER_LIST = object : KaModifierListRenderer {
    override fun renderModifiers(
        analysisSession: KaSession,
        symbol: KaDeclarationSymbol,
        declarationModifiersRenderer: KaDeclarationModifiersRenderer,
        printer: PrettyPrinter
    ) {}

}

private fun KtDeclaration.fetchNavigationElement(): KtDeclaration =
    serviceOrNull<KotlinDeclarationNavigationPolicy>()?.getNavigationElement(this) as? KtDeclaration
        ?: this
