/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.psi

import com.intellij.psi.tree.IElementType
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.toml.lang.psi.TomlElementTypes.*
import org.toml.lang.psi.ext.offsetsForTomlText

@RunWith(Parameterized::class)
class TomlLiteralOffsetsTest(type: IElementType, text: String) :
    LiteralOffsetsTestBase(type, text, ::offsetsForTomlText) {

    @Test
    fun test() = doTest()

    companion object {
        @Parameterized.Parameters(name = "{index}: {1}")
        @JvmStatic
        fun data(): Collection<Array<Any>> = listOf(
            arrayOf(BASIC_STRING, """|"|foo|"|"""),
            arrayOf(BASIC_STRING, """|"|\nfo\u0020o|"|"""),
            arrayOf(BASIC_STRING, """|"|\"|"|"""),
            arrayOf(BASIC_STRING, """|"|bar||"""),

            arrayOf(LITERAL_STRING, """|'|foo|'|"""),
            arrayOf(LITERAL_STRING, """|'|\nfo\u0020o|'|"""),
            arrayOf(LITERAL_STRING, """|'|"|'|"""),
            arrayOf(LITERAL_STRING, """|'|bar||"""),

            arrayOf(MULTILINE_BASIC_STRING, "|\"\"\"|foo|\"\"\"|"),
            arrayOf(MULTILINE_BASIC_STRING, "|\"\"\"|\\nfo\\u0020\n|\"\"\"|"),
            arrayOf(MULTILINE_BASIC_STRING, "|\"\"\"|\\\"|\"\"\"|"),
            arrayOf(MULTILINE_BASIC_STRING, "|\"\"\"|bar||"),
            arrayOf(MULTILINE_BASIC_STRING, "|\"\"\"|bar\"\"||"),

            arrayOf(MULTILINE_LITERAL_STRING, """|'''|foo|'''|"""),
            arrayOf(MULTILINE_LITERAL_STRING, """|'''|\n
                fo\u0020o|'''|""".trimIndent()
            ),
            arrayOf(MULTILINE_LITERAL_STRING, """|'''|don't|'''|"""),
            arrayOf(MULTILINE_LITERAL_STRING, """|'''|bar||"""),
            arrayOf(MULTILINE_LITERAL_STRING, """|'''|bar''||""")
        )
    }
}
