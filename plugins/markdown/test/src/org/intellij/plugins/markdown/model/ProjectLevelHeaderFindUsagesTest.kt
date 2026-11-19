package org.intellij.plugins.markdown.model

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readActionBlocking
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.util.startOffset
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension
import org.intellij.plugins.markdown.lang.psi.MarkdownRecursiveElementVisitor
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/model/headers/usages/project")
@ExtendWith(SoftAssertionsExtension::class)
class ProjectLevelHeaderFindUsagesTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private lateinit var testName: String

  @InjectSoftAssertions
  lateinit var softAssertions: SoftAssertions

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, true).trimStart().replace(' ', '_')
    myFixture.copyDirectoryToProject("", "")
  }

  @Test
  fun `test main`(): Unit = doTest()

  @Test
  fun `test other`(): Unit = doTest()

  @Test
  fun `test inner`(): Unit = doTest("subdirectory")

  private fun doTest(subdirectory: String? = null) {
    val contentFile = when (subdirectory) {
      null -> "$testName.md"
      else -> "$subdirectory/$testName.md"
    }
    myFixture.configureByFile(contentFile)
    val headers = collectHeaders()
    assertThat(headers).isNotEmpty
    timeoutRunBlocking {
      for (header in headers) {
        val (offset, anchorText) = readActionBlocking { header.startOffset + 1 to header.anchorText!! }
        val usages = withContext(Dispatchers.EDT) {
          myFixture.editor.caretModel.primaryCaret.moveToOffset(offset)
          myFixture.testFindUsagesUsingAction(contentFile)
        }.map { it.toString() }.sorted()
        val text = usages.joinToString(separator = "\n", postfix = "\n")
        val expectedUsagesFileName = "$testName-$anchorText.txt".lowercase()
        val expectedUsagesFilePath = Path("${myFixture.testDataPath}/$expectedUsagesFileName")
        softAssertions.assertThat(expectedUsagesFilePath.exists())
          .withFailMessage { "Expected file $expectedUsagesFileName does not exist. Expected full path: $expectedUsagesFilePath" }
          .isTrue
        if (expectedUsagesFilePath.exists()) {
          val expectedText = expectedUsagesFilePath.readText()
          softAssertions.assertThat(text).isEqualTo(expectedText)
        }
      }
    }
  }

  private fun collectHeaders(): Collection<MarkdownHeader> {
    val elements = mutableListOf<MarkdownHeader>()
    val visitor = object: MarkdownRecursiveElementVisitor() {
      override fun visitHeader(header: MarkdownHeader) {
        super.visitHeader(header)
        elements.add(header)
      }
    }
    timeoutRunBlocking { readActionBlocking { myFixture.file.accept(visitor) } }
    return elements
  }
}
