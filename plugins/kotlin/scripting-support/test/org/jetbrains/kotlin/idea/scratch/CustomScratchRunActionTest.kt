// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.scratch

import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.run.createLibraryWithLongPaths
import org.jetbrains.kotlin.idea.test.runAll
import com.intellij.openapi.application.runWriteAction
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class CustomScratchRunActionTest : AbstractScratchRunActionTest() {

    fun testLongCommandLineWithRepl() {
        assertEquals(
            """|// REPL_MODE: true
               |// INTERACTIVE_MODE: false
               |1    // RESULT: res0: kotlin.Int = 1""".trimMargin(),
            getOutput(true)
        )
    }

    fun testLongCommandLine() {
        assertEquals(
            """|// REPL_MODE: false
               |// INTERACTIVE_MODE: false
               |1    // RESULT: 1""".trimMargin(),
            getOutput(false)
        )
    }

    private fun getOutput(isRepl: Boolean): String {
        val fileText = doTestScratchText().inlinePropertiesValues(isRepl)
        configureScratchByText("scratch_1.kts", fileText)

        launchScratch()
        waitUntilScratchFinishes(isRepl)

        return getFileTextWithInlays()
    }

    private val library: Library by lazy {
        createLibraryWithLongPaths(project)
    }

    override fun setUp() {
        super.setUp()

        ModuleRootModificationUtil.addDependency(myFixture.module, library)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { removeLibraryWithLongPaths() },
            ThrowableRunnable { super.tearDown() }
        )
    }

    private fun removeLibraryWithLongPaths() {
        runWriteAction {
            val modifiableModel = LibraryTablesRegistrar.getInstance().getLibraryTable(project).modifiableModel
            try {
                modifiableModel.removeLibrary(library)
            } finally {
                modifiableModel.commit()
            }
        }
    }
}