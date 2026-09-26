// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.editor

import com.intellij.lang.properties.PropertiesFileType
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PropertiesStatementUpDownMoverTest : BasePlatformTestCase() {

  fun testMoveSingleLinePropertyUp() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=first
      d.e.f=sec<caret>ond
      
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION)
    myFixture.checkResult("""
      d.e.f=second
      a.b.c=first
      
    """.trimIndent())
  }

  fun testMoveSingleLinePropertyDown() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=fir<caret>st
      d.e.f=second
      
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION)
    myFixture.checkResult("""
      d.e.f=second
      a.b.c=first
      
    """.trimIndent())
  }

  fun testMoveMultilinePropertyUp() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=first
      d.e.f=sec<caret>ond \
        continued
        
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION)
    myFixture.checkResult("""
      d.e.f=second \
        continued
      a.b.c=first
        
    """.trimIndent())
  }

  fun testMoveMultilinePropertyDown() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=fir<caret>st \
        continued
      d.e.f=second
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION)
    myFixture.checkResult("""
      d.e.f=second
      a.b.c=first \
        continued
      
    """.trimIndent())
  }

  fun testMoveSingleLineUpPastMultiline() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=first \
        continued
      d.e.f=sec<caret>ond
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION)
    myFixture.checkResult("""
      d.e.f=second
      a.b.c=first \
        continued
      
    """.trimIndent())
  }

  fun testMoveSingleLineDownPastMultiline() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=fir<caret>st
      d.e.f=second \
        continued
        
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION)
    myFixture.checkResult("""
      d.e.f=second \
        continued
      a.b.c=first
        
    """.trimIndent())
  }

  fun testMoveMultilinePastMultiline() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=fir<caret>st \
        line2
      d.e.f=second \
        line2
        
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION)
    myFixture.checkResult("""
      d.e.f=second \
        line2
      a.b.c=first \
        line2
        
    """.trimIndent())
  }

  fun testCannotMoveFirstPropertyUp() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=fir<caret>st
      d.e.f=second
      
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION)
    myFixture.checkResult("""
      a.b.c=first
      d.e.f=second
      
    """.trimIndent())
  }

  fun testCannotMoveLastPropertyDown() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=first
      d.e.f=sec<caret>ond
      
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION)
    myFixture.checkResult("""
      a.b.c=first
      d.e.f=second
      
    """.trimIndent())
  }

  fun testCannotMoveSinglePropertyUp() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=fir<caret>st
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION)
    myFixture.checkResult("""
      a.b.c=first
    """.trimIndent())
  }

  fun testCannotMoveSinglePropertyDown() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=fir<caret>st
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION)
    myFixture.checkResult("""
      a.b.c=first
    """.trimIndent())
  }

  fun testMoveMultilineUpPastMultiline() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b=first \
        continued 1
      c.d=second \
        continued 2
      e.f=third \
        cont<caret>inued 3
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION)
    myFixture.checkResult("""
      a.b=first \
        continued 1
      e.f=third \
        continued 3
      c.d=second \
        continued 2
      
    """.trimIndent())
  }

  fun testMoveMultilineDownPastMultiline() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b=first \
        continued<caret> 1
      c.d=second \
        continued 2
      e.f=third \
        continued 3
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION)
    myFixture.checkResult("""
      c.d=second \
        continued 2
      a.b=first \
        continued 1
      e.f=third \
        continued 3
    """.trimIndent())
  }
}