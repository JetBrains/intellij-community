package org.intellij.plugins.markdown.editor.lists

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ListItemTabHandlerAdditionalTest: LightPlatformCodeInsightTestCase() {
  // Tests use the list marker width by default. Tests with fixed indentation enable that setting explicitly.
  override fun setUp() {
    super.setUp()
    val settings = CodeStyle.getSettings(project)
    settings.getCommonSettings(MarkdownLanguage.INSTANCE).indentOptions!!.INDENT_SIZE = 4
    settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).USE_FIXED_INDENTS_FOR_SUBLISTS = false
  }

  @Test
  fun `test tab handles a list item becoming code with renumbering enabled`() {
    enableFixedSublistIndent()
    val settings = MarkdownCodeInsightSettings.getInstance().state
    val renumberListsOnType = settings.renumberListsOnType
    settings.renumberListsOnType = true
    try {
      configureFromFileText("some.md", "- first<caret>")
      executeAction(IdeActions.ACTION_EDITOR_TAB)
      checkResultByText("    - first<caret>")
    }
    finally {
      settings.renumberListsOnType = renumberListsOnType
    }
  }

  @Test
  fun `test tab indents a new empty item according to fixed settings`() {
    enableFixedSublistIndent()
    val content = """
        - hello
        - <caret>
    """.trimIndent()
    val expected = """
        - hello
            - <caret>
    """.trimIndent()
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(expected)
  }

  @Test
  fun `test tab uses configured fixed sublist indent`() {
    enableFixedSublistIndent()
    val content = """
        - first
        - second<caret>
    """.trimIndent()
    val afterFirstTab = """
        - first
            - second<caret>
    """.trimIndent()
    val afterSecondTab = """
        - first
                - second<caret>
    """.trimIndent()
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(afterFirstTab)
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(afterSecondTab)
    executeAction(IdeActions.ACTION_EDITOR_UNINDENT_SELECTION)
    checkResultByText(afterFirstTab)
    executeAction(IdeActions.ACTION_EDITOR_UNINDENT_SELECTION)
    checkResultByText(content)
  }

  @Test
  fun `test tab uses marker width when fixed sublist indent is disabled`() {
    val content = """
        - first
        - second<caret>
    """.trimIndent()
    val expected = """
        - first
          - second<caret>
    """.trimIndent()
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(expected)
  }

  @Test
  fun `test fixed sublist indent does not go before parent content`() {
    enableFixedSublistIndent()
    val content = """
        1000. first
        1000. second<caret>
    """.trimIndent()
    val expected = """
        1000. first
              1000. second<caret>
    """.trimIndent()
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(expected)
  }

  @Test
  fun `test repeated tab and shift tab only move the new empty unordered item`() {
    enableFixedSublistIndent()
    val content = """
        - hello<caret>
    """.trimIndent()
    val afterEnter = """
        - hello
        - <caret>
    """.trimIndent()
    val afterFirstTab = """
        - hello
            - <caret>
    """.trimIndent()
    val afterSecondTab = """
        - hello
                - <caret>
    """.trimIndent()
    val afterUnindent = """
        - hello
            - <caret>
    """.trimIndent()
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_EDITOR_ENTER)
    checkResultByText(afterEnter)

    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(afterFirstTab)

    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(afterSecondTab)

    executeAction(IdeActions.ACTION_EDITOR_UNINDENT_SELECTION)
    checkResultByText(afterUnindent)
  }

  private fun enableFixedSublistIndent() {
    val settings = CodeStyle.getSettings(project)
    val indentOptions = settings.getCommonSettings(MarkdownLanguage.INSTANCE).indentOptions!!
    indentOptions.TAB_SIZE = 4
    indentOptions.CONTINUATION_INDENT_SIZE = 8
    settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).USE_FIXED_INDENTS_FOR_SUBLISTS = true
  }

  /**
   * Checks that list item is indented with 2 tabs (4 spaces) after pressing tab 2 times.
   */
  @Test
  fun `test multiple tabs are inserted correctly`() {
    // language=Markdown
    val content = """
    * Some list item
    <caret>* Some other item
    * Some
    """.trimIndent()
    // language=Markdown
    val expected = """
    * Some list item
        <caret>* Some other item
    * Some
    """.trimIndent()
    configureFromFileText("some.md", content)
    repeat(2) {
      executeAction(IdeActions.ACTION_EDITOR_TAB)
    }
    checkResultByText(expected)
  }

  @Test
  fun `test tab at the line start indents a nested item by a single level`() {
    // language=Markdown
    val content = """
    list:
    - 1
    <caret>  - 1.1
      - 1.2
    - 2
    """.trimIndent()
    // language=Markdown
    val expected = """
    list:
    - 1
    <caret>    - 1.1
      - 1.2
    - 2
    """.trimIndent()
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(expected)
  }

  @Test
  fun `test tab before the marker indents a nested item by a single level`() {
    // language=Markdown
    val content = """
    list:
    - 1
      <caret>- 1.1
      - 1.2
    - 2
    """.trimIndent()
    // language=Markdown
    val expected = """
    list:
    - 1
        <caret>- 1.1
      - 1.2
    - 2
    """.trimIndent()
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(expected)
  }

  @Test
  fun `test tab at the line start indents an ordered item by a single level`() {
    // language=Markdown
    val content = """
    1. one
    <caret>   1. one.one
       2. one.two
    2. two
    """.trimIndent()
    // language=Markdown
    val expected = """
    1. one
    <caret>      1. one.one
       2. one.two
    2. two
    """.trimIndent()
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(expected)
  }

  @Test
  fun `test tab before the marker indents an ordered item by a single level`() {
    // language=Markdown
    val content = """
    1. one
       <caret>1. one.one
       2. one.two
    2. two
    """.trimIndent()
    // language=Markdown
    val expected = """
    1. one
          <caret>1. one.one
       2. one.two
    2. two
    """.trimIndent()
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(expected)
  }

  @Test
  fun `test tab does not indent the children of the item under the caret`() {
    // language=Markdown
    val content = """
    <caret>- aaa
      - bbb
      - ccc
      - ddd
    - eee
    """.trimIndent()
    // language=Markdown
    val expected = """
    <caret>  - aaa
      - bbb
      - ccc
      - ddd
    - eee
    """.trimIndent()
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(expected)
  }

  @Test
  fun `test tab at the start of a top level item indents it by a single level`() {
    // language=Markdown
    val content = """
    list:
    <caret>- 1
      - 1.1
      - 1.2
      - 1.3
      - 1.4
    - 2
    - 3
    """.trimIndent()
    // language=Markdown
    val expected = """
    list:
      <caret>- 1
      - 1.1
      - 1.2
      - 1.3
      - 1.4
    - 2
    - 3
    """.trimIndent()
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(expected)
  }

  @Test
  fun `test tab indents a list item inside a blockquote by a single level`() {
    // language=Markdown
    val content = """
    > list:
    > - 1
    > <caret>  - 1.1
    >   - 1.2
    > - 2
    """.trimIndent()
    // language=Markdown
    val expected = """
    > list:
    > - 1
    > <caret>    - 1.1
    >   - 1.2
    > - 2
    """.trimIndent()
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(expected)
  }

  @Test
  fun `test tab inside fenced code block within list item does not shift the list item`() {
    // language=Markdown
    val content = """
    * First item
    * Second item
      ```
      <caret>code
      ```
    """.trimIndent()
    // language=Markdown
    val expected = """
    * First item
    * Second item
      ```
        <caret>code
      ```
    """.trimIndent()
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(expected)
  }

  @Test
  fun `test tab inside indented code block within list item does not shift the list item`() {
    // language=Markdown
    val content = """
    * First item
    * Second item

          <caret>code
    """.trimIndent()
    // language=Markdown
    val expected = """
    * First item
    * Second item

            <caret>code
    """.trimIndent()
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(expected)
  }

  @Test
  fun `test tab inside table cell within list item does not shift the list item`() {
    // language=Markdown
    val content = """
    * First item
    * Second item
      | h1 | h2 |
      |----|----|
      | <caret>a  | b  |
    """.trimIndent()
    // language=Markdown
    val expected = """
    * First item
    * Second item
      | h1 | h2 |
      |----|----|
      |     <caret>a  | b  |
    """.trimIndent()
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(expected)
  }

  @Test
  fun `test tab inside blockquote within list item does not shift the list item`() {
    // language=Markdown
    val content = """
    * First item
    * Second item
      > <caret>quoted
    """.trimIndent()
    // language=Markdown
    val expected = """
    * First item
    * Second item
      >     <caret>quoted
    """.trimIndent()
    configureFromFileText("some.md", content)
    executeAction(IdeActions.ACTION_EDITOR_TAB)
    checkResultByText(expected)
  }

  @Test
  fun `test selected items keep their alignment on every tab and unindent press`() {
    // language=Markdown
    configureFromFileText("some.md", """
    list:
    - 1
      - 1.1
    <selection>  - 1.2
      - 1.3
      - 1.4</selection>
    - 2
    - 3
    """.trimIndent())

    indentSelection()
    // language=Markdown
    checkResultByText("""
    list:
    - 1
      - 1.1
        - 1.2
        - 1.3
        - 1.4
    - 2
    - 3
    """.trimIndent())

    // 1.2 cannot be nested deeper, but the group still moves by one level
    indentSelection()
    // language=Markdown
    checkResultByText("""
    list:
    - 1
      - 1.1
          - 1.2
          - 1.3
          - 1.4
    - 2
    - 3
    """.trimIndent())

    indentSelection()
    // language=Markdown
    checkResultByText("""
    list:
    - 1
      - 1.1
            - 1.2
            - 1.3
            - 1.4
    - 2
    - 3
    """.trimIndent())

    // Shift+TAB must not unindent the unselected 1.1
    unindent()
    // language=Markdown
    checkResultByText("""
    list:
    - 1
      - 1.1
        - 1.2
        - 1.3
        - 1.4
    - 2
    - 3
    """.trimIndent())
  }

  @Test
  fun `test a selected item is indented together with its children`() {
    // a caret instead of the selection moves only its line, see `test tab does not indent the children of the item under the caret`
    // language=Markdown
    configureFromFileText("some.md", """
    <selection>- aaa</selection>
      - bbb
      - ccc
    - ddd
    """.trimIndent())

    indentSelection()
    // language=Markdown
    checkResultByText("""
      - aaa
        - bbb
        - ccc
    - ddd
    """.trimIndent())
  }

  private fun indentSelection() {
    executeAction(IdeActions.ACTION_EDITOR_INDENT_SELECTION)
  }
}
