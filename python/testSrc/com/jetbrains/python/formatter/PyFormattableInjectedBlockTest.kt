// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.formatting.Block
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.Indent
import com.intellij.idea.TestFor
import com.intellij.lang.LanguageFormatting
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

@Subsystems.Formatter
@Layers.Functional
@TestFor(classes = [PyFormattableInjectedBlock::class])
class PyFormattableInjectedBlockTest : PyCodeInsightTestCase() {
  @Test
  @TestFor(issues = ["PY-92910"])
  fun `child attributes after the opening quotes of a formatted injected fragment`() = runInEdtAndWait {
    myFixture.configureByText("a.py", """
      # language=JSON
      data = '''{"a": 1}'''
    """.trimIndent())
    val file = myFixture.file
    val settings = CodeStyle.getSettings(file)
    val model = LanguageFormatting.INSTANCE.forContext(file)!!.createModel(FormattingContext.create(file, settings))
    val injectedBlock = findInjectedBlock(model.rootBlock) ?: fail("The formatting model has no block for the injected JSON")

    val attributes = injectedBlock.getChildAttributes(1)

    assertEquals(Indent.getNoneIndent().toString(), attributes.childIndent.toString())
    assertNull(attributes.alignment)
  }

  private fun findInjectedBlock(block: Block): PyFormattableInjectedBlock? =
    block as? PyFormattableInjectedBlock ?: block.subBlocks.firstNotNullOfOrNull { findInjectedBlock(it) }
}
