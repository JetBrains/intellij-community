// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.copy

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.YAMLFileType
import java.awt.datatransfer.DataFlavor

class YAMLCopyTest : BasePlatformTestCase() {
  fun testSimpleCopy() { // just general simple case
    val source = """
      someKey: just <selection>hello</selection> world
    """.trimIndent()
    val expected = "hello"
    doTest(source, expected)
  }

  fun testNoAdjustOnSameLine() {
    val source = """
      object:
        <selection>someKey</selection>: just hello world
        otherKey: other value
    """.trimIndent()
    val expected = "someKey"
    doTest(source, expected)
  }

  fun testNoAdjustOnSameLineUpToTheEnd() {
    val source = """
      object:
        <selection>someKey: just hello world</selection>
        otherKey: other value
    """.trimIndent()
    val expected = "someKey: just hello world"
    doTest(source, expected)
  }

  fun testNoAdjustOnSameLineInFileEnd() {
    val source = """
      object:
        someKey: just hello world
        <selection>otherKey: other value</selection>
    """.trimIndent()
    val expected = "otherKey: other value"
    doTest(source, expected)
  }

  fun testNoAdjustAcrossMapBorder() { // let's leave this copy action as it is
    val source = """
      someKey:
        <selection>key1: value 1
        key2: value 2
      anotherKey: some value</selection>
    """.trimIndent()
    val expected = """
      |key1: value 1
      |  key2: value 2
      |anotherKey: some value
    """.trimMargin()
    doTest(source, expected)
  }

  fun testCopySubMap() {
    val source = """
      someKey:
        <selection>key1: value 1
        key2: value 2</selection>
        key3: value 3
    """.trimIndent()
    val expected = """
      |  key1: value 1
      |  key2: value 2
    """.trimMargin()
    doTest(source, expected)
  }

  fun testCopyEndOfMap() {
    val source = """
      |someKey:
      |  <selection>key1: value 1
      |  key2: value 2
      |
      |</selection>
    """.trimMargin()
    val expected = """
      |  key1: value 1
      |  key2: value 2
      |
      |
    """.trimMargin()
    doTest(source, expected)
  }

  fun testSubsequence() {
    val source = """
      |someKey:
      |  - item 1
      |  <selection>- item 2
      |  - item 3
      |
      |</selection>
    """.trimMargin()
    val expected = """
      |  - item 2
      |  - item 3
      |
      |
    """.trimMargin()
    doTest(source, expected)
  }

  fun testCopyInlinedMap() {
    val source = """
      someKey:
        - <selection>key1: value 1
          key2: value 2</selection>
          key3: value 3
    """.trimIndent()
    val expected = """
      |    key1: value 1
      |    key2: value 2
    """.trimMargin()
    doTest(source, expected)
  }

  fun testCopyInlinedSequence() {
    val source = """
      someKey:
        - <selection>- item 1
          - item 2</selection>
          - item 3
    """.trimIndent()
    val expected = """
      |    - item 1
      |    - item 2
    """.trimMargin()
    doTest(source, expected)
  }

  private fun doTest(source: String, expected: String) {
    myFixture.configureByText(YAMLFileType.YML, source)
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COPY)
    val contents = CopyPasteManager.getInstance().contents ?: error("No content after copy")
    val resultText = contents.getTransferData(DataFlavor.stringFlavor) as String
    assertEquals(expected, resultText)
  }
}
