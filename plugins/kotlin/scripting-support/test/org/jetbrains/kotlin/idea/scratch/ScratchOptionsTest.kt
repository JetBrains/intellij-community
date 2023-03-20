// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.scratch

import org.jetbrains.kotlin.idea.scratch.ui.ModulesComboBoxAction
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class ScratchOptionsTest : AbstractScratchRunActionTest() {

    fun testModuleSelectionPanelIsVisibleForScratchFile() {
        val scratchFile = configureScratchByText("scratch_1.kts", doTestScratchText())

        Assert.assertTrue("Module selector should be visible for scratches", isModuleSelectorVisible(scratchFile))
    }

    fun testModuleSelectionPanelIsHiddenForWorksheetFile() {
        val scratchFile = configureWorksheetByText("worksheet.ws.kts", doTestScratchText())

        Assert.assertFalse("Module selector should be hidden for worksheets", isModuleSelectorVisible(scratchFile))
    }

    fun testCurrentModuleIsAutomaticallySelectedForWorksheetFile() {
        val scratchFile = configureWorksheetByText("worksheet.ws.kts", doTestScratchText())

        Assert.assertEquals(
            "Selected module should be equal to current project module for worksheets",
            myFixture.module,
            scratchFile.module
        )
    }

    private fun isModuleSelectorVisible(scratchTopPanel: ScratchFile): Boolean {
        return ModulesComboBoxAction(scratchTopPanel).isModuleSelectorVisible()
    }

}