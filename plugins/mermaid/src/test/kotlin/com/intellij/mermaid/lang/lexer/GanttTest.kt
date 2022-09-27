package com.intellij.mermaid.lang.lexer

import com.intellij.mermaid.lang.lexer.MermaidTokens.CALL
import com.intellij.mermaid.lang.lexer.MermaidTokens.CLICK
import com.intellij.mermaid.lang.lexer.MermaidTokens.CLICK_DATA
import com.intellij.mermaid.lang.lexer.MermaidTokens.CLOSE_ROUND
import com.intellij.mermaid.lang.lexer.MermaidTokens.COLON
import com.intellij.mermaid.lang.lexer.MermaidTokens.COMMA
import com.intellij.mermaid.lang.lexer.MermaidTokens.DOUBLE_QUOTE
import com.intellij.mermaid.lang.lexer.MermaidTokens.EOL
import com.intellij.mermaid.lang.lexer.MermaidTokens.Gantt.DATE_FORMAT
import com.intellij.mermaid.lang.lexer.MermaidTokens.Gantt.EXCLUDES
import com.intellij.mermaid.lang.lexer.MermaidTokens.Gantt.GANTT
import com.intellij.mermaid.lang.lexer.MermaidTokens.Gantt.TODAY_MARKER
import com.intellij.mermaid.lang.lexer.MermaidTokens.HREF
import com.intellij.mermaid.lang.lexer.MermaidTokens.LINE_COMMENT
import com.intellij.mermaid.lang.lexer.MermaidTokens.OPEN_ROUND
import com.intellij.mermaid.lang.lexer.MermaidTokens.SECTION
import com.intellij.mermaid.lang.lexer.MermaidTokens.SECTION_TITLE
import com.intellij.mermaid.lang.lexer.MermaidTokens.STRING_VALUE
import com.intellij.mermaid.lang.lexer.MermaidTokens.TASK_DATA
import com.intellij.mermaid.lang.lexer.MermaidTokens.TASK_NAME
import com.intellij.mermaid.lang.lexer.MermaidTokens.TITLE
import com.intellij.mermaid.lang.lexer.MermaidTokens.TITLE_VALUE
import com.intellij.mermaid.lang.lexer.MermaidTokens.WHITE_SPACE

class GanttTest : MermaidLexerTestCase() {
  override val diagramName: String
    get() = "gantt"

  fun `test simple gantt`() {
    val content = """
    gantt
      title A Gantt Diagram
      dateFormat  YYYY-MM-DD
      section Section
      A task           :a1, 2014-01-01, 30d
      Another task     :after a1  , 20d
      section Another
      Task in sec      :2014-01-12  , 12d
      another task      : 24d
    """.trimIndent()
    doTest(content)
  }

  fun `test full complex`() {
    val content = """
    gantt
      dateFormat  YYYY-MM-DD
      title       Adding GANTT diagram functionality to mermaid
      excludes    weekends
      %% (`excludes` accepts specific dates in YYYY-MM-DD format, days of the week ("sunday") or "weekends", but not the word "weekdays".)
  
      section A section
      Completed task            :done,    des1, 2014-01-06,2014-01-08
      Active task               :active,  des2, 2014-01-09, 3d
      Future task               :         des3, after des2, 5d
      Future task2              :         des4, after des3, 5d
  
      section Critical tasks
      Completed task in the critical line :crit, done, 2014-01-06,24h
      Implement parser and jison          :crit, done, after des1, 2d
      Create tests for parser             :crit, active, 3d
      Future task in critical line        :crit, 5d
      Create tests for renderer           :2d
      Add to mermaid                      :1d
      Functionality added                 :milestone, 2014-01-25, 0d
  
      section Documentation
      Describe gantt syntax               :active, a1, after des1, 3d
      Add gantt diagram to demo page      :after a1  , 20h
      Add another diagram to demo page    :doc1, after a1  , 48h
  
      section Last section
      Describe gantt syntax               :after doc1, 3d
      Add gantt diagram to demo page      :20h
      Add another diagram to demo page    :48h
    """.trimIndent()
    doTest(content)
  }

  fun `test click statements`() {
    val content = """
    gantt
      Visit mermaidjs :active, cl1, 2014-01-07, 3d
      Print arguments :cl2, after cl1, 3d
      Print task :cl3, after cl2, 3d

      click cl1 href "https://mermaidjs.github.io/"
      click cl2 call printArguments("test1", "test2", test3)
      click cl3 call printTask()
    """.trimIndent()
    doTest(content)
  }

  fun `test today marker`() {
    val content = """
    gantt
      todayMarker off
      todayMarker stroke-width:5px,stroke:#0f0,opacity:0.5
    """.trimIndent()
    doTest(content)
  }
}
