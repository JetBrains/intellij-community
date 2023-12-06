/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.annotator

class TomlAnnotatorTest : TomlAnnotatorTestBase(TomlAnnotator::class) {

    fun `test inline tables`() = checkByText("""
        a = {something = "", another = ""}
        a = <error>{something = "",
            another = ""}</error>
        a = {something = [
                "foo", "bar"
            ]}
    """)

    fun `test trailing comma in inline table`() = checkByText("""
        foo = { bar = "", baz = ""<error>,</error> }
        foo = [ "bar", "baz", ]
    """)

    fun `test trailing comma in inline table fix`() = checkFixByText("Remove trailing comma", """
        foo = { bar = "", baz = ""<error>,/*caret*/</error> }
    """, """
        foo = { bar = "", baz = "" }
    """)
}
