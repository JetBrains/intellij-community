// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.navigation

import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.psi.KtClassOrObject

abstract class AbstractMoveToNextMethodTest : AbstractGotoActionTest() {
    override val actionName: String
        /**
         * see [com.intellij.codeInsight.navigation.actions.MethodDownAction]
         */
        get() = "MethodDown"

    override fun performAction() {
        val documentText = editor.document.text
        InTextDirectivesUtils.findLineWithPrefixRemoved(documentText, "// NAVIGATE_TO_ELEMENT: ")?.let { navigateToElement ->
            val className = navigateToElement.substringBefore("#")
            val navigationElement = myFixture.findClass(className).navigationElement
            myFixture.openFileInEditor(navigationElement.containingFile.virtualFile)
            myFixture.editor.caretModel.moveToOffset(navigationElement.textOffset)
            navigateToElement.substringAfter("#", "").takeIf { it.isNotEmpty() }?.let { methodName ->
                val declaration = (navigationElement as? KtClassOrObject)?.declarations?.firstOrNull { it.name == methodName }
                if (declaration != null) {
                    myFixture.editor.caretModel.moveToOffset(declaration.textOffset)
                }
            }
        }
        super.performAction()
    }
}