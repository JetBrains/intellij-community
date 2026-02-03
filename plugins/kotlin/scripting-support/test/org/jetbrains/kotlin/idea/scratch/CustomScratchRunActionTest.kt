// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.scratch

import com.intellij.openapi.roots.ModuleRootModificationUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.run.createLibraryWithLongPaths
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class CustomScratchRunActionTest : AbstractScratchRunActionTest() {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K1

    // See KTIJ-31394 for the reasoning why this test is ignored
    //fun testLongCommandLineWithRepl() {
    //    assertEquals(
    //        """|// REPL_MODE: true
    //           |// INTERACTIVE_MODE: false
    //           |1    // RESULT: res0: kotlin.Int = 1""".trimMargin(),
    //        getOutput(true)
    //    )
    //}

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

    override fun setUp() {
        super.setUp()

        ModuleRootModificationUtil.addDependency(module, createLibraryWithLongPaths(project, testRootDisposable))
    }

}
