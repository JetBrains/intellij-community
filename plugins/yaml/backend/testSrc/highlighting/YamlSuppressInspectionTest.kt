// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.highlighting

import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.inspections.YAMLUnusedAnchorInspection

class YamlSuppressInspectionTest: BasePlatformTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(YAMLUnusedAnchorInspection::class.java)
  }

  fun testInspectionSuppressedByComment() {
    myFixture.configureByText("test.yaml", """
      someKey: <warning descr="Anchor unusedAnchor is never used">&unusedAnchor</warning>
        - item1
        - item2
      nextKey: &usedAnchor
        - item1_2
        - item2_2
      useOfAnchor: *usedAnchor
      # noinspection YAMLUnusedAnchor
      anotherKey: &suppressedUnusedAnchor
    """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testInspectionSuppressedByCommentInNested() {
    myFixture.configureByText("test.yaml", """
      someKey: <warning descr="Anchor unusedAnchor is never used">&unusedAnchor</warning>
        - item1
        - item2
      # noinspection YAMLUnusedAnchor
      # empty line
      
      toplevel:
        nested:
          - anotherKey: &suppressedUnusedAnchor
    """.trimIndent())
    myFixture.testHighlighting()
  }
  
  fun testInspectionSuppressedByTopFileComment() {
    myFixture.configureByText("test.yaml", """
      #file: noinspection YAMLUnusedAnchor
      someKey: &unusedAnchor
        - item1
        - item2
      toplevel:
        nested:
          - anotherKey: &suppressedUnusedAnchor
    """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testInspectionSuppressedByCommentQuickFix() {
    myFixture.configureByText("test.yaml", """
      someKey: &unusedAnchor
        - item1
        - item2
      nextKey: &usedAnchor
        - item1_2
        - item2_2
      useOfAnchor: *usedAnchor
      anotherKey: &suppressed<caret>UnusedAnchor
    """.trimIndent())
    val intention = myFixture.findSingleIntention("Suppress 'YAMLUnusedAnchor' for key 'anotherKey'")
    myFixture.launchAction(intention)
    myFixture.checkResult("""
      someKey: &unusedAnchor
        - item1
        - item2
      nextKey: &usedAnchor
        - item1_2
        - item2_2
      useOfAnchor: *usedAnchor
      # noinspection YAMLUnusedAnchor
      anotherKey: &suppressedUnusedAnchor
    """.trimIndent())
  }

  fun testSuppressInNested() {
    myFixture.configureByText("test.yaml", """
      someKey: &unusedAnchor
        - item1
        - item2
      toplevel:
        nested:
          - anotherKey: &suppressed<caret>UnusedAnchor
    """.trimIndent())
    val intention = myFixture.findSingleIntention("Suppress 'YAMLUnusedAnchor' for key 'anotherKey'")
    myFixture.launchAction(intention)
    myFixture.checkResult("""
      someKey: &unusedAnchor
        - item1
        - item2
      toplevel:
        nested:
          - # noinspection YAMLUnusedAnchor
            anotherKey: &suppressedUnusedAnchor
    """.trimIndent())
    val highligted = myFixture.doHighlighting().map { it.text }
    UsefulTestCase.assertContainsElements(highligted, "&unusedAnchor")
    UsefulTestCase.assertDoesntContain(highligted, "&suppressedUnusedAnchor")
  } 
  fun testSuppressInNestedExisting() {
    myFixture.configureByText("test.yaml", """
      someKey: &unusedAnchor
        - item1
        - item2
      toplevel:
        nested:
          - # noinspection SomeInspection
            anotherKey: &suppressed<caret>UnusedAnchor
    """.trimIndent())
    val intention = myFixture.findSingleIntention("Suppress 'YAMLUnusedAnchor' for key 'anotherKey'")
    myFixture.launchAction(intention)
    myFixture.checkResult("""
      someKey: &unusedAnchor
        - item1
        - item2
      toplevel:
        nested:
          - # noinspection SomeInspection,YAMLUnusedAnchor
            anotherKey: &suppressedUnusedAnchor
    """.trimIndent())
    val highligted = myFixture.doHighlighting().map { it.text }
    UsefulTestCase.assertContainsElements(highligted, "&unusedAnchor")
    UsefulTestCase.assertDoesntContain(highligted, "&suppressedUnusedAnchor")
  } 
  
  fun testSuppressInFile() {
    myFixture.configureByText("test.yaml", """
      someKey: &unusedAnchor
        - item1
        - item2
      toplevel:
        nested:
          - anotherKey: &suppressed<caret>UnusedAnchor
    """.trimIndent())
    val intention = myFixture.findSingleIntention("Suppress 'YAMLUnusedAnchor' for file 'test.yaml'")
    myFixture.launchAction(intention)
    myFixture.checkResult("""
      #file: noinspection YAMLUnusedAnchor
      someKey: &unusedAnchor
        - item1
        - item2
      toplevel:
        nested:
          - anotherKey: &suppressedUnusedAnchor
    """.trimIndent())
    myFixture.testHighlighting()
  }  
  
  fun testSuppressInFileExisting() {
    myFixture.configureByText("test.yaml", """
      #file: noinspection SomeInspection
      someKey: &unusedAnchor
        - item1
        - item2
      toplevel:
        nested:
          - anotherKey: &suppressed<caret>UnusedAnchor
    """.trimIndent())
    val intention = myFixture.findSingleIntention("Suppress 'YAMLUnusedAnchor' for file 'test.yaml'")
    myFixture.launchAction(intention)
    myFixture.checkResult("""
      #file: noinspection SomeInspection,YAMLUnusedAnchor
      someKey: &unusedAnchor
        - item1
        - item2
      toplevel:
        nested:
          - anotherKey: &suppressedUnusedAnchor
    """.trimIndent())
    myFixture.testHighlighting()
  }



}