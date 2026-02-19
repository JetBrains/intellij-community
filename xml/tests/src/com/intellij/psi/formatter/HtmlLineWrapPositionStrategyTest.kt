// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter

import com.intellij.openapi.editor.AbstractLineWrapPositionStrategyTest
import com.intellij.openapi.editor.LineWrapPositionStrategy
import org.junit.Before
import org.junit.Test

class HtmlLineWrapPositionStrategyTest : AbstractLineWrapPositionStrategyTest() {

  private lateinit var myStrategy: LineWrapPositionStrategy

  @Before
  override fun prepare() {
    super.prepare()
    myStrategy = MarkupLineWrapPositionStrategy()
  }

  @Test
  fun doNotWrapWithinClosingTagStart1() {
    doTest(myStrategy,
           "<a href=\"https://e3bfa38b.sibforms.com/served/MUIFAHGA2fUufYYM4ynBfdgdfgdsfffffffffffffff5Bc\"><WRAP></a<EDGE>>")
  }

  @Test
  fun doNotWrapWithinClosingTagStart2() {
    doTest(myStrategy,
           "<a href=\"https://e3bfa38b.sibforms.com/served/MUIFAHGA2fUufYYM4ynBfdgdfgdsfffffffffffffff5Bc\"><WRAP></<EDGE>a>")
  }

  @Test
  fun doNotWrapWithinClosingTagStart3() {
    doTest(myStrategy,
           "<a href=\"https://e3bfa38b.sibforms.com/served/MUIFAHGA2fUufYYM4ynBfdgdfgdsfffffffffffffff5Bc\"><WRAP><<EDGE>/a>")
  }

  @Test
  fun doNotWrapWithinClosingTagStart4() {
    doTest(myStrategy,
           "<a href=\"https://e3bfa38b.sibforms.com/served/MUIFAHGA2fUufYYM4ynBfdgdfgdsfffffffffffffff5Bc\"><WRAP><EDGE></a>")
  }

  @Test
  fun wrapAfterSlash() {
    val document =
      "<a href=\"https://e3bfa38b.sibforms.com/served/<WRAP>MUIFAHGA2fUufYYM4ynBfdgdfgdsfffffffffffffff<EDGE>5Bc\"></a>"
    doTest(myStrategy, document)
  }
}