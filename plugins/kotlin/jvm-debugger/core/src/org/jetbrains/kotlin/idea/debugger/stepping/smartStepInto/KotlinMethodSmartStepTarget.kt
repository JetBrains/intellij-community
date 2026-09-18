// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.stepping.smartStepInto

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.serviceOrNull
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.tree.TokenSet
import com.intellij.util.Range
import com.intellij.xdebugger.stepping.ForceSmartStepIntoSource
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.rendering.KaPiece
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderer
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderingOption
import org.jetbrains.kotlin.analysis.api.rendering.KaTextAttribute
import org.jetbrains.kotlin.analysis.api.rendering.append
import org.jetbrains.kotlin.analysis.api.rendering.output
import org.jetbrains.kotlin.analysis.api.rendering.push
import org.jetbrains.kotlin.analysis.api.rendering.render
import org.jetbrains.kotlin.analysis.api.rendering.renderToString
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.debugger.base.util.runDumbAnalyze
import org.jetbrains.kotlin.idea.debugger.core.getClassName
import org.jetbrains.kotlin.idea.util.withReflectionTypesAsFunctionTypes
import org.jetbrains.kotlin.idea.util.withShortNames
import org.jetbrains.kotlin.idea.util.withValueParameterTypesOnly
import org.jetbrains.kotlin.idea.util.withoutAnnotations
import org.jetbrains.kotlin.idea.util.withoutDeclarationKeywords
import org.jetbrains.kotlin.idea.util.withoutModifiers
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KotlinDeclarationNavigationPolicy
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.name.render
import javax.swing.Icon

context(session: KaSession)
fun calcLabel(symbol: KaDeclarationSymbol): String {
    return RENDERER.renderToString(symbol)
}

class KotlinMethodSmartStepTarget(
    lines: Range<Int>,
    highlightElement: PsiElement,
    label: String,
    declaration: KtDeclaration?,
    val ordinal: Int,
    val methodInfo: CallableMemberInfo
) : KotlinSmartStepTarget(label, highlightElement, false, lines), ForceSmartStepIntoSource {

    private val declarationPtr = declaration?.fetchNavigationElement()?.createSmartPointer()

    init {
        assert(declaration != null || methodInfo.isInvoke)
    }

    override fun getIcon(): Icon = if (methodInfo.isExtension) KotlinIcons.EXTENSION_FUNCTION else KotlinIcons.FUNCTION
    override fun getClassName(): String? {
        val declaration = getDeclaration() ?: return null
        return runDumbAnalyze(declaration, fallback = null) { getClassName(declaration) }
    }

    override fun needForceSmartStepInto(): Boolean = methodInfo.isInvoke && methodInfo.isSuspend

    fun getDeclaration(): KtDeclaration? =
        declarationPtr.getElementInReadAction()

    override fun createMethodFilter(): KotlinMethodFilter {
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


/**
 * Renders a smart-step-into label: the declaration name and its parameter types, without modifiers, annotations, receiver, type parameters or
 * return type. The `constructor`, `get` and `set` keywords are kept, so an accessor target reads `Foo.get()`.
 */
private val RENDERER: KaRenderer = KaRenderer.withShortNames.copy {
    withoutAnnotations()
    withoutModifiers()
    withoutDeclarationKeywords(except = TokenSet.create(KtTokens.GET_KEYWORD, KtTokens.SET_KEYWORD))
    withValueParameterTypesOnly()
    withReflectionTypesAsFunctionTypes()
    set(KaRenderingOption.ContextParametersOnNewLine) { _ -> false }

    push(KaPiece.NamedFunction) { function ->
        render(function, KaPiece.ContextParameterList)
        render(function, KaPiece.SymbolName)
        render(function, KaPiece.ValueParameterList)
    }

    push(KaPiece.Constructor) { constructor ->
        output.append(KtTokens.CONSTRUCTOR_KEYWORD.value, KaTextAttribute.Keyword).space()
        constructor.containingClassId?.shortClassName?.let { output.append(it.render(), KaTextAttribute.Identifier) }
        render(constructor, KaPiece.ValueParameterList)
    }

    push(KaPiece.Property) { render(it, KaPiece.SymbolName) }
}

private fun KtDeclaration.fetchNavigationElement(): KtDeclaration =
    serviceOrNull<KotlinDeclarationNavigationPolicy>()?.getNavigationElement(this) as? KtDeclaration
        ?: this
