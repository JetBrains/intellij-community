// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.editing

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class YAMLStatementMoverTest : BasePlatformTestCase() {
  fun testTopKeyValueUp() {
    myFixture.configureByText("test.yaml", """
      top1:
        child1: hi
      top2<caret>:
        child2: bye
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION)
    //Note: move statement algorithm adds empty line to the end (if no one)
    myFixture.checkResult("""
      top2<caret>:
        child2: bye
      top1:
        child1: hi
    """.trimIndent() + "\n")
  }

  fun testTopKeyValueDown() {
    myFixture.configureByText("test.yaml", """
      top1<caret>:
        child1: hi
      top2:
        child2: bye
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION)
    //Note: move statement algorithm adds empty line to the end (if no one)
    myFixture.checkResult("""
      top2:
        child2: bye
      top1<caret>:
        child1: hi
    """.trimIndent() + "\n")
  }

  fun testNonTopKeyValueDown() {
    myFixture.configureByText("test.yaml", """
      megatop:
        subtop1<caret>:
          child1: hi
        subtop2:
          child2: bye
        subtop3:
          child3: hello
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION)
    myFixture.checkResult("""
      megatop:
        subtop2:
          child2: bye
        subtop1<caret>:
          child1: hi
        subtop3:
          child3: hello
    """.trimIndent())
  }

  fun testNonTopKeyValueUp() {
    myFixture.configureByText("test.yaml", """
      megatop:
        subtop1:
          child1: hi
        subtop2<caret>:
          child2: bye
        subtop3:
          child3: hello
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION)
    myFixture.checkResult("""
      megatop:
        subtop2<caret>:
          child2: bye
        subtop1:
          child1: hi
        subtop3:
          child3: hello
    """.trimIndent())
  }

  fun testNonTopKeyValueDownWithSelection() {
    myFixture.configureByText("test.yaml", """
      megatop:
        <selection>subtop1:
          child1: hi
        subtop2:
          child2: bye</selection>
        subtop3:
          child3: hello
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION)
    //Note: move statement algorithm adds empty line to the end (if no one)
    myFixture.checkResult("""
      megatop:
        subtop3:
          child3: hello
        <selection>subtop1:
          child1: hi
        subtop2:
          child2: bye</selection>
    """.trimIndent() + "\n")
  }

  fun testNonTopKeyValueUpWithSelection() {
    myFixture.configureByText("test.yaml", """
      megatop:
        subtop1:
          child1: hi
        <selection>subtop2:
          child2: bye
        subtop3:
          child3: hello</selection>
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION)
    //Note: move statement algorithm adds empty line to the end (if no one)
    myFixture.checkResult("""
      megatop:
        <selection>subtop2:
          child2: bye
        subtop3:
          child3: hello</selection>
        subtop1:
          child1: hi
    """.trimIndent() + "\n")
  }

  fun testTopItemDown() {
    myFixture.configureByText("test.yaml", """
      <caret>- subkey1:
          value 1
        subkey2:
          value 2
      - item 2
      - item 3
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION)
    myFixture.checkResult("""
      - item 2
      <caret>- subkey1:
          value 1
        subkey2:
          value 2
      - item 3
    """.trimIndent())
  }

  fun testTopItemUp() {
    myFixture.configureByText("test.yaml", """
      - subkey1:
          value 1
        subkey2:
          value 2
      - item 2<caret>
      - item 3
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION)
    myFixture.checkResult("""
      - item 2<caret>
      - subkey1:
          value 1
        subkey2:
          value 2
      - item 3
    """.trimIndent())
  }

  fun testNonTopItemDown() {
    myFixture.configureByText("test.yaml", """
      top-array:
        <caret>- subkey1:
            value 1
          subkey2:
            value 2
        - subkey3:
            value 1
          subkey4:
            value 2
        - item 3
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION)
    myFixture.checkResult("""
      top-array:
        - subkey3:
            value 1
          subkey4:
            value 2
        <caret>- subkey1:
            value 1
          subkey2:
            value 2
        - item 3
    """.trimIndent())
  }

  fun testNonTopItemUp() {
    myFixture.configureByText("test.yaml", """
      top-array:
        - subkey1:
            value 1
          subkey2:
            value 2
        <caret>- subkey3:
            value 1
          subkey4:
            value 2
        - item 3
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION)
    myFixture.checkResult("""
      top-array:
        <caret>- subkey3:
            value 1
          subkey4:
            value 2
        - subkey1:
            value 1
          subkey2:
            value 2
        - item 3
    """.trimIndent())
  }

  fun testNonTopItemDownWithSelectionParts() {
    myFixture.configureByText("test.yaml", """
      top-array:
        - subkey1:
            value 1
          <selection>subkey2:
            value 2
        - subkey3:
            value 3</selection>
          subkey4:
            value 4
        - subkey5:
            value 5
          subkey6:
            value 6
        - item 4
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION)
    myFixture.checkResult("""
      top-array:
        - subkey5:
            value 5
          subkey6:
            value 6
        - subkey1:
            value 1
          <selection>subkey2:
            value 2
        - subkey3:
            value 3</selection>
          subkey4:
            value 4
        - item 4
    """.trimIndent())
  }

  fun testMoveLastDown() {
    val fileContent = """
      top1: hi
      top2<caret>: bye

    """.trimIndent()
    myFixture.configureByText("test.yaml", fileContent)
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION)
    // Nothing should be changed
    myFixture.checkResult(fileContent)
  }
}