package org.intellij.plugins.markdown.model

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.CompletionAutoPopupTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.application
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/model/headers/completion")
class HeaderAnchorCompletionPopupTypedHandlerTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private val lookup: LookupImpl? get() = CompletionAutoPopupTestUtil.getLookup(myFixture)

  private fun type(s: String) = CompletionAutoPopupTestUtil.type(myFixture, s)

  private fun runTest(block: () -> Unit) = CompletionAutoPopupTestUtil.runWithAutoPopupEnabled<Throwable>(myFixture, block)

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true).trimStart().replace(' ', '_')
    application.invokeAndWait {
      myFixture.copyDirectoryToProject("", "")
    }
  }

  @Test
  fun `test reference to own header`(): Unit = runTest {
    doTest(
      "header-in-main",
      "other-header-in-main"
    )
  }

  @Test
  fun `test reference to header in other file`(): Unit = runTest {
    doTest(
      "header-near-main",
      "other-header-near-main"
    )
  }

  @Test
  fun `test reference to header in subdirectory`(): Unit = runTest {
    doTest(
      "header-in-subdirectory",
      "other-header-in-subdirectory"
    )
  }

  private fun doTest(vararg expectedLookupString: String) {
    myFixture.configureByFile("$testName.md")
    type("#")
    assertNotNull(lookup)
    val elements = myFixture.lookupElementStrings ?: emptyList()
    assertThat(elements).containsExactlyInAnyOrder(*expectedLookupString)
  }
}
