package org.toml.ide.json

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.testFramework.registerExtension
import org.toml.ide.json.TomlJsonSchemaValueCompletionTest.TestCompletionContributor.Companion.TEST_COMPLETION_VARIANT
import org.toml.lang.TomlLanguage

class TomlJsonSchemaValueCompletionTest : TomlJsonSchemaCompletionTestBase() {
    fun `test enum variants`() = checkContainsCompletion(setOf("\"2015\"", "\"2018\"", "\"2021\""), """
        [package]
        edition = <caret>
    """)

    fun `test variants inside array table`() = checkContainsCompletion(setOf("\"\""), """
        [[bin]]
        name = <caret>
    """)

    fun `test string literal inside literal variants 1`() = checkNotContainsCompletion(setOf("\"\"", "{}", "[]"), """
        [package]
        name = "<caret>"
    """)

    fun `test string literal inside literal variants 2`() = checkNotContainsCompletion(setOf("\"\"", "{}", "[]"), """
        bar = '<caret>'
    """)

    fun `test string literal inside literal variants 3`() = checkNotContainsCompletion(setOf("\"\"", "{}", "[]"), """
        baz = "<caret>"
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

    fun `test ordering`() {
        val completionContributorEP = CompletionContributorEP(
            TomlLanguage.id,
            TestCompletionContributor::class.java.name,
            DefaultPluginDescriptor("testTomlPluginDescriptor")
        )

        ApplicationManager.getApplication().registerExtension(CompletionContributor.EP, completionContributorEP, testRootDisposable)
        checkCompletionList(listOf(TEST_COMPLETION_VARIANT, "\"\""), """
            [package]
            name = <caret>    
        """)
    }

    private class TestCompletionContributor : CompletionContributor() {
        override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
            result.addElement(LookupElementBuilder.create(TEST_COMPLETION_VARIANT))
        }

        companion object {
            const val TEST_COMPLETION_VARIANT = "testCompletionVariant"
        }
    }
}