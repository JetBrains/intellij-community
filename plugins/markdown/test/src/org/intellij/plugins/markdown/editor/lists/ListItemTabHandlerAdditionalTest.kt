package org.intellij.plugins.markdown.editor.lists

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ListItemTabHandlerAdditionalTest: LightPlatformCodeInsightTestCase() {
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
