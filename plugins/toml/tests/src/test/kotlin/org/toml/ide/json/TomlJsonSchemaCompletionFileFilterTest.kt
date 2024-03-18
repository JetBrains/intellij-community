package org.toml.ide.json

import com.intellij.psi.PsiFile
import com.intellij.testFramework.ExtensionTestUtil

class TomlJsonSchemaCompletionFileFilterTest : TomlJsonSchemaCompletionTestBase() {
    fun `test no completion for file when filtered out`() {
        ExtensionTestUtil.maskExtensions(TomlJsonSchemaCompletionFileFilter.EP_NAME, listOf(TestFileFilterExclude()), testRootDisposable)

        checkNotContainsCompletions("""
            <caret>
        """)
    }

    fun `test complete if filter is not valid for this file`() {
        ExtensionTestUtil.maskExtensions(TomlJsonSchemaCompletionFileFilter.EP_NAME, listOf(TestFileFilterDoNotExclude()), testRootDisposable)

        checkContainsCompletion(setOf("dependencies"), """
            dep<caret>
        """)
    }
}

private class TestFileFilterExclude: TomlJsonSchemaCompletionFileFilter {
    override fun shouldCompleteInFile(file: PsiFile): Boolean {
        return file.name != "Cargo.toml"
    }
}

private class TestFileFilterDoNotExclude: TomlJsonSchemaCompletionFileFilter {
    override fun shouldCompleteInFile(file: PsiFile): Boolean {
        return file.name != "Foo.toml"
    }
}