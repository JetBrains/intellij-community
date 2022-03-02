/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.json

class TomlJsonSchemaKeyCompletionTest : TomlJsonSchemaCompletionTestBase() {
    fun `test top level completion`() = checkContainsCompletion(setOf("package", "dependencies"), """
        <caret>
    """)

    fun `test completion inside table`() = checkContainsCompletion(setOf("name", "version", "authors", "edition"), """
        [package]
        <caret>
    """)

    fun `test single completion`() = checkContainsCompletion(setOf("package"), """
        [packa<caret>]
        <caret>
    """)

    fun `test completion inside array table`() = checkContainsCompletion(setOf("name", "path"), """
        [[bin]]
        <caret>
    """)

    fun `test completion in key segments`() = checkContainsCompletion(setOf("name", "version", "authors", "edition"), """
        package.<caret>
    """)

    fun `test completion in table header`() = checkContainsCompletion(setOf("authors"), """
        [package.<caret>]
    """)

    fun `test no completion of literals in table key`() = checkNotContainsCompletion(setOf("name", "version", "edition"), """
        [package.<caret>]
    """)

    fun `test completion in table key`() = checkContainsCompletion(setOf("dependencies", "package"), """
        [<caret>]
    """)

    fun `test completion in array key`() = checkContainsCompletion(setOf("bin"), """
        [[<caret>]]
    """)

    fun `test completion inside inline tables`() = checkContainsCompletion(setOf("name", "version", "authors", "edition"), """
        package = { <caret> }
    """)

    fun `test completion inside inline array`() =  checkContainsCompletion(setOf("name", "path"), """
        bin = [{<caret>}]
    """)

    fun `test completion in variable path`() = checkContainsCompletion(setOf("version", "features"), """
        [dependencies]
        foo = { <caret> }
    """)

    fun `test key segment completion`() = checkContainsCompletion(setOf("version", "features"), """
        target.'cfg(unix)'.dependencies.rocket.<caret>
    """)

    fun `test mixed nested tables completion`() = checkContainsCompletion(setOf("d"), """
        [foo]
        a = 0
        
        [[foo.bar]]
        c = 0
        
        [[foo.baz]]
        b = 0
        
        [[foo.bar.qux]]
        d = 0
        
        [[foo.baz]]
        b = 1
        
        [[foo.bar.qux]]
        <caret>
    """)
}