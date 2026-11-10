package org.jetbrains.plugins.textmate.editor

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.textmate.TextMateService
import org.jetbrains.plugins.textmate.TextMateServiceImpl

/**
 * The tests use the built-in bundles. Their indentation patterns have the JavaScript syntax of VS Code.
 */
class TextMateLineIndentProviderTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()
    (TextMateService.getInstance() as TextMateServiceImpl).reloadEnabledBundles()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  fun testIncreasePatternWithBareBrace() {
    doTest("main.go", "func main() {<caret>", "func main() {\n    <caret>")
  }

  fun testNoIncreaseWithoutPatternMatch() {
    doTest("main.go", "func main() {\n    x := 1<caret>", "func main() {\n    x := 1\n    <caret>")
  }

  fun testDecreasePatternOnPreviousLine() {
    doTest("main.go", "func main() {\n    }<caret>", "func main() {\n    }\n<caret>")
  }

  fun testOnEnterRuleSuppressesIncrease() {
    // the increase pattern matches the brace, and the `//.*` on-enter rule with the action `none` wins
    doTest("main.go", "func main() {\n    // bar {<caret>", "func main() {\n    // bar {\n    <caret>")
  }

  fun testIncreasePatternMatchesPartOfLine() {
    doTest("list.tex", "text \\begin{itemize}<caret>", "text \\begin{itemize}\n    <caret>")
  }

  private fun doTest(fileName: String, source: String, expected: String) {
    myFixture.configureByText(fileName, source)
    myFixture.type('\n')
    myFixture.checkResult(expected)
  }
}
