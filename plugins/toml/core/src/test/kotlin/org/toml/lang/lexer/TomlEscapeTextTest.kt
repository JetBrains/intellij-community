/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.lexer

import com.intellij.psi.tree.IElementType
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.toml.lang.psi.TomlElementTypes.*

@RunWith(Parameterized::class)
class TomlEscapeTextTest(
    private val tokenType: IElementType,
    private val str: String,
    private val decoded: String
) {

    @Test
    fun test() {
        val decodedActual = str.unescapeToml(tokenType)
        Assert.assertEquals(decoded, decodedActual)
    }

    companion object {
        @Parameterized.Parameters(name = "{index}: (\"{0}\", \"{1}\") → \"{2}\"")
        @JvmStatic fun data(): Collection<Array<Any>> = listOf(
            arrayOf(BASIC_STRING, "aaa", "aaa"),
            arrayOf(BASIC_STRING, "a\\na", "a\na"),
            arrayOf(BASIC_STRING, "a\\ra", "a\ra"),
            arrayOf(BASIC_STRING, "a\\ta", "a\ta"),
            arrayOf(BASIC_STRING, "a\\ba", "a\ba"),
            arrayOf(BASIC_STRING, "a\\fa", "a\u000Ca"),
            arrayOf(BASIC_STRING, "a\\\"a", "a\"a"),
            arrayOf(BASIC_STRING, "a\\\\a", "a\\a"),
            arrayOf(BASIC_STRING, "\\u0119dw\\u0105rd", "\u0119dw\u0105rd"),
            arrayOf(BASIC_STRING, "\\u", "\\u"),
            arrayOf(BASIC_STRING, "\\u119dw\\u105rd", "\\u119dw\\u105rd"),
            arrayOf(BASIC_STRING, "\\uzzzz", "\\uzzzz"),
            arrayOf(BASIC_STRING, "\\U00000119dw\\U00000105rd", "\u0119dw\u0105rd"),
            arrayOf(BASIC_STRING, "\\U", "\\U"),
            arrayOf(BASIC_STRING, "\\U0119dw\\U0105rd", "\\U0119dw\\U0105rd"),
            arrayOf(BASIC_STRING, "\\U0020FFFF", "\\U0020FFFF"),
            arrayOf(BASIC_STRING, "foo\\\n    bar", "foo\\\n    bar"),
            arrayOf(BASIC_STRING, "foo\\\r\n    bar", "foo\\\r\n    bar"),
            arrayOf(BASIC_STRING, "\nfoo\\\n    bar", "\nfoo\\\n    bar"),
            arrayOf(BASIC_STRING, "\nfoo\\\r\n    bar", "\nfoo\\\r\n    bar"),
            arrayOf(BASIC_STRING, "\n\nfoo\\\n    bar", "\n\nfoo\\\n    bar"),
            arrayOf(BASIC_STRING, "\n\nfoo\\\r\n    bar", "\n\nfoo\\\r\n    bar"),

            arrayOf(MULTILINE_BASIC_STRING, "foo\\\n    bar", "foobar"),
            arrayOf(MULTILINE_BASIC_STRING, "foo\\\r\n    bar", "foobar"),
            arrayOf(MULTILINE_BASIC_STRING, "foo\\r\\nbar", "foo\r\nbar"),
            arrayOf(MULTILINE_BASIC_STRING, "\nfoo\\\n    bar", "foobar"),
            arrayOf(MULTILINE_BASIC_STRING, "\nfoo\\\r\n    bar", "foobar"),
            arrayOf(MULTILINE_BASIC_STRING, "\nfoo\\r\\nbar", "foo\r\nbar"),
            arrayOf(MULTILINE_BASIC_STRING, "\n\nfoo\\\n    bar", "\nfoobar"),
            arrayOf(MULTILINE_BASIC_STRING, "\n\nfoo\\\r\n    bar", "\nfoobar"),
            arrayOf(MULTILINE_BASIC_STRING, "\n\nfoo\\r\\nbar", "\nfoo\r\nbar"),

            arrayOf(LITERAL_STRING, "aaa", "aaa"),
            arrayOf(LITERAL_STRING, "a\\na", "a\\na"),
            arrayOf(LITERAL_STRING, "a\\ra", "a\\ra"),
            arrayOf(LITERAL_STRING, "a\\ta", "a\\ta"),
            arrayOf(LITERAL_STRING, "a\\ba", "a\\ba"),
            arrayOf(LITERAL_STRING, "a\\fa", "a\\fa"),
            arrayOf(LITERAL_STRING, "a\\\"a", "a\\\"a"),
            arrayOf(LITERAL_STRING, "a\\\\a", "a\\\\a"),
            arrayOf(LITERAL_STRING, "\\u0119dw\\u0105rd", "\\u0119dw\\u0105rd"),
            arrayOf(LITERAL_STRING, "\\u", "\\u"),
            arrayOf(LITERAL_STRING, "\\u119dw\\u105rd", "\\u119dw\\u105rd"),
            arrayOf(LITERAL_STRING, "\\uzzzz", "\\uzzzz"),
            arrayOf(LITERAL_STRING, "\\U00000119dw\\U00000105rd", "\\U00000119dw\\U00000105rd"),
            arrayOf(LITERAL_STRING, "\\U", "\\U"),
            arrayOf(LITERAL_STRING, "\\U0119dw\\U0105rd", "\\U0119dw\\U0105rd"),
            arrayOf(LITERAL_STRING, "\\U0020FFFF", "\\U0020FFFF"),
            arrayOf(LITERAL_STRING, "foo\\\n    bar", "foo\\\n    bar"),
            arrayOf(LITERAL_STRING, "foo\\\r\n    bar", "foo\\\r\n    bar"),
            arrayOf(LITERAL_STRING, "\nfoo\\\n    bar", "\nfoo\\\n    bar"),
            arrayOf(LITERAL_STRING, "\nfoo\\\r\n    bar", "\nfoo\\\r\n    bar"),
            arrayOf(LITERAL_STRING, "\n\nfoo\\\n    bar", "\n\nfoo\\\n    bar"),
            arrayOf(LITERAL_STRING, "\n\nfoo\\\r\n    bar", "\n\nfoo\\\r\n    bar"),

            arrayOf(MULTILINE_LITERAL_STRING, "foo\\\n    bar", "foo\\\n    bar"),
            arrayOf(MULTILINE_LITERAL_STRING, "foo\\\r\n    bar", "foo\\\r\n    bar"),
            arrayOf(MULTILINE_LITERAL_STRING, "foo\\r\\nbar", "foo\\r\\nbar"),
            arrayOf(MULTILINE_LITERAL_STRING, "\nfoo\\\n    bar", "foo\\\n    bar"),
            arrayOf(MULTILINE_LITERAL_STRING, "\nfoo\\\r\n    bar", "foo\\\r\n    bar"),
            arrayOf(MULTILINE_LITERAL_STRING, "\nfoo\\r\\nbar", "foo\\r\\nbar"),
            arrayOf(MULTILINE_LITERAL_STRING, "\n\nfoo\\\n    bar", "\nfoo\\\n    bar"),
            arrayOf(MULTILINE_LITERAL_STRING, "\n\nfoo\\\r\n    bar", "\nfoo\\\r\n    bar"),
            arrayOf(MULTILINE_LITERAL_STRING, "\n\nfoo\\r\\nbar", "\nfoo\\r\\nbar")
        )
    }
}
