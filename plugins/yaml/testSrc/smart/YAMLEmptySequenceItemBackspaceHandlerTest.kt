// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.smart

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class YAMLEmptySequenceItemBackspaceHandlerTest : BasePlatformTestCase() {
  fun testDeleteSequenceMarkerWithSpace() {
    myFixture.configureByText("test.yml", """
      foo:
        bar:
          baz:
            - item1
            - <caret>
      
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
    myFixture.checkResult("""
      foo:
        bar:
          baz:
            - item1
            <caret>
      
    """.trimIndent())
  }

  fun testDeleteSequenceMarkerWithSpaceBeforeValue() {
    myFixture.configureByText("test.yml", """
      foo:
        - <caret>item1
      
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
    myFixture.checkResult("""
      foo:
        <caret>item1
      
    """.trimIndent())
  }

  fun testDeleteSequenceMarkerWithSpaceInEndOfFile() {
    myFixture.configureByText("test.yml", """
      foo:
        - <caret>
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
    myFixture.checkResult("""
      foo:
        <caret>
    """.trimIndent())
  }


  fun testDeletingSequenceMarkerDownNotDeleteAnythingElse() {
    myFixture.configureByText("test.yml", """
      foo:
        -<caret>item1
      
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
    myFixture.checkResult("""
      foo:
        <caret>item1
      
    """.trimIndent())
  }

  fun testNoSmartDeleteWhenNotYamlFile() {
    myFixture.configureByText("test.txt", """
      foo:
        - <caret>
      
    """.trimIndent())
    // First backspace deletes the space
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
    myFixture.checkResult("""
      foo:
        -<caret>
      
    """.trimIndent())
  }

  fun testNoDeleteWhenNotSequenceMarker() {
    myFixture.configureByText("test.yml", """
      foo:
        x <caret>
      
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
    myFixture.checkResult("""
      foo:
        x<caret>
      
    """.trimIndent())
  }
}