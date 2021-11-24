/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.json

class TomlJsonSchemaCompletionTest : TomlJsonSchemaCompletionTestBase() {
    fun `test top level completion`() = checkContainsCompletion(listOf("package", "dependencies"), """
        <caret>
    """)

    fun `test completion inside table`() = checkContainsCompletion(listOf("name", "version", "authors", "edition"), """
        [package]
        <caret>
    """)

    fun `test completion inside array table`() = checkContainsCompletion(listOf("name", "path"), """
        [[bin]]
        <caret>
    """)

    fun `test completion in key segments`() = checkContainsCompletion(listOf("name", "version", "authors", "edition"), """
        package.<caret>
    """)

    fun `test completion in table header`() = checkContainsCompletion(listOf("name", "version", "authors", "edition"), """
        [package.<caret>]
    """)

    fun `test completion in table key`() = checkContainsCompletion(listOf("dependencies", "package"), """
        [<caret>]
    """)

    fun `test completion in array key`() = checkContainsCompletion(listOf("bin"), """
        [[<caret>]]
    """)

    fun `test completion inside inline tables`() = checkContainsCompletion(listOf("name", "version", "authors", "edition"), """
        package = { <caret> }
    """)

    fun `test completion inside inline array`() =  checkContainsCompletion(listOf("name", "path"), """
        bin = [{<caret>}]
    """)

    fun `test completion in variable path`() = checkContainsCompletion(listOf("version", "features"), """
        [dependencies]
        foo = { <caret> }
    """)

    fun `test key segment completion`() = checkContainsCompletion(listOf("version", "features"), """
        target.'cfg(unix)'.dependencies.rocket.<caret>
    """)
}
