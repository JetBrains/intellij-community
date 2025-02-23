// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.icons.AllIcons
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import javax.swing.Icon

class YAMLStructuralKeysCompletionTest : BasePlatformTestCase() {

  fun testInArrayObjectKeysCompletion() {
    myFixture.configureByText("test.yaml", """
      root:
        - boo:
            aa: 1
            bb: "2"
          gaa: 2
        - <caret>

    """.trimIndent())
    val lookupElements = myFixture.complete(CompletionType.BASIC).map(::render)
    UsefulTestCase.assertContainsElements(
      lookupElements,
      mapping("boo"),
      number("gaa"),
    )

  }

  private fun number(name: String) = LookupPresentation(name, IconManager.getInstance().getPlatformIcon(PlatformIcons.Property), "number")
  private fun string(name: String) = LookupPresentation(name, IconManager.getInstance().getPlatformIcon(PlatformIcons.Property), "string")
  private fun mapping(name: String) = LookupPresentation(name, AllIcons.Json.Object, "object")

  private data class LookupPresentation(val lookupString: String, val icon: Icon?, val typeDescription: String?)

  private fun render(lookupElement: LookupElement): LookupPresentation {
    val presentation = LookupElementPresentation()
    lookupElement.renderElement(presentation)
    return LookupPresentation(presentation.itemText ?: "<none>", presentation.icon, presentation.typeText)
  }

  fun testInDeeplyNested() {
    myFixture.configureByText("test.yaml", """
      top1:
        top2:
          - abc: 1
            inner:
              inner1: "value"
              inner2: "value"
          - abc: 3
            inner:
              inner3: "value"
          - abc: 4
            inner:
              <caret>
    """.trimIndent())

    val lookupElements = myFixture.complete(CompletionType.BASIC).map(::render)
    UsefulTestCase.assertContainsElements(
      lookupElements,
      string("inner1"),
      string("inner2"),
      string("inner3"),
    )
  }

  fun testDontCompleteExistingKeys() {
    myFixture.configureByText("test.yaml", """
      root:
        - boo:
            aa: 1
            bb: "2"
            cc: 2
          gaa: 2
        - gaa: 1
          <caret>

    """.trimIndent())
    val lookupElements = myFixture.complete(CompletionType.BASIC).map(::render)
    UsefulTestCase.assertContainsElements(
      lookupElements,
      mapping("boo")
    )
    UsefulTestCase.assertDoesntContain(
      lookupElements,
      number("gaa")
    )

  }

  fun testInsertHandlerForObject() {
    myFixture.configureByText("test.yaml", """
      root:
        - boo:
            aa: 1
            bb: "2"
          gaa: 2
        - bo<caret>

    """.trimIndent())
    val variants = myFixture.completeBasic()
    (myFixture.lookup as? LookupImpl)?.finishLookup(Lookup.NORMAL_SELECT_CHAR, variants.first())
    myFixture.checkResult("""
      root:
        - boo:
            aa: 1
            bb: "2"
          gaa: 2
        - boo:
            
      
    """.trimIndent())
  }

  fun testInsertHandlerForArray() {
    myFixture.configureByText("test.yaml", """
      root:
        - boo:
            - "aa"
            - "bb"
          gaa: 2
        - bo<caret>

    """.trimIndent())
    val variants = myFixture.completeBasic()
    (myFixture.lookup as? LookupImpl)?.finishLookup(Lookup.NORMAL_SELECT_CHAR, variants.first())
    myFixture.checkResult("""
      root:
        - boo:
            - "aa"
            - "bb"
          gaa: 2
        - boo:
            - 
      
    """.trimIndent())
  }

  fun testInsertHandlerForScalar() {
    myFixture.configureByText("test.yaml", """
      root:
        - boo:
            - "aa"
            - "bb"
          gaa: 2
        - ga<caret>

    """.trimIndent())
    val variants = myFixture.completeBasic()
    (myFixture.lookup as? LookupImpl)?.finishLookup(Lookup.NORMAL_SELECT_CHAR, variants.first())
    myFixture.checkResult("""
      root:
        - boo:
            - "aa"
            - "bb"
          gaa: 2
        - gaa: 
      
    """.trimIndent())
  }

  fun testNotCompletingTheKeyUnderTheCaret() {
    myFixture.configureByText("test.yaml", """
      someStuff:
        - foo<caret>: 1
    """.trimIndent())
    val lookupElements = myFixture.complete(CompletionType.BASIC).map(::render)
    UsefulTestCase.assertEmpty(lookupElements)
  }
}