// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.k2.scratch

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.testFramework.FileEditorManagerTestCase
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.TestDataProvider
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.jvm.shared.scratch.getScratchEditorForSelectedFile
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

class K2ScratchActionStateTest : FileEditorManagerTestCase() {

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

    

    fun testRunActionDisabledInInteractiveMode() {
        val editor = openScratchEditor()
        val scratchFile = editor.kotlinScratchFile
        scratchFile.setModule(myFixture.module) // baseline: with a module bound the action would be enabled
        scratchFile.saveOptions { copy(isMakeBeforeRun = false, isInteractiveMode = true) }

        val presentation = updateAndGetPresentation(RunScratchActionK2())

        assertFalse(
            "Run action must be disabled while interactive mode is on",
            presentation.isEnabled,
        )
        assertTrue(
            "Run action stays visible while interactive mode is on (only disabled)",
            presentation.isVisible,
        )
    }

    fun testMakeBeforeRunDisabledInInteractiveMode() {
        val editor = openScratchEditor()
        val scratchFile = editor.kotlinScratchFile
        scratchFile.setModule(myFixture.module) // make-before-run is only visible while a module is bound
        scratchFile.saveOptions { copy(isMakeBeforeRun = true, isInteractiveMode = true) }

        val makeBeforeRun = editor.getViewActionsForTesting().first()
        val presentation = updateAndGetPresentation(makeBeforeRun)

        assertFalse(
            "MakeBeforeRun toggle must be disabled while interactive mode is on",
            presentation.isEnabled,
        )
        assertTrue(
            "MakeBeforeRun toggle stays visible while a module is bound (only disabled by interactive mode)",
            presentation.isVisible,
        )
    }

    private fun openScratchEditor(): K2ScratchFileEditorWithPreview {
        val name = "K2ScratchActionStateTest_${getTestName(false)}.kts"
        val vFile = ScratchRootType.getInstance().createScratchFile(
            project, name, KotlinLanguage.INSTANCE, "", ScratchFileService.Option.create_if_missing,
        ) ?: error("Failed to create scratch file $name")

        myFixture.openFileInEditor(vFile)
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val editor = manager?.let { getScratchEditorForSelectedFile(it, vFile) }
            ?: error("Scratch editor not opened for $name")
        return editor as? K2ScratchFileEditorWithPreview
            ?: error("Expected K2ScratchFileEditorWithPreview, was ${editor::class.simpleName}")
    }

    private fun updateAndGetPresentation(action: AnAction): Presentation {
        val event: AnActionEvent = TestActionEvent.createTestEvent(action, TestDataProvider(project)::getData)
        ActionUtil.updateAction(action, event)
        return event.presentation
    }
}
