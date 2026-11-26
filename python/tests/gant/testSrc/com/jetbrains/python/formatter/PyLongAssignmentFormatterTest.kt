// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.idea.TestFor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * The expected texts are what Black 26 and Ruff 0.17 write, with these differences of the Black-like profile.
 * The profile chops down a long argument list and a long subscription one item per line, also when the items
 * fit on one line between the brackets. The long-line wrap of the profile splits a string that is still too long.
 * Each test also checks that a second reformat changes nothing.
 */
@Subsystems.Formatter
@Layers.Functional
@TestFor(classes = [PyLongAssignedValuePreFormatProcessor::class, PyBlock::class], issues = ["PY-94480"])
class PyLongAssignmentFormatterTest : PyCodeInsightTestCase() {

  @Test
  fun `long value assigned to a subscription goes in parentheses`() = assertReformatted("""
    some_dictionary["some_key"] = some_long_expression_causing_long_line_some_long_expression
  """, """
    some_dictionary["some_key"] = (
        some_long_expression_causing_long_line_some_long_expression
    )
  """)

  @Test
  fun `long value of each kind of assignment goes in parentheses`() = assertReformatted("""
    value = some_long_expression_causing_long_line_some_long_expression + other_long_expression
    value += some_long_expression_causing_long_line_some_long_expression + other_long_expression
    value: int = some_long_expression_causing_long_line_some_long_expression + other_long_expr
    value_name_long = some_object.some_attribute_name.other_attribute_name.third_attribute_name
  """, """
    value = (
        some_long_expression_causing_long_line_some_long_expression + other_long_expression
    )
    value += (
        some_long_expression_causing_long_line_some_long_expression + other_long_expression
    )
    value: int = (
        some_long_expression_causing_long_line_some_long_expression + other_long_expr
    )
    value_name_long = (
        some_object.some_attribute_name.other_attribute_name.third_attribute_name
    )
  """)

  @Test
  fun `long tuple value goes in parentheses one item per line`() = assertReformatted("""
    value_name = first_element_of_the_tuple_expression, second_element_of_the_tuple_expression
  """, """
    value_name = (
        first_element_of_the_tuple_expression,
        second_element_of_the_tuple_expression,
    )
  """)

  @Test
  fun `value in parentheses gets a line break after the opening parenthesis`() = assertReformatted("""
    value = (some_long_expression_causing_long_line_some_long_expression + other_long_expression)
  """, """
    value = (
        some_long_expression_causing_long_line_some_long_expression + other_long_expression
    )
  """)

  @Test
  fun `value that ends with a bracket splits inside its brackets`() = assertReformatted("""
    value = some_function_name(argument_number_one, argument_number_two, argument_number_three)
  """, """
    value = some_function_name(
        argument_number_one,
        argument_number_two,
        argument_number_three,
    )
  """)

  @Test
  fun `value that fits stays on the line of the targets`() = assertUnchanged("""
    value = some_long_expression + other_long_expression
  """)

  @Test
  fun `value stays when the targets do not fit on one line with the parenthesis`() = assertReformatted("""
    some_dictionary["some_key_aaaaaaaaaaaaaaaaaaaaaaaaa", "some_key_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"] = x
  """, """
    some_dictionary[
        "some_key_aaaaaaaaaaaaaaaaaaaaaaaaa",
        "some_key_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
    ] = x
  """)

  @Test
  fun `long subscription puts one item per line`() = assertReformatted("""
    some_dictionary["some_key", "some_key", "some_key", "some_key", "some_key", "some_key", "some_key"] = 1
    x = some_dictionary["some_key_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "some_key_bbbbbbbbbbbbbbbbbbbbbbbbbbbbb"]
  """, """
    some_dictionary[
        "some_key",
        "some_key",
        "some_key",
        "some_key",
        "some_key",
        "some_key",
        "some_key",
    ] = 1
    x = some_dictionary[
        "some_key_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "some_key_bbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
    ]
  """)

  @Test
  fun `long subscription with one item puts the item on its own line`() = assertReformatted("""
    x = some_dictionary["some_key_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"]
  """, """
    x = some_dictionary[
        "some_key_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    ]
  """)

  @Test
  fun `long tuple value with one item keeps its comma`() = assertReformatted("""
    some_variable_name_here = some_function_name_that_is_long(arguments_that_are_long, xxxx),
  """, """
    some_variable_name_here = (
        some_function_name_that_is_long(arguments_that_are_long, xxxx),
    )
  """)

  @Test
  fun `value goes in parentheses when the targets have brackets, also if it does not fit`() = assertReformatted("""
    some_dictionary["k"] = "a string that does not fit on its own line even when it is put in parentheses at all"
  """, """
    some_dictionary["k"] = (
        "a string that does not fit on its own line even when it is put in parentheses at "
        "all"
    )
  """)

  @Test
  fun `value that ends with empty brackets or has two operators goes in parentheses`() = assertReformatted("""
    value_name = some_object.some_attribute_name.other_attribute_name.third_attribute_name_xx()
    value = first_long_operand_name + second_long_operand_name + some_function(argument_one_xx)
  """, """
    value_name = (
        some_object.some_attribute_name.other_attribute_name.third_attribute_name_xx()
    )
    value = (
        first_long_operand_name + second_long_operand_name + some_function(argument_one_xx)
    )
  """)

  @Test
  fun `long value in a function body goes in parentheses at the body indent`() = assertReformatted("""
    def f():
        value = some_long_expression_causing_long_line_some_long_expression + other_long_expr
  """, """
    def f():
        value = (
            some_long_expression_causing_long_line_some_long_expression + other_long_expr
        )
  """)

  @Test
  fun `value in parentheses stays on its own line without kept line breaks`() {
    val text = """
      value = (
          some_long_expression_causing_long_line_some_long_expression + other_long_expression
      )
    """.trimIndent()
    myFixture.configureByText("a.py", text)
    val settings = CodeStyle.createTestSettings()
    PyDefaultStyleGuide.apply(settings)
    settings.getCommonSettings(PythonLanguage.getInstance()).KEEP_LINE_BREAKS = false
    CodeStyle.doWithTemporarySettings(myFixture.project, settings, Runnable {
      WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable {
        CodeStyleManager.getInstance(myFixture.project).reformat(myFixture.file)
      })
    })
    assertEquals(text, myFixture.file.text.trimEnd())
  }

  @Test
  fun `subscription in an f-string fragment and a comment after the bracket stay as written`() = assertUnchanged("""
    x = f"{d[ 'a' ]=}"
    y = some_dictionary[  # comment
        "some_key"
    ]
  """)

  @Test
  fun `short subscription stays on one line`() = assertUnchanged("""
    x = some_dictionary["some_key", "other_key"][0]
    y = call(argument_number_one, argument_number_two)["some_key"]
  """)

  @Test
  fun `option off leaves the long value to the line wrap`() {
    // The line wrap inserts a backslash or hanging parentheses, depending on the Enter settings.
    reformat("""
      value_name_long = some_object.some_attribute_name.other_attribute_name.third_attribute_name
    """) { it.WRAP_LONG_ASSIGNED_VALUE_IN_PARENTHESES = false }
    assertFalse(myFixture.file.text.startsWith("value_name_long = (\n"), myFixture.file.text)
  }

  private fun assertUnchanged(text: String, configure: (PyCodeStyleSettings) -> Unit = {}) =
    assertReformatted(text, text, configure)

  private fun assertReformatted(before: String, expected: String, configure: (PyCodeStyleSettings) -> Unit = {}) {
    reformat(before, configure)
    assertEquals(expected.trimIndent(), myFixture.file.text.trimEnd())
    reformat(myFixture.file.text, configure)
    assertEquals(expected.trimIndent(), myFixture.file.text.trimEnd(), "A second reformat changed the text")
  }

  private fun reformat(before: String, configure: (PyCodeStyleSettings) -> Unit) {
    myFixture.configureByText("a.py", before.trimIndent())
    val settings = CodeStyle.createTestSettings()
    PyDefaultStyleGuide.apply(settings)
    configure(settings.getCustomSettings(PyCodeStyleSettings::class.java))
    CodeStyle.doWithTemporarySettings(myFixture.project, settings, Runnable {
      WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable {
        CodeStyleManager.getInstance(myFixture.project).reformat(myFixture.file)
      })
    })
  }
}
