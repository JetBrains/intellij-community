package org.intellij.plugins.markdown.editor.lists

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.plugins.markdown.editor.executeAction
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class ListItemTabHandlerAdditionalTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  // Tests use the list marker width by default. Tests with fixed indentation enable that setting explicitly.
  @BeforeEach
  fun setUp() {
    val settings = CodeStyle.getSettings(myFixture.project)
    settings.getCommonSettings(MarkdownLanguage.INSTANCE).indentOptions!!.INDENT_SIZE = 4
    settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).USE_FIXED_INDENTS_FOR_SUBLISTS = false
  }

  @Test
  fun `test tab handles a list item becoming code with renumbering enabled`(): Unit = timeoutRunBlocking {
    enableFixedSublistIndent()
    val settings = MarkdownCodeInsightSettings.getInstance().state
    val renumberListsOnType = settings.renumberListsOnType
    settings.renumberListsOnType = true
    try {
      myFixture.configureByText("some.md", "- first<caret>")
      withContext(Dispatchers.EDT) {
        myFixture.executeAction(IdeActions.ACTION_EDITOR_TAB)
        myFixture.checkResult("    - first<caret>")
      }
    }
    finally {
      settings.renumberListsOnType = renumberListsOnType
    }
  }

  @Test
  fun `test tab indents a new empty item according to fixed settings`(): Unit = timeoutRunBlocking {
    enableFixedSublistIndent()
    val content = """
        - hello
        - <caret>
    """.trimIndent()
    val expected = """
        - hello
            - <caret>
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TAB)
      myFixture.checkResult(expected)
    }
  }

  @Test
  fun `test tab uses configured fixed sublist indent`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TAB)
      myFixture.checkResult(afterFirstTab)
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TAB)
      myFixture.checkResult(afterSecondTab)
      myFixture.executeAction(IdeActions.ACTION_EDITOR_UNINDENT_SELECTION)
      myFixture.checkResult(afterFirstTab)
      myFixture.executeAction(IdeActions.ACTION_EDITOR_UNINDENT_SELECTION)
      myFixture.checkResult(content)
    }
  }

  @Test
  fun `test tab uses marker width when fixed sublist indent is disabled`(): Unit = timeoutRunBlocking {
    val content = """
        - first
        - second<caret>
    """.trimIndent()
    val expected = """
        - first
          - second<caret>
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TAB)
      myFixture.checkResult(expected)
    }
  }

  @Test
  fun `test fixed sublist indent does not go before parent content`(): Unit = timeoutRunBlocking {
    enableFixedSublistIndent()
    val content = """
        1000. first
        1000. second<caret>
    """.trimIndent()
    val expected = """
        1000. first
              1000. second<caret>
    """.trimIndent()
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TAB)
      myFixture.checkResult(expected)
    }
  }

  @Test
  fun `test repeated tab and shift tab only move the new empty unordered item`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ENTER)
      myFixture.checkResult(afterEnter)

      myFixture.executeAction(IdeActions.ACTION_EDITOR_TAB)
      myFixture.checkResult(afterFirstTab)

      myFixture.executeAction(IdeActions.ACTION_EDITOR_TAB)
      myFixture.checkResult(afterSecondTab)

      myFixture.executeAction(IdeActions.ACTION_EDITOR_UNINDENT_SELECTION)
      myFixture.checkResult(afterUnindent)
    }
  }

  private fun enableFixedSublistIndent() {
    val settings = CodeStyle.getSettings(myFixture.project)
    val indentOptions = settings.getCommonSettings(MarkdownLanguage.INSTANCE).indentOptions!!
    indentOptions.TAB_SIZE = 4
    indentOptions.CONTINUATION_INDENT_SIZE = 8
    settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java).USE_FIXED_INDENTS_FOR_SUBLISTS = true
  }

  /**
   * Checks that list item is indented with 2 tabs (4 spaces) after pressing tab 2 times.
   */
  @Test
  fun `test multiple tabs are inserted correctly`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      repeat(2) {
        myFixture.executeAction(IdeActions.ACTION_EDITOR_TAB)
      }
      myFixture.checkResult(expected)
    }
  }

  @Test
  fun `test tab at the line start indents a nested item by a single level`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TAB)
      myFixture.checkResult(expected)
    }
  }

  @Test
  fun `test tab before the marker indents a nested item by a single level`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TAB)
      myFixture.checkResult(expected)
    }
  }

  @Test
  fun `test tab at the line start indents an ordered item by a single level`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TAB)
      myFixture.checkResult(expected)
    }
  }

  @Test
  fun `test tab before the marker indents an ordered item by a single level`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TAB)
      myFixture.checkResult(expected)
    }
  }

  @Test
  fun `test tab does not indent the children of the item under the caret`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TAB)
      myFixture.checkResult(expected)
    }
  }

  @Test
  fun `test tab at the start of a top level item indents it by a single level`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TAB)
      myFixture.checkResult(expected)
    }
  }

  @Test
  fun `test tab indents a list item inside a blockquote by a single level`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TAB)
      myFixture.checkResult(expected)
    }
  }

  @Test
  fun `test tab inside fenced code block within list item does not shift the list item`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TAB)
      myFixture.checkResult(expected)
    }
  }

  @Test
  fun `test tab inside indented code block within list item does not shift the list item`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TAB)
      myFixture.checkResult(expected)
    }
  }

  @Test
  fun `test tab inside table cell within list item does not shift the list item`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TAB)
      myFixture.checkResult(expected)
    }
  }

  @Test
  fun `test tab inside blockquote within list item does not shift the list item`(): Unit = timeoutRunBlocking {
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
    myFixture.configureByText("some.md", content)
    withContext(Dispatchers.EDT) {
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TAB)
      myFixture.checkResult(expected)
    }
  }

  @Test
  fun `test selected items keep their alignment on every tab and unindent press`(): Unit = timeoutRunBlocking {
    // language=Markdown
    myFixture.configureByText("some.md", """
    list:
    - 1
      - 1.1
    <selection>  - 1.2
      - 1.3
      - 1.4</selection>
    - 2
    - 3
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      indentSelection()
      // language=Markdown
      myFixture.checkResult("""
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
      myFixture.checkResult("""
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
      myFixture.checkResult("""
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
      myFixture.executeAction("EditorUnindentSelection")
      // language=Markdown
      myFixture.checkResult("""
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
  }

  @Test
  fun `test a selected item is indented together with its children`(): Unit = timeoutRunBlocking {
    // a caret instead of the selection moves only its line, see `test tab does not indent the children of the item under the caret`
    // language=Markdown
    myFixture.configureByText("some.md", """
    <selection>- aaa</selection>
      - bbb
      - ccc
    - ddd
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      indentSelection()
      // language=Markdown
      myFixture.checkResult("""
        - aaa
          - bbb
          - ccc
      - ddd
      """.trimIndent())
    }
  }

  @Test
  fun `test tab aligns selected lines below a list item to its content column`(): Unit = timeoutRunBlocking {
    enableFixedSublistIndent()
    // language=Markdown
    myFixture.configureByText("some.md", """
    - **`settings.gradle.kts`** defines the root project.

    <selection>The settings script defines the project structure:

    ```kotlin
    rootProject.name = "my-project"
        include("module-a")
    ```</selection>
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      indentSelection()
      // language=Markdown
      myFixture.checkResult("""
      - **`settings.gradle.kts`** defines the root project.

        The settings script defines the project structure:

        ```kotlin
        rootProject.name = "my-project"
            include("module-a")
        ```
      """.trimIndent())
    }
  }

  @Test
  fun `test tab aligns selected lines below a numbered item to its content column`(): Unit = timeoutRunBlocking {
    // language=Markdown
    myFixture.configureByText("some.md", """
    1. item

    <selection>text</selection>
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      indentSelection()
      // language=Markdown
      myFixture.checkResult("""
      1. item

         text
      """.trimIndent())
    }
  }

  @Test
  fun `test repeated tab steps through the content columns of the nested items above`(): Unit = timeoutRunBlocking {
    // language=Markdown
    myFixture.configureByText("some.md", """
    - a
      - b

    <selection>text</selection>
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      indentSelection()
      // language=Markdown
      myFixture.checkResult("""
      - a
        - b

        text
      """.trimIndent())

      indentSelection()
      // language=Markdown
      myFixture.checkResult("""
      - a
        - b

          text
      """.trimIndent())

      indentSelection()
      // language=Markdown
      myFixture.checkResult("""
      - a
        - b

              text
      """.trimIndent())
    }
  }

  @Test
  fun `test tab aligns selected lines inside a blockquote to the item above`(): Unit = timeoutRunBlocking {
    // language=Markdown
    myFixture.configureByText("some.md", """
    > * item
    >
    <selection>> ```
    > code
    > ```</selection>
    """.trimIndent())

    withContext(Dispatchers.EDT) {
      indentSelection()
      // language=Markdown
      myFixture.checkResult("""
      > * item
      >
      >   ```
      >   code
      >   ```
      """.trimIndent())
    }
  }

  @RequiresEdt
  private fun indentSelection() {
    myFixture.executeAction(IdeActions.ACTION_EDITOR_INDENT_SELECTION)
  }
}
