// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.injection

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence

class MarkdownCodeFenceInjectionDumbModeTest : BasePlatformTestCase() {
  fun `test a code fence is injected while the project is dumb`() {
    DumbModeTestUtils.runInDumbModeSynchronously(project) {
      myFixture.configureByText("test.md", "```xml\n<tag/>\n```\n")
      val codeFence = requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java))

      val injectedFiles = InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(codeFence)

      assertNotNull("the fence must get an injected file in dumb mode", injectedFiles)
      assertEquals(1, injectedFiles!!.size)
    }
  }
}
