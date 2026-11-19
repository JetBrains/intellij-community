// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor.injection

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.readAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeFence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@TestApplication
class MarkdownCodeFenceInjectionDumbModeTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @Test
  fun `test a code fence is injected while the project is dumb`() {
    DumbModeTestUtils.runInDumbModeSynchronously(myFixture.project) {
      myFixture.configureByText("test.md", "```xml\n<tag/>\n```\n")
      timeoutRunBlocking {
        readAction {
          val codeFence = requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, MarkdownCodeFence::class.java))

          val injectedFiles = InjectedLanguageManager.getInstance(myFixture.project).getInjectedPsiFiles(codeFence)

          assertNotNull(injectedFiles, "the fence must get an injected file in dumb mode")
          assertEquals(1, injectedFiles!!.size)
        }
      }
    }
  }
}
