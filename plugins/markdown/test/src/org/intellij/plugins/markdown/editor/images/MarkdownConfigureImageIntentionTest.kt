// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.intellij.plugins.markdown.images.MarkdownImagesBundle
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/plugins/markdown/test/data/editor/images/intention")
class MarkdownConfigureImageIntentionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private lateinit var testName: String

  @BeforeEach
  fun setUp(testInfo: TestInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.testMethod.get().name, false).trimStart().replace(' ', '_')
  }

  @Test
  fun `test markdown image1`(): Unit = doTest()

  @Test
  fun `test markdown image2`(): Unit = doTest()

  @Test
  fun `test markdown image3`(): Unit = doTest()

  @Test
  fun `test markdown image4`(): Unit = doTest()

  @Test
  fun `test markdown image inside text html1`() {
    doContentTest("Prefix paragraph text <div><caret>!<caret>[descri<caret>ption]<caret>(ima<caret>ge.png)</div>")
  }

  @Test
  fun `test markdown image inside text html2`() {
    doContentTest("Prefix paragraph text <div><caret>!<caret>[descri<caret>ption]<caret>(ima<caret>ge.png)</div> suffix text")
  }

  @Test
  fun `test markdown image inside text html3`() {
    doContentTest("Prefix paragraph text <div><div><caret>!<caret>[descri<caret>ption]<caret>(ima<caret>ge.png)</div></div>")
  }

  @Test
  fun `test markdown image inside text html4`(): Unit = doTest()

  @Test
  fun `test markdown image inside reference`(): Unit = doTest()

  @Test
  fun `test reference inside markdown image`(): Unit = doTest()

  @Test
  fun `test no markdown image inside html block1`(): Unit = doTest(shouldHaveIntentions = false)

  @Test
  fun `test no markdown image inside html block2`(): Unit = doTest(shouldHaveIntentions = false)

  @Test
  fun `test no markdown image inside html block3`(): Unit = doTest(shouldHaveIntentions = false)

  @Test
  fun `test html block1`(): Unit = doTest()

  @Test
  fun `test html block2`(): Unit = doTest()

  @Test
  fun `test html block3`(): Unit = doContentTest("""<caret><<caret>img<caret> src="s<caret>ome.png">""")

  @Test
  fun `test html block4`(): Unit = doContentTest("""<caret><<caret>img<caret> src="s<caret>ome.png"/>""")

  @Test
  fun `test html block5`(): Unit = doContentTest("""<caret><<caret>img<caret> src="s<caret>ome.png"></img>""")

  @Test
  fun `test html block6`(): Unit = doContentTest("""<div><caret><<caret>img<caret> src="s<caret>ome.png"></div>""")

  @Test
  fun `test html block7`(): Unit = doContentTest("""<div><div><caret><<caret>img<caret> src="s<caret>ome.png"></div></div>""")

  @Test
  fun `test no around html image1`(): Unit = doTest(shouldHaveIntentions = false)

  @Test
  fun `test no around html image2`(): Unit = doTest(shouldHaveIntentions = false)

  @Test
  fun `test no around html image3`(): Unit = doTest(shouldHaveIntentions = false)

  @Test
  fun `test text html1`(): Unit = doContentTest("""Some paragraph <div><caret><<caret>img src="<caret>image.png"></div>""")

  @Test
  fun `test text html2`(): Unit = doContentTest("""Some paragraph <caret><img<caret> src="i<caret>mage.png"> suffix""")

  @Test
  fun `test text html3`(): Unit = doContentTest("""Some paragraph <div><caret><i<caret>mg src="i<caret>mage.png">""")

  @Test
  fun `test text html4`(): Unit = doContentTest("""Some paragraph <div><caret><<caret>img src="<caret>image.png"></div>""")

  private fun doContentTest(fileContent: String, shouldHaveIntentions: Boolean = true) {
    myFixture.configureByText(getTestFileName(), fileContent)
    doActualTest(shouldHaveIntentions)
  }

  private fun doTest(fileName: String = getTestFileName(), shouldHaveIntentions: Boolean = true) {
    myFixture.configureByFile(fileName)
    doActualTest(shouldHaveIntentions)
  }

  private fun doActualTest(shouldHaveIntentions: Boolean) {
    val intentions = myFixture.filterAvailableIntentions(MarkdownImagesBundle.message("markdown.configure.image.text"))
    when {
      shouldHaveIntentions -> assertThat(intentions).hasSize(1)
      else -> assertThat(intentions).isEmpty()
    }
  }

  private fun getTestFileName(): String {
    return "$testName.md"
  }
}
