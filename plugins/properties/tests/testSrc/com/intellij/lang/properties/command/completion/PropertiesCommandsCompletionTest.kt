// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.command.completion

import com.intellij.codeInsight.completion.lightCompletionFixture
import com.intellij.codeInsight.completion.lightCompletionProjectFixture
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.lang.properties.PropertiesFileType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.NeedsIndex
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@NeedsIndex.SmartMode(reason = "it requires highlighting")
@TestApplication
class PropertiesCommandsCompletionTest {
  companion object {
    private val projectFixture = lightCompletionProjectFixture()
  }

  private val completion by lightCompletionFixture(projectFixture)
  private val myFixture get() = completion.fixture

  @BeforeEach
  fun setUp() {
    Registry.get("ide.completion.command.enabled").setValue(false, myFixture.testRootDisposable)
    Registry.get("ide.completion.command.force.enabled").setValue(true, myFixture.testRootDisposable)
  }

  @Test
  fun testRename(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c.<caret>=some.strange
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Rename", ignoreCase = true) })
  }

  @Test
  fun testFindUsages(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c.<caret>=some.strange
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Go to declaration or usages", ignoreCase = true) })
  }

  @Test
  fun testReformat(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c    =  some.strange .<caret>
      """.trimIndent())
    val elements = myFixture.completeBasic()
    completion.selectItem(elements.first { element -> element.lookupString.contains("Reformat", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
        a.b.c=some.strange 
        """.trimIndent())
  }

  @Test
  fun testMovePropertyUp(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=first
      d.e.f=second.<caret>
      """.trimIndent())
    val elements = myFixture.completeBasic()
    completion.selectItem(elements.first { element -> element.lookupString.contains("Move Statement Up", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      d.e.f=second
      a.b.c=first
      
      """.trimIndent())
  }

  @Test
  fun testMovePropertyUpMultiline(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=first
      d.e.f=second \
        some.<caret>
      """.trimIndent())
    val elements = myFixture.completeBasic()
    completion.selectItem(elements.first { element -> element.lookupString.contains("Move Statement Up", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      d.e.f=second \
        some
      a.b.c=first
      
      """.trimIndent())
  }

  @Test
  fun testMovePropertyDownMultiline(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=first
      d.e.f=second \
                some2.<caret>
      c.b.a=third \
               some3
      """.trimIndent())
    val elements = myFixture.completeBasic()
    completion.selectItem(elements.first { element -> element.lookupString.contains("Move Statement Down", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      a.b.c=first
      c.b.a=third \
               some3
      d.e.f=second \
                some2
      
      """.trimIndent())
  }

  @Test
  fun testMovePropertyDown(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=first.<caret>
      d.e.f=second
      """.trimIndent())
    val elements = myFixture.completeBasic()
    completion.selectItem(elements.first { element -> element.lookupString.contains("Move Statement Down", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      d.e.f=second
      a.b.c=first
      
      """.trimIndent())
  }

  @Test
  fun testMovePropertyUpWithMultilineAbove(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=first \
        continued
      d.e.f=second.<caret>
      """.trimIndent())
    val elements = myFixture.completeBasic()
    completion.selectItem(elements.first { element -> element.lookupString.contains("Move Statement Up", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      d.e.f=second
      a.b.c=first \
        continued
      
      """.trimIndent())
  }

  @Test
  fun testMovePropertyDownWithMultilineBelow(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=first.<caret>
      d.e.f=second \
        continued
      """.trimIndent())
    val elements = myFixture.completeBasic()
    completion.selectItem(elements.first { element -> element.lookupString.contains("Move Statement Down", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      d.e.f=second \
        continued
      a.b.c=first
      
      """.trimIndent())
  }

  @Test
  fun testMovePropertyUpWithMultilineBelow(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=first
      d.e.f=second.<caret>
      e.f.g=third \
        continued
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Move Statement Up", ignoreCase = true) })
  }

  @Test
  fun testMovePropertyDownWithMultilineAbove(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=first \
        continued
      d.e.f=second.<caret>
      e.f.g=third
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Move Statement Down", ignoreCase = true) })
  }

  @Test
  fun testDoubleDot(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c.<caret>    =  some.strange 
      """.trimIndent())
    myFixture.completeBasic()
    myFixture.type('.')
    val l = LookupManager.getActiveLookup(myFixture.editor)!!
    val elements = l.items
    completion.selectItem(elements.first { element -> element.lookupString.contains("Reformat", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
        a.b.c=some.strange 
        """.trimIndent())
  }

  @Test
  fun testCommentProperty(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=some.value.<caret>
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Comment", ignoreCase = true) })
  }

  @Test
  fun testCommentPropertyExecution(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=some.value.<caret>
      """.trimIndent())
    val elements = myFixture.completeBasic()
    completion.selectItem(elements.first { element -> element.lookupString.contains("Comment", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      #a.b.c=some.value
      """.trimIndent())
  }

  @Test
  fun testCommentNotAvailableForCommentedProperty(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      #a.b.c=some.value.<caret>
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNull(elements.firstOrNull { element -> element.lookupString.contains("Comment", ignoreCase = true) })
  }

  @Test
  fun testCommentMultilineProperty(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=line1 \
        line2 \
        line3.<caret>
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Comment", ignoreCase = true) })
  }

  @Test
  fun testCommentMultilinePropertyExecution(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.c=line1 \
        line2 \
        line3.<caret>
      """.trimIndent())
    val elements = myFixture.completeBasic()
    completion.selectItem(elements.first { element -> element.lookupString.contains("Comment", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      #a.b.c=line1 \
      #  line2 \
      #  line3
      """.trimIndent())
  }

  @Test
  fun testCommentNotAvailableInMiddle(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      a.b.<caret>c=some.value
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNull(elements?.firstOrNull { element -> element.lookupString.contains("Comment property", ignoreCase = true) })
  }

  @Test
  fun testUncommentPropertyWithDoubleDot(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      #a.b.c=some.value..<caret>
      """.trimIndent())
    val elements = myFixture.completeBasic()
    assertNotNull(elements?.firstOrNull { element -> element.lookupString.contains("Uncomment", ignoreCase = true) },
      "Uncomment command should be available for explicit double-dot invocation at end of comment")
  }

  @Test
  fun testUncommentPropertyWithDoubleDotExecution(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      #a.b.c=some.value..<caret>
      """.trimIndent())
    val elements = myFixture.completeBasic()
    completion.selectItem(elements.first { element -> element.lookupString.contains("Uncomment", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      a.b.c=some.value
      """.trimIndent())
  }

  @Test
  fun testUncommentMultilineCommentWithDoubleDotExecution(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      ###a.b.c=some.value..<caret>
      """.trimIndent())
    val elements = myFixture.completeBasic()
    completion.selectItem(elements.first { element -> element.lookupString.contains("Uncomment", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    myFixture.checkResult("""
      ##a.b.c=some.value
      """.trimIndent())
  }

  @Test
  fun testUncommentMultilinePropertyWithDoubleDotExecution(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    myFixture.configureByText(PropertiesFileType.INSTANCE, """
      #a.b.c=line1 \
      #  line2 \
      #  line3..<caret>
      """.trimIndent())
    val elements = myFixture.completeBasic()
    completion.selectItem(elements.first { element -> element.lookupString.contains("Uncomment", ignoreCase = true) })
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    //here only the line is uncommented, it is expected behavior
    myFixture.checkResult("""
      #a.b.c=line1 \
      #  line2 \
        line3
      """.trimIndent())
  }
}
