/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.k2.refactoring.rename

import com.intellij.model.psi.impl.targetSymbols
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.api.RenameTarget
import com.intellij.refactoring.rename.symbol.SymbolRenameTargetFactory
import org.jetbrains.kotlin.idea.k2.refactoring.rename.KotlinRenameTargetProvider
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance

/**
 * Not used at the moment, since [org.jetbrains.kotlin.idea.refactoring.rename.KotlinRenameUsageSearcher]
 * is disabled.
 */
abstract class AbstractFirSimpleRenameTest : KotlinLightCodeInsightFixtureTestCase() {
    private val NEW_NAME_DIRECTIVE = "NEW_NAME"

    override fun isFirPlugin(): Boolean = true

    protected fun doTest(testPath: String) {
        val beforeFile = mainFile()
        val newName = InTextDirectivesUtils.stringWithDirective(beforeFile.readText(), NEW_NAME_DIRECTIVE)

        myFixture.configureByFile(beforeFile)

        val renameTarget = myFixture.elementAtCaret.getKotlinRenameTarget() ?: error("No target under caret!")
        myFixture.renameTarget(renameTarget, newName)

        KotlinTestUtils.assertEqualsToSibling(beforeFile.toPath(), "after", myFixture.file.text)
    }

    private fun PsiElement.getKotlinRenameTarget(): RenameTarget? {
        val psiSymbol = targetSymbols(this.containingFile, this.textOffset).firstOrNull() ?: return null

        val kotlinProvider = SymbolRenameTargetFactory.EP_NAME.extensionList.firstIsInstance<KotlinRenameTargetProvider>()

        return kotlinProvider.renameTarget(project, psiSymbol)
    }
}
