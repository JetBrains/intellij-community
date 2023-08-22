// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownTestingUtil

class MarkdownConfigureImageIntentionTest: BasePlatformTestCase() {
  fun `test markdown image1`() = doTest()

  fun `test markdown image2`() = doTest()

  fun `test markdown image3`() = doTest()

  fun `test markdown image4`() = doTest()

  fun `test markdown image inside text html1`() {
    doContentTest("Prefix paragraph text <div><caret>!<caret>[descri<caret>ption]<caret>(ima<caret>ge.png)</div>")
  }

  fun `test markdown image inside text html2`() {
    doContentTest("Prefix paragraph text <div><caret>!<caret>[descri<caret>ption]<caret>(ima<caret>ge.png)</div> suffix text")
  }

  fun `test markdown image inside text html3`() {
    doContentTest("Prefix paragraph text <div><div><caret>!<caret>[descri<caret>ption]<caret>(ima<caret>ge.png)</div></div>")
  }

  fun `test markdown image inside text html4`() = doTest()

  fun `test markdown image inside reference`() = doTest()

  fun `test reference inside markdown image`() = doTest()

  fun `test no markdown image inside html block1`() = doTest(shouldHaveIntentions = false)

  fun `test no markdown image inside html block2`() = doTest(shouldHaveIntentions = false)

  fun `test no markdown image inside html block3`() = doTest(shouldHaveIntentions = false)

  fun `test html block1`() = doTest()

  fun `test html block2`() = doTest()

  fun `test html block3`() = doContentTest("""<caret><<caret>img<caret> src="s<caret>ome.png">""")

  fun `test html block4`() = doContentTest("""<caret><<caret>img<caret> src="s<caret>ome.png"/>""")

  fun `test html block5`() = doContentTest("""<caret><<caret>img<caret> src="s<caret>ome.png"></img>""")

  fun `test html block6`() = doContentTest("""<div><caret><<caret>img<caret> src="s<caret>ome.png"></div>""")

  fun `test html block7`() = doContentTest("""<div><div><caret><<caret>img<caret> src="s<caret>ome.png"></div></div>""")

  fun `test no around html image1`() = doTest(shouldHaveIntentions = false)

  fun `test no around html image2`() = doTest(shouldHaveIntentions = false)

  fun `test no around html image3`() = doTest(shouldHaveIntentions = false)

  fun `test text html1`() = doContentTest("""Some paragraph <div><caret><<caret>img src="<caret>image.png"></div>""")

  fun `test text html2`() = doContentTest("""Some paragraph <caret><img<caret> src="i<caret>mage.png"> suffix""")

  fun `test text html3`() = doContentTest("""Some paragraph <div><caret><i<caret>mg src="i<caret>mage.png">""")

  fun `test text html4`() = doContentTest("""Some paragraph <div><caret><<caret>img src="<caret>image.png"></div>""")

  private fun doContentTest(fileContent: String, shouldHaveIntentions: Boolean = true) {
    myFixture.configureByText(getTestFileName(), fileContent)
    doActualTest(shouldHaveIntentions)
  }

  private fun doTest(fileName: String = getTestFileName(), shouldHaveIntentions: Boolean = true) {
    myFixture.configureByFile(fileName)
    doActualTest(shouldHaveIntentions)
  }

  private fun doActualTest(shouldHaveIntentions: Boolean) {
    val intentions = myFixture.filterAvailableIntentions(MarkdownBundle.message("markdown.configure.image.text"))
    when {
      shouldHaveIntentions -> assertSize(1, intentions)
      else -> assertEmpty(intentions)
    }
  }

  private fun getTestFileName(): String {
    return "${getTestName(false)}.md"
  }

  override fun getTestName(lowercaseFirstLetter: Boolean): String {
    val name = super.getTestName(lowercaseFirstLetter)
    return name.trimStart().replace(' ', '_')
  }

  override fun getTestDataPath(): String {
    return "${MarkdownTestingUtil.TEST_DATA_PATH}/editor/images/intention/"
  }
}
