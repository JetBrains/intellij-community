// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.extensions

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.PreviewCommandRunnability
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Guards the default registration: if the client's mirror ever leaked into a non-split product, every preview would
 * render twice and its run icons would be missing from the first pass. */
@RunWith(JUnit4::class)
class PreviewCommandRunnabilityTest : BasePlatformTestCase() {

  @Test
  fun `runnability is decided locally, so no rendering is ever repeated`() {
    val file = myFixture.addFileToProject("foo/test.md", "`pwd`").virtualFile
    val runnability = PreviewCommandRunnability.getInstance()

    runnability.isRunnable(project, file, "pwd", allowRunConfigurations = false)
    assertFalse("Local runnability must never leave a question pending", runBlocking { runnability.resolvePending() })
  }

  @Test
  fun `a command no provider claims is not runnable`() {
    val file = myFixture.addFileToProject("bar/test.md", "`nonsense`").virtualFile
    val runnability = PreviewCommandRunnability.getInstance()

    assertFalse(runnability.isRunnable(project, file, "definitely-not-a-runnable-command", allowRunConfigurations = false))
  }
}
