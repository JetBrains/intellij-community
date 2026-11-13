// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.extensions

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.runBlocking
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.PreviewCommandRunnability
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/** Guards the default registration: if the client's mirror ever leaked into a non-split product, every preview would
 * render twice and its run icons would be missing from the first pass. */
@TestApplication
class PreviewCommandRunnabilityTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `runnability is decided locally, so no rendering is ever repeated`() {
    val file = myFixture.addFileToProject("foo/test.md", "`pwd`").virtualFile
    val runnability = PreviewCommandRunnability.getInstance()

    runnability.isRunnable(myFixture.project, file, "pwd", allowRunConfigurations = false)
    assertFalse(runBlocking { runnability.resolvePending() }, "Local runnability must never leave a question pending")
  }

  @Test
  fun `a command no provider claims is not runnable`() {
    val file = myFixture.addFileToProject("bar/test.md", "`nonsense`").virtualFile
    val runnability = PreviewCommandRunnability.getInstance()

    assertFalse(runnability.isRunnable(myFixture.project, file, "definitely-not-a-runnable-command", allowRunConfigurations = false))
  }
}
