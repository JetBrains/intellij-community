// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.editing

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class YAMLQuotesTypingTest : BasePlatformTestCase(){
  
  fun testQuoteCompletionInValue(){
    myFixture.configureByText("test.yaml", """
        myyaml: 
          root: <caret>
          
    """.trimIndent())
    myFixture.type("\"")
    myFixture.checkResult("""
        myyaml: 
          root: "<caret>"
          
    """.trimIndent())
  }

  fun testQuoteBackspace(){
    myFixture.configureByText("test.yaml", """
        myyaml: 
          root: "<caret>"
          
    """.trimIndent())
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
    myFixture.checkResult("""
        myyaml: 
          root: <caret>
          
    """.trimIndent())
  }

  fun testQuoteBeforeCaret(){
    myFixture.configureByText("test.yaml", """
        myyaml: 
          root: <caret>"
          
    """.trimIndent())
    myFixture.type("\"")
    myFixture.checkResult("""
        myyaml: 
          root: "<caret>"
          
    """.trimIndent())
  }


  fun testSingleQuoteCompletionInValue(){
    myFixture.configureByText("test.yaml", """
        myyaml: 
          root: <caret>
          
    """.trimIndent())
    myFixture.type("'")
    myFixture.checkResult("""
        myyaml: 
          root: ''
          
    """.trimIndent())
  }
  
  
  fun testQuoteInMultiline(){
    myFixture.configureByText("test.yaml", """
        myyaml: |
          firstline
          <caret>
          
    """.trimIndent())
    myFixture.type("\"")
    myFixture.checkResult("""
        myyaml: |
          firstline
          "<caret>
          
    """.trimIndent())
  }
  
  fun testOverTypeClosingQuoteInEmptyValue(){
    myFixture.configureByText("test.yaml", """
        myyaml:
          root: "<caret>"

    """.trimIndent())
    myFixture.type("\"")
    myFixture.checkResult("""
        myyaml:
          root: ""<caret>

    """.trimIndent())
  }

  fun testOverTypeClosingSingleQuoteInEmptyValue(){
    myFixture.configureByText("test.yaml", """
        myyaml:
          root: '<caret>'

    """.trimIndent())
    myFixture.type("'")
    myFixture.checkResult("""
        myyaml:
          root: ''<caret>

    """.trimIndent())
  }

  fun testOverTypeClosingQuoteInValue(){
    myFixture.configureByText("test.yaml", """
        myyaml:
          root: "value<caret>"

    """.trimIndent())
    myFixture.type("\"")
    myFixture.checkResult("""
        myyaml:
          root: "value"<caret>

    """.trimIndent())
  }

  fun testOverTypeClosingSingleQuoteInValue(){
    myFixture.configureByText("test.yaml", """
        myyaml:
          root: 'value<caret>'

    """.trimIndent())
    myFixture.type("'")
    myFixture.checkResult("""
        myyaml:
          root: 'value'<caret>

    """.trimIndent())
  }

  fun testLoneDoubleQuoteIsNotOverTyped(){
    myFixture.configureByText("test.yaml", """
        myyaml:
          root: <caret>"

    """.trimIndent())
    myFixture.type("\"")
    myFixture.checkResult("""
        myyaml:
          root: "<caret>"

    """.trimIndent())
  }

  fun testLoneSingleQuoteIsNotOverTyped(){
    myFixture.configureByText("test.yaml", """
        myyaml:
          root: <caret>'

    """.trimIndent())
    myFixture.type("'")
    myFixture.checkResult("""
        myyaml:
          root: '<caret>'

    """.trimIndent())
  }

  fun testOverTypeClosingQuoteInEmptyKey(){
    myFixture.configureByText("test.yaml", """
        myyaml:
          "<caret>": value

    """.trimIndent())
    myFixture.type("\"")
    myFixture.checkResult("""
        myyaml:
          ""<caret>: value

    """.trimIndent())
  }

  fun testOverTypeClosingSingleQuoteInEmptyKey(){
    myFixture.configureByText("test.yaml", """
        myyaml:
          '<caret>': value

    """.trimIndent())
    myFixture.type("'")
    myFixture.checkResult("""
        myyaml:
          ''<caret>: value

    """.trimIndent())
  }

  fun testOverTypeClosingQuoteInKey(){
    myFixture.configureByText("test.yaml", """
        myyaml:
          "my key<caret>": value

    """.trimIndent())
    myFixture.type("\"")
    myFixture.checkResult("""
        myyaml:
          "my key"<caret>: value

    """.trimIndent())
  }

  fun testOverTypeClosingSingleQuoteInKey(){
    myFixture.configureByText("test.yaml", """
        myyaml:
          'my key<caret>': value

    """.trimIndent())
    myFixture.type("'")
    myFixture.checkResult("""
        myyaml:
          'my key'<caret>: value

    """.trimIndent())
  }

  fun testQuoteBeforeQuotes(){
    myFixture.configureByText("test.yaml", """
      bug:
        yesno:
          yes: <caret>
          no: "nope"
          undefined:
          
    """.trimIndent())
    myFixture.type("\"")
    myFixture.checkResult("""
      bug:
        yesno:
          yes: "<caret>"
          no: "nope"
          undefined:
          
    """.trimIndent())
  }
  fun testQuoteBeforeQuotedKey(){
    myFixture.configureByText("test.yaml", """
      foo:
        bar: <caret>
        boo: 
        'aaa bbb': aaa
          
    """.trimIndent())
    myFixture.type("'")
    myFixture.checkResult("""
      foo:
        bar: ''
        boo: 
        'aaa bbb': aaa
          
    """.trimIndent())
  }
  
  fun testQuoteCompletionInKey(){
    myFixture.configureByText("test.yaml", """
        myyaml: 
          <caret>
          
    """.trimIndent())
    myFixture.type("\"")
    myFixture.checkResult("""
        myyaml: 
          "<caret>"
          
    """.trimIndent())
  }



}