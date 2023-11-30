//// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.inlineExpandConversation

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.yaml.YAMLBundle


class YAMLInlineExpandTest : BasePlatformTestCase() {

  private fun doInlineActionTest(testCase: String, testCaseAnswer: String) {
    myFixture.configureByText("test.yaml", testCase)
    val inlineAction = myFixture.findSingleIntention(YAMLBundle.message("yaml.intention.name.inline.collection"))
    myFixture.launchAction(inlineAction)
    myFixture.checkResult(testCaseAnswer)
  }

  private fun doExpandActionTest(testCase: String, testCaseAnswer: String) {
    myFixture.configureByText("test.yaml", testCase)
    val expandAction = myFixture.findSingleIntention(YAMLBundle.message("yaml.intention.name.expand.collection"))
    myFixture.launchAction(expandAction)
    myFixture.checkResult(testCaseAnswer)
  }

  private fun doExpandActinTestWithCustomIndent(indent: Int, testCase: String, testCaseAnswer: String) {
    myFixture.configureByText("test.yaml", testCase)
    val startIndent = CodeStyle.getIndentOptions(myFixture.file).INDENT_SIZE
    CodeStyle.getIndentOptions(myFixture.file).INDENT_SIZE = indent
    val expandAction = myFixture.findSingleIntention(YAMLBundle.message("yaml.intention.name.expand.collection"))
    myFixture.launchAction(expandAction)
    myFixture.checkResult(testCaseAnswer)
    CodeStyle.getIndentOptions(myFixture.file).INDENT_SIZE = startIndent
  }

  private fun doExpandAllActinTestWithCustomIndent(indent: Int, testCase: String, testCaseAnswer: String) {
    myFixture.configureByText("test.yaml", testCase)
    val startIndent = CodeStyle.getIndentOptions(myFixture.file).INDENT_SIZE
    CodeStyle.getIndentOptions(myFixture.file).INDENT_SIZE = indent
    val expandAllAction = myFixture.findSingleIntention(YAMLBundle.message("yaml.intention.name.expand.all.collections.inside"))
    myFixture.launchAction(expandAllAction)
    try {
      myFixture.checkResult(testCaseAnswer)
    } finally {
      CodeStyle.getIndentOptions(myFixture.file).INDENT_SIZE = startIndent
    }
  }

  private fun doExpandAllActionTest(testCase: String, testCaseAnswer: String) {
    myFixture.configureByText("test.yaml", testCase)
    val expandAllAction = myFixture.findSingleIntention(YAMLBundle.message("yaml.intention.name.expand.all.collections.inside"))
    myFixture.launchAction(expandAllAction)
    myFixture.checkResult(testCaseAnswer)
  }

  override fun getTestDataPath(): String {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/formatting/inlineExpandConversation/data/"
  }

  // Simple INLINE tests
  fun testYamlInlineSimpleSequence_caretBeforeKey() = doInlineActionTest(
    """
      <caret>list:
        - elem1
        - elem2
        - elem3
    """.trimIndent(),
    """
      list: [ elem1, elem2, elem3 ]
    """.trimIndent()
  )

  fun testYamlInlineSimpleSequence_caretOnKey() = doInlineActionTest(
    """
      li<caret>st:
        - elem1
        - elem2
        - elem3
    """.trimIndent(),
    """
      list: [ elem1, elem2, elem3 ]
    """.trimIndent()
  )

  fun testYamlInlineSimpleSequence_caretBeforeColon() = doInlineActionTest(
    """
      list<caret>:
        - elem1
        - elem2
        - elem3
    """.trimIndent(),
    """
      list: [ elem1, elem2, elem3 ]
    """.trimIndent()
  )

  fun testYamlInlineSimpleSequence_caretAfterColon() = doInlineActionTest(
    """
      list:<caret>
        - elem1
        - elem2
        - elem3
    """.trimIndent(),
    """
      list: [ elem1, elem2, elem3 ]
    """.trimIndent()
  )

  fun testYamlInlineSimpleSequence_caretBeforeHyphen() = doInlineActionTest(
    """
      list:
        <caret>- elem1
        - elem2
        - elem3
    """.trimIndent(),
    """
      list: [ elem1, elem2, elem3 ]
    """.trimIndent()
  )

  fun testYamlInlineSimpleSequence_caretAfterHyphen() = doInlineActionTest(
    """
      list:
        -<caret> elem1
        - elem2
        - elem3
    """.trimIndent(),
    """
      list: [ elem1, elem2, elem3 ]
    """.trimIndent()
  )

  fun testYamlInlineSimpleSequence_caretAfterItem() = doInlineActionTest(
    """
      list:
        - elem1<caret>
        - elem2
        - elem3
    """.trimIndent(),
    """
      list: [ elem1, elem2, elem3 ]
    """.trimIndent()
  )

  fun testYamlInlineSimpleMapping_caretBeforeKey() = doInlineActionTest(
    """
      <caret>map:
          inner1: "abc"
          inner2: 1315
    """.trimIndent(),
    """
      map: { inner1: "abc", inner2: 1315 }
    """.trimIndent()
  )

  fun testYamlInlineSimpleMapping_caretOnKey() = doInlineActionTest(
    """
      ma<caret>p:
          inner1: "abc"
          inner2: 1315
    """.trimIndent(),
    """
      map: { inner1: "abc", inner2: 1315 }
    """.trimIndent()
  )

  fun testYamlInlineSimpleMapping_caretAfterKey() = doInlineActionTest(
    """
      map<caret>:
          inner1: "abc"
          inner2: 1315
    """.trimIndent(),
    """
      map: { inner1: "abc", inner2: 1315 }
    """.trimIndent()
  )

  fun testYamlInlineSimpleMapping_caretBeforeFirstItemKey() = doInlineActionTest(
    """
      map:
          <caret>inner1: "abc"
          inner2: 1315
    """.trimIndent(),
    """
      map: { inner1: "abc", inner2: 1315 }
    """.trimIndent()
  )

  fun testYamlInlineSimpleMapping_caretBeforeMediumItemKey() = doInlineActionTest(
    """
      map:
          inner1: "abc"
          <caret>inner2: 1315
    """.trimIndent(),
    """
      map: { inner1: "abc", inner2: 1315 }
    """.trimIndent()
  )

  fun testYamlInlineSimpleMapping_caretOnItemKey() = doInlineActionTest(
    """
      map:
          in<caret>ner1: "abc"
          inner2: 1315
    """.trimIndent(),
    """
      map: { inner1: "abc", inner2: 1315 }
    """.trimIndent()
  )

  fun testYamlInlineSimpleMapping_caretAfterItemKey() = doInlineActionTest(
    """
      map:
          inner1<caret>: "abc"
          inner2: 1315
    """.trimIndent(),
    """
      map: { inner1: "abc", inner2: 1315 }
    """.trimIndent()
  )

  fun testYamlInlineSimpleMapping_caretLastItemColon() = doInlineActionTest(
    """
      map:
          inner1: "abc"
          inner2:<caret> 1315
    """.trimIndent(),
    """
      map: { inner1: "abc", inner2: 1315 }
    """.trimIndent()
  )

  fun testYamlInlineSimpleMapping_caretAfterItemValue() = doInlineActionTest(
    """
      map:
          inner1: "abc"<caret>
          inner2: 1315
    """.trimIndent(),
    """
      map: { inner1: "abc", inner2: 1315 }
    """.trimIndent()
  )


  // Simple EXPAND tests
  fun testYamlExpandSimpleMapping_caretBeforeKey() = doExpandActionTest(
    """
      <caret>map: { inner1: "abc", inner2: 1315 }
      """.trimIndent(),
    """
      map:
        inner1: "abc"
        inner2: 1315
    """.trimIndent()
  )

  fun testYamlExpandSimpleMapping_caretOnKey() = doExpandActionTest(
    """
      ma<caret>p: { inner1: "abc", inner2: 1315 }
      """.trimIndent(),
    """
      map:
        inner1: "abc"
        inner2: 1315
    """.trimIndent()
  )

  fun testYamlExpandSimpleMapping_caretAfterKey() = doExpandActionTest(
    """
      map<caret>: { inner1: "abc", inner2: 1315 }
      """.trimIndent(),
    """
      map:
        inner1: "abc"
        inner2: 1315
    """.trimIndent()
  )

  fun testYamlExpandSimpleMapping_caretAfterColon() = doExpandActionTest(
    """
      map:<caret> { inner1: "abc", inner2: 1315 }
      """.trimIndent(),
    """
      map:
        inner1: "abc"
        inner2: 1315
    """.trimIndent()
  )

  fun testYamlExpandSimpleMapping_caretBeforeBracket() = doExpandActionTest(
    """
      map: <caret>{ inner1: "abc", inner2: 1315 }
      """.trimIndent(),
    """
      map:
        inner1: "abc"
        inner2: 1315
    """.trimIndent()
  )

  fun testYamlExpandSimpleMapping_caretAfterBracket() = doExpandActionTest(
    """
      map: {<caret> inner1: "abc", inner2: 1315 }
      """.trimIndent(),
    """
      map:
        inner1: "abc"
        inner2: 1315
    """.trimIndent()
  )

  fun testYamlExpandSimpleMapping_caretBeforeItemKey() = doExpandActionTest(
    """
      map: { <caret>inner1: "abc", inner2: 1315 }
      """.trimIndent(),
    """
      map:
        inner1: "abc"
        inner2: 1315
    """.trimIndent()
  )

  fun testYamlExpandSimpleMapping_caretOnItemKey() = doExpandActionTest(
    """
      map: { in<caret>ner1: "abc", inner2: 1315 }
      """.trimIndent(),
    """
      map:
        inner1: "abc"
        inner2: 1315
    """.trimIndent()
  )

  fun testYamlExpandSimpleMapping_caretAfterItemKey() = doExpandActionTest(
    """
      map: { inner1<caret>: "abc", inner2: 1315 }
      """.trimIndent(),
    """
      map:
        inner1: "abc"
        inner2: 1315
    """.trimIndent()
  )

  fun testYamlExpandSimpleMapping_caretAfterItemColon() = doExpandActionTest(
    """
      map: { inner1:<caret> "abc", inner2: 1315 }
      """.trimIndent(),
    """
      map:
        inner1: "abc"
        inner2: 1315
    """.trimIndent()
  )

  fun testYamlExpandSimpleMapping_caretBeforeComa() = doExpandActionTest(
    """
      map: { inner1: "abc"<caret>, inner2: 1315 }
      """.trimIndent(),
    """
      map:
        inner1: "abc"
        inner2: 1315
    """.trimIndent()
  )

  fun testYamlExpandSimpleMapping_caretAfterComa() = doExpandActionTest(
    """
      map: { inner1: "abc",<caret> inner2: 1315 }
      """.trimIndent(),
    """
      map:
        inner1: "abc"
        inner2: 1315
    """.trimIndent()
  )

  fun testYamlExpandSimpleSequence_caretBeforeKey() = doExpandActionTest(
    """
      <caret>list: [ elem1, elem2, elem3 ]
    """.trimIndent(),
    """
      list:
        - elem1
        - elem2
        - elem3
    """.trimIndent()
  )

  fun testYamlExpandSimpleSequence_caretOnKey() = doExpandActionTest(
    """
      li<caret>st: [ elem1, elem2, elem3 ]
    """.trimIndent(),
    """
      list:
        - elem1
        - elem2
        - elem3
    """.trimIndent()
  )

  fun testYamlExpandSimpleSequence_caretBeforeColon() = doExpandActionTest(
    """
      list<caret>: [ elem1, elem2, elem3 ]
    """.trimIndent(),
    """
      list:
        - elem1
        - elem2
        - elem3
    """.trimIndent()
  )

  fun testYamlExpandSimpleSequence_caretAfterColon() = doExpandActionTest(
    """
      list:<caret> [ elem1, elem2, elem3 ]
    """.trimIndent(),
    """
      list:
        - elem1
        - elem2
        - elem3
    """.trimIndent()
  )

  fun testYamlExpandSimpleSequence_caretBeforeOpenBracket() = doExpandActionTest(
    """
      list: <caret>[ elem1, elem2, elem3 ]
    """.trimIndent(),
    """
      list:
        - elem1
        - elem2
        - elem3
    """.trimIndent()
  )

  fun testYamlExpandSimpleSequence_caretAfterOpenBracket() = doExpandActionTest(
    """
      list: [<caret> elem1, elem2, elem3 ]
    """.trimIndent(),
    """
      list:
        - elem1
        - elem2
        - elem3
    """.trimIndent()
  )

  fun testYamlExpandSimpleSequence_caretBeforeCloseBracket() = doExpandActionTest(
    """
      list: [ elem1, elem2, elem3 <caret>]
    """.trimIndent(),
    """
      list:
        - elem1
        - elem2
        - elem3
    """.trimIndent()
  )

  fun testYamlExpandSimpleSequence_caretBeforeComma() = doExpandActionTest(
    """
      list: [ elem1<caret>, elem2, elem3 ]
    """.trimIndent(),
    """
      list:
        - elem1
        - elem2
        - elem3
    """.trimIndent()
  )

  fun testYamlExpandSimpleSequence_caretAfterComma() = doExpandActionTest(
    """
      list: [ elem1,<caret> elem2, elem3 ]
    """.trimIndent(),
    """
      list:
        - elem1
        - elem2
        - elem3
    """.trimIndent()
  )


  // Not simple INLINE tests
  fun testYamlInlineRecursive_2Levels() = doInlineActionTest(
    """
      myyaml<caret>:
        a: [ 1, 2, 3, 4, 5, 6 ]
        b: [ 7, 8, 9, 10, 11 ]
        c:
          name: { 16: 17 }
          age: { 18: 19 }
        f: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent(),
    """
      myyaml: { a: [ 1, 2, 3, 4, 5, 6 ], b: [ 7, 8, 9, 10, 11 ], c: { name: { 16: 17 }, age: { 18: 19 } }, f: { name: { 21: 17 }, age: { 123: 19 } } }
    """.trimIndent()
  )

  fun testYamlInlineRecursive_3Levels() = doInlineActionTest(
    """
      myyaml<caret>:
          a:
            - 1
            - 2
            - 3
            - 4
            - 5
            - 6
        b: [ 7, 8, 9, 10, 11 ]
        c:
          name: { 16: 17 }
          age: { 18: 19 }
        f:
          name: { 21: 17 }
          age:
            123: 19
    """.trimIndent(),
    """
      myyaml: { a: [ 1, 2, 3, 4, 5, 6 ], b: [ 7, 8, 9, 10, 11 ], c: { name: { 16: 17 }, age: { 18: 19 } }, f: { name: { 21: 17 }, age: { 123: 19 } } }
    """.trimIndent()
  )

  fun testYamlInlineRecursive_InlineInner2Layers() = doInlineActionTest(
    """
      myyaml:
        a:
          - 1
          - 2
          - 3
          - 4
          - 5
          - 6
        b: [ 7, 8, 9, 10, 11 ]
        c:
          name: { 16: 17 }
          age: { 18: 19 }
        f:<caret>
          name: { 21: 17 }
          age:
            123: 19
    """.trimIndent(),
    """
      myyaml:
        a:
          - 1
          - 2
          - 3
          - 4
          - 5
          - 6
        b: [ 7, 8, 9, 10, 11 ]
        c:
          name: { 16: 17 }
          age: { 18: 19 }
        f: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent()
  )


  // Not simple EXPAND tests
  fun `test YamlExpandRecursive - expand 2 levels of 3`() = doExpandActionTest(
    """
      myyaml: { a: [ 1, 2, 3, 4, 5, 6 ], b: [ 7, 8, 9, 10, 11 ], c<caret>: { name: { 16: 17 }, age: { 18: 19 } }, f: { name: { 21: 17 }, age: { 123: 19 } } }
    """.trimIndent(),
    """
      myyaml:
        a: [ 1, 2, 3, 4, 5, 6 ]
        b: [ 7, 8, 9, 10, 11 ]
        c:
          name: { 16: 17 }
          age: { 18: 19 }
        f: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent()
  )

  fun `test YamlExpandRecursive - expand 3 levels`() = doExpandActionTest(
    """
      myyaml: { a: [ 1, 2, 3, 4, 5, 6 ], b: [ 7, 8, 9, 10, 11 ], c: { name: { 16: 17 }, age: { 18: 19 } }, f: { name: { 21: 17 }, age: { 123<caret>: 19 } } }
    """.trimIndent(),
    """
      myyaml:
        a: [ 1, 2, 3, 4, 5, 6 ]
        b: [ 7, 8, 9, 10, 11 ]
        c: { name: { 16: 17 }, age: { 18: 19 } }
        f:
          name: { 21: 17 }
          age:
            123: 19
    """.trimIndent()
  )

  fun `test YamlExpandRecursive - expand 2 levels`() = doExpandActionTest(
    """
      myyaml: { a<caret>: [ 1, 2, 3, 4, 5, 6 ], b: [ 7, 8, 9, 10, 11 ], c: { name: { 16: 17 }, age: { 18: 19 } }, f: { name: { 21: 17 }, age: { 123: 19 } } }
    """.trimIndent(),
    """
      myyaml:
        a:
          - 1
          - 2
          - 3
          - 4
          - 5
          - 6
        b: [ 7, 8, 9, 10, 11 ]
        c: { name: { 16: 17 }, age: { 18: 19 } }
        f: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent()
  )

  fun `test YamlExpandRecursive - expand 1 level`() = doExpandActionTest(
    """
      myyaml: <caret>{ a: [ 1, 2, 3, 4, 5, 6 ], b: [ 7, 8, 9, 10, 11 ], c: { name: { 16: 17 }, age: { 18: 19 } }, f: { name: { 21: 17 }, age: { 123: 19 } } }
    """.trimIndent(),
    """
      myyaml:
        a: [ 1, 2, 3, 4, 5, 6 ]
        b: [ 7, 8, 9, 10, 11 ]
        c: { name: { 16: 17 }, age: { 18: 19 } }
        f: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent()
  )

  fun `test YamlExpandRecursive - inner sequence expand 1 layer`() = doExpandActionTest(
    """
      myyaml:
        a<caret>: [ 1, 2, 3, 4, 5, 6 ]
        b: [ 7, 8, 9, 10, 11 ]
        c: { name: { 16: 17 }, age: { 18: 19 } }
        f: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent(),
    """
      myyaml:
        a:
          - 1
          - 2
          - 3
          - 4
          - 5
          - 6
        b: [ 7, 8, 9, 10, 11 ]
        c: { name: { 16: 17 }, age: { 18: 19 } }
        f: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent()
  )

  fun `test YamlExpandRecursive - inner mapping expand 2 layers`() = doExpandActionTest(
    """
      myyaml:
        a<caret>: [ 1, 2, 3, 4, 5, 6 ]
        b: [ 7, 8, 9, 10, 11 ]
        c: { na<caret>me: { 16: 17 }, age: { 18: 19 } }
        f: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent(),
    """
      myyaml:
        a: [ 1, 2, 3, 4, 5, 6 ]
        b: [ 7, 8, 9, 10, 11 ]
        c:
          name:
            16: 17
          age: { 18: 19 }
        f: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent()
  )

  fun `test YamlExpandRecursive - inner mapping expand 1 layer`() = doExpandActionTest(
    """
      myyaml:
        a: [ 1, 2, 3, 4, 5, 6 ]
        b: [ 7, 8, 9, 10, 11 ]
        c<caret>: { name: { 16: 17 }, age: { 18: 19 } }
        f: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent(),
    """
      myyaml:
        a: [ 1, 2, 3, 4, 5, 6 ]
        b: [ 7, 8, 9, 10, 11 ]
        c:
          name: { 16: 17 }
          age: { 18: 19 }
        f: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent()
  )

  fun `test YamlExpandRecursive - inner mapping expand on 2nd layer`() = doExpandActionTest(
    """
      myyaml:
        a: [ 1, 2, 3, 4, 5, 6 ]
        b: [ 7, 8, 9, 10, 11 ]
        c:
          na<caret>me: { 16: 17, 23: 24 }
          age: { 18: 19 }
        f: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent(),
    """
      myyaml:
        a: [ 1, 2, 3, 4, 5, 6 ]
        b: [ 7, 8, 9, 10, 11 ]
        c:
          name:
            16: 17
            23: 24
          age: { 18: 19 }
        f: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent()
  )

  fun `test YamlExpandRecursive - inner sequence expand in lot of layers`() = doExpandActionTest(
    """
      a:
        b:
          c:
            d<caret>: [1, 2, 3]
            e: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent(),
    """
      a:
        b:
          c:
            d:
              - 1
              - 2
              - 3
            e: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent()
  )

  fun `test YamlExpandRecursive - inner mapping expand in lot of layers`() = doExpandActionTest(
    """
      a:
        b:
          c:
            d: [1, 2, 3]
            e<caret>: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent(),
    """
      a:
        b:
          c:
            d: [1, 2, 3]
            e:
              name: { 21: 17 }
              age: { 123: 19 }
    """.trimIndent()
  )

  fun `test YamlExpandRecursive - inner mapping key expand in lot of layers`() = doExpandActionTest(
    """
      a:
        b:
          c:
            d: [1, 2, 3]
            e: { na<caret>me: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent(),
    """
      a:
        b:
          c:
            d: [1, 2, 3]
            e:
              name:
                21: 17
              age: { 123: 19 }
    """.trimIndent()
  )

  // EXPAND ALL Tests
  fun `test expandAll on simple mapping - caret before key`() = doExpandAllActionTest(
    """
      <caret>yaml: { a: [ 1, 2, 3, 4, 5, 6 ], b: [ 7, 8, 9, 10, 11 ], c: { name: { 16: 17, 23: 24 }, age: { 18: 19 } }, f: { name: { 21: 17 }, age: { 123: 19 } } }
    """.trimMargin().trim(),
    """
      yaml:
        a:
          - 1
          - 2
          - 3
          - 4
          - 5
          - 6
        b:
          - 7
          - 8
          - 9
          - 10
          - 11
        c:
          name:
            16: 17
            23: 24
          age:
            18: 19
        f:
          name:
            21: 17
          age:
            123: 19
    """.trimIndent().trim()
  )

  fun `test expandAll on simple mapping - caret on key`() = doExpandAllActionTest(
    """
      ya<caret>ml: { a: [ 1, 2, 3, 4, 5, 6 ], b: [ 7, 8, 9, 10, 11 ], c: { name: { 16: 17, 23: 24 }, age: { 18: 19 } }, f: { name: { 21: 17 }, age: { 123: 19 } } }
    """.trimMargin().trim(),
    """
      yaml:
        a:
          - 1
          - 2
          - 3
          - 4
          - 5
          - 6
        b:
          - 7
          - 8
          - 9
          - 10
          - 11
        c:
          name:
            16: 17
            23: 24
          age:
            18: 19
        f:
          name:
            21: 17
          age:
            123: 19
    """.trimIndent().trim()
  )

  fun `test expandAll on simple mapping - caret after key`() = doExpandAllActionTest(
    """
      yaml<caret>: { a: [ 1, 2, 3, 4, 5, 6 ], b: [ 7, 8, 9, 10, 11 ], c: { name: { 16: 17, 23: 24 }, age: { 18: 19 } }, f: { name: { 21: 17 }, age: { 123: 19 } } }
    """.trimMargin().trim(),
    """
      yaml:
        a:
          - 1
          - 2
          - 3
          - 4
          - 5
          - 6
        b:
          - 7
          - 8
          - 9
          - 10
          - 11
        c:
          name:
            16: 17
            23: 24
          age:
            18: 19
        f:
          name:
            21: 17
          age:
            123: 19
    """.trimIndent().trim()
  )

  fun `test expandAll on simple mapping - caret after colon`() = doExpandAllActionTest(
    """
      yaml:<caret> { a: [ 1, 2, 3, 4, 5, 6 ], b: [ 7, 8, 9, 10, 11 ], c: { name: { 16: 17, 23: 24 }, age: { 18: 19 } }, f: { name: { 21: 17 }, age: { 123: 19 } } }
    """.trimMargin().trim(),
    """
      yaml:
        a:
          - 1
          - 2
          - 3
          - 4
          - 5
          - 6
        b:
          - 7
          - 8
          - 9
          - 10
          - 11
        c:
          name:
            16: 17
            23: 24
          age:
            18: 19
        f:
          name:
            21: 17
          age:
            123: 19
    """.trimIndent().trim()
  )

  fun `test expandAll on simple mapping - caret before bracket`() = doExpandAllActionTest(
    """
      yaml: <caret>{ a: [ 1, 2, 3, 4, 5, 6 ], b: [ 7, 8, 9, 10, 11 ], c: { name: { 16: 17, 23: 24 }, age: { 18: 19 } }, f: { name: { 21: 17 }, age: { 123: 19 } } }
    """.trimMargin().trim(),
    """
      yaml:
        a:
          - 1
          - 2
          - 3
          - 4
          - 5
          - 6
        b:
          - 7
          - 8
          - 9
          - 10
          - 11
        c:
          name:
            16: 17
            23: 24
          age:
            18: 19
        f:
          name:
            21: 17
          age:
            123: 19
    """.trimIndent().trim()
  )

  fun `test expandAll on simple mapping - caret after bracket`() = doExpandAllActionTest(
    """
      yaml: {<caret> a: [ 1, 2, 3, 4, 5, 6 ], b: [ 7, 8, 9, 10, 11 ], c: { name: { 16: 17, 23: 24 }, age: { 18: 19 } }, f: { name: { 21: 17 }, age: { 123: 19 } } }
    """.trimMargin().trim(),
    """
      yaml:
        a:
          - 1
          - 2
          - 3
          - 4
          - 5
          - 6
        b:
          - 7
          - 8
          - 9
          - 10
          - 11
        c:
          name:
            16: 17
            23: 24
          age:
            18: 19
        f:
          name:
            21: 17
          age:
            123: 19
    """.trimIndent().trim()
  )

  fun `test expandAll on simple mapping - caret before close bracket`() = doExpandAllActionTest(
    """
      yaml: { a: [ 1, 2, 3, 4, 5, 6 ], b: [ 7, 8, 9, 10, 11 ], c: { name: { 16: 17, 23: 24 }, age: { 18: 19 } }, f: { name: { 21: 17 }, age: { 123: 19 } } }
    """.trimMargin().trim(),
    """
      yaml:
        a:
          - 1
          - 2
          - 3
          - 4
          - 5
          - 6
        b:
          - 7
          - 8
          - 9
          - 10
          - 11
        c:
          name:
            16: 17
            23: 24
          age:
            18: 19
        f:
          name:
            21: 17
          age:
            123: 19
    """.trimIndent().trim()
  )

  fun `test expandAll not so simple collection`() = doExpandAllActionTest(
    """
      yaml<caret>: { a: [ 1, 2, { name: { 16: 17 }, 23: 24 }, { age: { 18: 19 } }, 4, 5, 6 ], b: [ 7, 8, 9, 10, 11 ], c: { name: { 16: 17, 23: 24 }, age: { 18: 19 } }, f: { name: { 21: 17 }, age: { 123: 19 } } }
    """.trimMargin().trim(),
    """
      yaml:
        a:
          - 1
          - 2
          - name:
              16: 17
            23: 24
          - age:
              18: 19
          - 4
          - 5
          - 6
        b:
          - 7
          - 8
          - 9
          - 10
          - 11
        c:
          name:
            16: 17
            23: 24
          age:
            18: 19
        f:
          name:
            21: 17
          age:
            123: 19
    """.trimIndent().trim()
  )

  fun `test expandAll inner collection`() = doExpandAllActionTest(
    """
      yaml:
        a<caret>: [ 1, 2, { name: { 16: 17 }, 23: 24 }, { age: { 18: 19 } }, 4, 5, 6 ]
        b: [ 7, 8, 9, 10, 11 ]
        c: { name: { 16: 17, 23: 24 }, age: { 18: 19 } }
        f: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent(),
    """
      yaml:
        a:
          - 1
          - 2
          - name:
              16: 17
            23: 24
          - age:
              18: 19
          - 4
          - 5
          - 6
        b: [ 7, 8, 9, 10, 11 ]
        c: { name: { 16: 17, 23: 24 }, age: { 18: 19 } }
        f: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent()
  )

  // Custom indent tests
  // FIXME: Is it possible to add beforeEach and afterEach cals of fixing indent?
  //  Check doExpandActinTestWithCustomIndent, please

  fun `test YamlExpandRecursive - inner mapping expand - a lot of layers - with indent 4`() {
    doExpandActinTestWithCustomIndent(4,
                                      """
    #language=YAML
    a:
        b:
            c:
                d: [1, 2, 3]
                e<caret>: { name: { 21: 17 }, age: { 123: 19 } }
  """.trimIndent(),
                                      """
    #language=YAML
    a:
        b:
            c:
                d: [1, 2, 3]
                e:
                    name: { 21: 17 }
                    age: { 123: 19 }
  """.trimIndent()
    )
  }

  fun `test YamlExpandRecursive - inner mapping key expand in lot of layers - indent 4`() = doExpandActinTestWithCustomIndent(4,
                                                                                                                              """
    #language=YAML
    a:
        b:
            c:
                d: [1, 2, 3]
                e: { na<caret>me: { 21: 17 }, age: { 123: 19 } }
  """.trimIndent(),
                                                                                                                              """
    #language=YAML
    a:
        b:
            c:
                d: [1, 2, 3]
                e:
                    name:
                        21: 17
                    age: { 123: 19 }
  """.trimIndent()
  )

  fun `test YamlExpandRecursive - inner sequence expand in lot of layers - indent 4`() = doExpandActinTestWithCustomIndent(4,
                                                                                                                           """
      a:
          b:
              c:
                  d<caret>: [1, 2, 3]
                  e: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent(),
                                                                                                                           """
      a:
          b:
              c:
                  d:
                      - 1
                      - 2
                      - 3
                  e: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent()
  )

  fun `test YamlExpandRecursive - inner mapping expand in lot of layers - indent 6`() = doExpandActinTestWithCustomIndent(6,
                                                                                                                          """
    #language=YAML
    a:
          b:
                c:
                      d: [1, 2, 3]
                      e<caret>: { name: { 21: 17 }, age: { 123: 19 } }
  """.trimIndent(),
                                                                                                                          """
    #language=YAML
    a:
          b:
                c:
                      d: [1, 2, 3]
                      e:
                            name: { 21: 17 }
                            age: { 123: 19 }
  """.trimIndent()
  )

  fun `test YamlExpandRecursive - inner mapping key expand in lot of layers - indent 6`() = doExpandActinTestWithCustomIndent(6,
                                                                                                                              """
    #language=YAML
    a:
          b:
                c:
                      d: [1, 2, 3]
                      e: { na<caret>me: { 21: 17 }, age: { 123: 19 } }
  """.trimIndent(),
                                                                                                                              """
    #language=YAML
    a:
          b:
                c:
                      d: [1, 2, 3]
                      e:
                            name:
                                  21: 17
                            age: { 123: 19 }
  """.trimIndent()
  )

  fun `test YamlExpandRecursive - inner sequence expand in lot of layers - indent 6`() = doExpandActinTestWithCustomIndent(6,
                                                                                                                           """
      a:
            b:
                  c:
                        d<caret>: [1, 2, 3]
                        e: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent(),
                                                                                                                           """
      a:
            b:
                  c:
                        d:
                              - 1
                              - 2
                              - 3
                        e: { name: { 21: 17 }, age: { 123: 19 } }
    """.trimIndent()
  )

  // Expand all with custom indent
  fun `test expandAll on simple mapping - indent 4`() = doExpandAllActinTestWithCustomIndent(4,
                                                                                             """
      ya<caret>ml: { a: [ 1, 2, 3, 4, 5, 6 ], b: [ 7, 8, 9, 10, 11 ], c: { name: { 16: 17, 23: 24 }, age: { 18: 19 } }, f: { name: { 21: 17 }, age: { 123: 19 } } }
    """.trimIndent(),
                                                                                             """
      yaml:
          a:
              - 1
              - 2
              - 3
              - 4
              - 5
              - 6
          b:
              - 7
              - 8
              - 9
              - 10
              - 11
          c:
              name:
                  16: 17
                  23: 24
              age:
                  18: 19
          f:
              name:
                  21: 17
              age:
                  123: 19
    """.trimIndent()
  )

  fun `test expandAll on simple mapping - indent 6`() = doExpandAllActinTestWithCustomIndent(6,
                                                                                             """
      ya<caret>ml: { a: [ 1, 2, 3, 4, 5, 6 ], b: [ 7, 8, 9, 10, 11 ], c: { name: { 16: 17, 23: 24 }, age: { 18: 19 } }, f: { name: { 21: 17 }, age: { 123: 19 } } }
      """.trimIndent(),
                                                                                             """
      yaml:
            a:
                  - 1
                  - 2
                  - 3
                  - 4
                  - 5
                  - 6
            b:
                  - 7
                  - 8
                  - 9
                  - 10
                  - 11
            c:
                  name:
                        16: 17
                        23: 24
                  age:
                        18: 19
            f:
                  name:
                        21: 17
                  age:
                        123: 19
    """.trimIndent()
  )
}
