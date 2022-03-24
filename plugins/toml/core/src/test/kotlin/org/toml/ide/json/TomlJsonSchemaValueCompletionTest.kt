package org.toml.ide.json

class TomlJsonSchemaValueCompletionTest : TomlJsonSchemaCompletionTestBase() {
    fun `test enum variants`() = checkContainsCompletion(setOf("\"2015\"", "\"2018\"", "\"2021\""), """
        [package]
        edition = <caret>
    """)

    fun `test variants inside array table`() = checkContainsCompletion(setOf("\"\""), """
        [[bin]]
        name = <caret>
    """)

    fun `test string literal inside literal variants`() = checkNotContainsCompletion(setOf("\"\""), """
        [package]
        name = "<caret>"
    """)

    fun `test boolean value variants`() = checkContainsCompletion(setOf("true", "false"), """
        [[bin]]
        test = <caret>
    """)

    // TODO: Support value completion in inline tables
    fun `test variants inside inline table`() {
        assertThrows(IllegalStateException::class.java) {
            checkContainsCompletion(setOf("\"\""), """
                package = { name = <caret> } 
            """)
        }
    }

    fun `test inside inside inline array`() {
        assertThrows(IllegalStateException::class.java) {
            checkContainsCompletion(setOf("{}"), """
            bin = [<caret>]
        """)
        }
    }

    fun `test variants inside inline array and table`() {
        assertThrows(IllegalStateException::class.java) {
            checkContainsCompletion(setOf("\"\""), """
                bin = [{ name = <caret> }]
            """)
        }
    }

    fun `test enum string value completion in literal`() = doSingleCompletion("""
        [package]
        edition = "21<caret>"
    """, """
        [package]
        edition = "2021<caret>"
    """)

    fun `test enum string value completion`() = doSingleCompletion("""
        [package]
        edition = 21<caret>
    """, """
        [package]
        edition = "2021"<caret>
    """)

    fun `test array completion`() = doSingleCompletion("""
        [package]
        authors = <caret>
    """, """
        [package]
        authors = [<caret>]
    """)

    fun `test inline table completion`() = doSingleCompletion("""
        [dependencies]
        foo = <caret>
    """, """
        [dependencies]
        foo = {<caret>}
    """)

    fun `test string literal completion`() = doSingleCompletion("""
        [package]
        name = <caret>
    """, """
        [package]
        name = "<caret>"
    """)

    fun `test number enum variants`() = checkContainsCompletion(setOf("1", "2"), """
        [foo]
        number-enum = <caret>
    """)
}