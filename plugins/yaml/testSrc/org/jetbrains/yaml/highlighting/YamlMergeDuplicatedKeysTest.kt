// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.highlighting

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.YAMLBundle
import org.jetbrains.yaml.inspections.YAMLDuplicatedKeysInspection

class YamlMergeDuplicatedKeysTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(YAMLDuplicatedKeysInspection::class.java)
  }

  fun testSimpleMerge() {
    myFixture.configureByText("test.yaml", """
      prop:
        bar: 5
      prop:
        baz: 6
    """.trimIndent())
    myFixture.checkPreviewAndLaunchAction(
      myFixture.findSingleIntention(YAMLBundle.message("YAMLDuplicatedKeysInspection.merge.quickfix.name")))
    myFixture.checkResult("""
      prop:
        bar: 5
        baz: 6<caret>
    """.trimIndent())
  }

  fun testSimpleMergeWithRepeatingProps() {
    myFixture.configureByText("test.yaml", """
      prop:
        bar: 5
        foo: 5
      prop:
        baz: 6
        foo: 5
    """.trimIndent())
    myFixture.checkPreviewAndLaunchAction(
      myFixture.findSingleIntention(YAMLBundle.message("YAMLDuplicatedKeysInspection.merge.quickfix.name")))
    myFixture.checkResult("""
      prop:
        bar: 5
        foo: 5
        baz: 6<caret>
    """.trimIndent())
  }

  fun testSimpleMergeSequences() {
    myFixture.configureByText("test.yaml", """
      prop:
        - bar
        - baz
      prop:
        - foo
        - moo
    """.trimIndent())
    myFixture.checkPreviewAndLaunchAction(
      myFixture.findSingleIntention(YAMLBundle.message("YAMLDuplicatedKeysInspection.merge.quickfix.name")))
    myFixture.checkResult("""
      prop:
        - bar
        - baz
        - foo
        - moo<caret>
    """.trimIndent())
  }

  fun testSimpleMergeSequencesWithRepeatingElements() {
    myFixture.configureByText("test.yaml", """
      prop:
        - bar
        - moo
      prop:
        - foo
        - moo
    """.trimIndent())
    myFixture.checkPreviewAndLaunchAction(
      myFixture.findSingleIntention(YAMLBundle.message("YAMLDuplicatedKeysInspection.merge.quickfix.name")))
    myFixture.checkResult("""
      prop:
        - bar
        - moo
        - foo<caret>
    """.trimIndent())
  }

  fun testMultilevelMerge() {
    myFixture.configureByText("test.yaml", """
      settings:
          compose: enabled
      
      settings:
          kotlin:
              progressiveMode: true
      
      settings:
          kotlin:
              allWarningsAsErrors: true
    """.trimIndent())
    myFixture.checkPreviewAndLaunchAction(
      myFixture.findSingleIntention(YAMLBundle.message("YAMLDuplicatedKeysInspection.merge.quickfix.name")))
    myFixture.checkResult("""
      settings:
        compose: enabled
        kotlin:
          progressiveMode: true
          allWarningsAsErrors: true<caret>
      

    """.trimIndent())
  }
}