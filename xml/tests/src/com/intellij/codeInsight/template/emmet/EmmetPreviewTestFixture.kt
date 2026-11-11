// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet

import com.intellij.application.options.emmet.EmmetOptions
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.project.Project
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

/**
 * The helpers of an Emmet preview test: they check the preview hint of the editor after a typed abbreviation.
 * Create it with [emmetPreviewFixture].
 *
 * The setup turns on the Emmet preview, and the teardown turns it off.
 * Call the checks on the EDT, in the same block as the typing that they check.
 */
@TestOnly
class EmmetPreviewTestFixture private constructor(
  /**
   * The code insight fixture of the test, on a new module for each test.
   */
  val fixture: CodeInsightTestFixture,
) {
  val project: Project get() = fixture.project

  /**
   * Waits for the preview, and checks that the preview hint shows [previewContent].
   */
  @RequiresEdt
  fun assertPreview(previewContent: String) {
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val previewHint = getPreview()
    assertNotNull(previewHint)
    assertEquals(previewContent, previewHint!!.content)
  }

  /**
   * Waits for the preview, and checks that the editor shows no preview hint.
   */
  @RequiresEdt
  fun assertNoPreview() {
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertNull(getPreview())
  }

  /**
   * Returns the preview hint of the editor of [fixture], or `null`.
   */
  @RequiresEdt
  fun getPreview(): EmmetPreviewHint? = EmmetPreviewHint.getExistingHint(fixture.editor)

  companion object {
    /**
     * Creates an [EmmetPreviewTestFixture] on a new module of [projectFixture].
     * Declare the result as an instance field, so that each test gets its own fixture.
     */
    @JvmStatic
    @TestOnly
    fun emmetPreviewFixture(projectFixture: TestFixture<Project>): TestFixture<EmmetPreviewTestFixture> =
      testFixture("emmetPreviewFixture") {
        val fixture = codeInsightModuleFixture(projectFixture).init()
        EmmetOptions.getInstance().isPreviewEnabled = true
        initialized(EmmetPreviewTestFixture(fixture)) {
          EmmetOptions.getInstance().isPreviewEnabled = false
        }
      }
  }
}
