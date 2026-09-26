// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename.handlers

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.elements.KtLightMember
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtPrimaryConstructor

internal class RenameSyntheticDeclarationByReferenceHandler : AbstractForbidRenamingSymbolByReferenceHandler() {
    override fun shouldForbidRenamingFromJava(file: PsiFile, editor: Editor): Boolean {
        if (file is PsiJavaFile) {
            val targetElement = TargetElementUtil.findTargetElement(editor, TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED) as? KtLightElement<*, *>
                ?: return false

            return shouldForbidRenamingFromJava(targetElement)
        }
        return false
    }

    context(_: KaSession)
    override fun shouldForbidRenaming(symbol: KaSymbol): Boolean {
        return symbol.origin == KaSymbolOrigin.SOURCE_MEMBER_GENERATED && !(symbol is KaConstructorSymbol && symbol.isPrimary)
    }

    override fun getErrorMessage(): @DialogMessage String {
        return KotlinBundle.message("text.rename.is.not.applicable.to.synthetic.declarations")
    }
}

fun shouldForbidRenamingFromJava(targetElement: KtLightElement<*, *>): Boolean {
    val kotlinOrigin = targetElement.kotlinOrigin
    return when (targetElement) {
        is KtLightClass -> kotlinOrigin !is KtClassOrObject
        is KtLightMember<*> -> kotlinOrigin is KtPrimaryConstructor || kotlinOrigin !is KtCallableDeclaration
        else -> false
    }
}