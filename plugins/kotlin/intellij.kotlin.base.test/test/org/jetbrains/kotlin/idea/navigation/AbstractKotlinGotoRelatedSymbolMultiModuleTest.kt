// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.navigation

import com.intellij.codeInsight.navigation.GotoTargetHandler
import com.intellij.codeInsight.navigation.collectRelatedItems
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import java.io.File

abstract class AbstractKotlinGotoRelatedSymbolMultiModuleTest : AbstractKotlinNavigationMultiModuleTest() {
    override fun getTestDataDirectory(): File = IDEA_TEST_DATA_DIR.resolve("navigation/relatedSymbols/multiModule")

    override fun doNavigate(editor: Editor, file: PsiFile): GotoTargetHandler.GotoData {
        val source = file.findElementAt(editor.caretModel.offset)!!
        val relatedItems =
            runBlockingMaybeCancellable {
                withBackgroundProgress(project, "Test Android Gradle Sync") {
                    readAction {
                        collectRelatedItems(contextElement = source, dataContext = SimpleDataContext.EMPTY_CONTEXT)
                    }
                }
            }
        return GotoTargetHandler.GotoData(source, relatedItems.map { it.element }.toTypedArray(), emptyList())
    }
}