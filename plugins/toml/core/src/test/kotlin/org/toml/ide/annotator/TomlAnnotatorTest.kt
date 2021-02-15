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
    """)
}
