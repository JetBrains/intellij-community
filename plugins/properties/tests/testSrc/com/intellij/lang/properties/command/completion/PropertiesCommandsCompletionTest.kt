// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.command.completion

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.lang.properties.PropertiesFileType
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.NeedsIndex

@NeedsIndex.SmartMode(reason = "it requires highlighting")
class PropertiesCommandsCompletionTest : LightFixtureCompletionTestCase() {

  override fun setUp() {
    super.setUp()
    Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable())
    Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
  }

  fun testRename() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c.<caret>=some.strange
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Rename", ignoreCase = true) })
  }

  fun testFindUsages() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c.<caret>=some.strange
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Go to declaration or usages", ignoreCase = true) })
  }

  fun testReformat() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c    =  some.strange .<caret>
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Reformat", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
        a.b.c=some.strange 
        """.trimIndent())
  }

  fun testMoveLineUp() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=first
      d.e.f=second.<caret>
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Move line up", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      d.e.f=second
      a.b.c=first
      
      """.trimIndent())
  }

  fun testMoveLineUpMultiline() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=first
      d.e.f=second \.<caret>
        some
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNull(elements.firstOrNull { element -> element.lookupString.contains("Move line up", ignoreCase = true) })
  }

  fun testMoveLineDownMultiline() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=first
      d.e.f=second \.<caret>
                some
      c.b.a=third \
               some
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNull(elements.firstOrNull { element -> element.lookupString.contains("Move line down", ignoreCase = true) })
  }

  fun testMoveLineDown() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=first.<caret>
      d.e.f=second
      """.trimIndent())
    val elements = myFixture.completeBasic()
    selectItem(elements.first { element -> element.lookupString.contains("Move line down", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      d.e.f=second
      a.b.c=first
      
      """.trimIndent())
  }

  fun testMoveLineUpWithMultilineAbove() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=first \
        continued
      d.e.f=second.<caret>
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNull(elements.firstOrNull { element -> element.lookupString.contains("Move line up", ignoreCase = true) })
  }

  fun testMoveLineDownWithMultilineBelow() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=first.<caret>
      d.e.f=second \
        continued
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNull(elements.firstOrNull { element -> element.lookupString.contains("Move line down", ignoreCase = true) })
  }

  fun testMoveLineUpWithMultilineBelow() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=first
      d.e.f=second.<caret>
      e.f.g=third \
        continued
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Move line up", ignoreCase = true) })
  }

  fun testMoveLineDownWithMultilineAbove() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=first \
        continued
      d.e.f=second.<caret>
      e.f.g=third
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Move line down", ignoreCase = true) })
  }

  fun testDoubleDot() {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c.<caret>    =  some.strange 
      """.trimIndent())
    myFixture.completeBasic()
    myFixture.type('.')
    val l = LookupManager.getActiveLookup(myFixture.editor)!!
    val elements = l.items
    selectItem(elements.first { element -> element.lookupString.contains("Reformat", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
        a.b.c=some.strange 
        """.trimIndent())
  }
}