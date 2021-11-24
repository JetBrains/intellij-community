package org.toml.ide.json

import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.toml.TomlTestBase

class TomlJsonFindPositionTest : TomlTestBase() {
    fun `test empty`() {
        myFixture.configureByText("example.toml", "")
        assertElementPosition(myFixture.file, "/")
    }

    fun `test single segment key`() = assertPosition("""
        foo<caret> = 1
    """, "/foo")

    fun `test multiple segment key`() = assertPosition("""
        foo.bar<caret> = 1
    """, "/foo/bar")

    fun `test key segment caret in the middle`() = assertPosition("""
        foo<caret>.bar = 1
    """, "/foo")

    fun `test value`() = assertPosition("""
        foo = 1<caret>
    """, "/foo")

    fun `test value with multiple key segments`() = assertPosition("""
        foo.bar = 1<caret>
    """, "/foo/bar")

    fun `test table header`() = assertPosition("""
        [foo<caret>]
    """, "/foo")

    fun `test inside table header`() = assertPosition("""
        [foo<caret>]
    """, "/foo")

    fun `test multiple segments inside table header`() = assertPosition("""
        [foo.bar<caret>]
    """, "/foo/bar")

    fun `test in middle multiple segments inside table header`() = assertPosition("""
        [foo<caret>.bar]
    """, "/foo")

    fun `test value in table`() = assertPosition("""
        [foo]
        bar = 1<caret>
    """, "/foo/bar")

    fun `test value in array table`() = assertPosition("""
        [[foo]]
        bar = 1<caret>
    """, "/foo/0/bar")

    fun `test inline table value`() = assertPosition("""
        foo.bar = { baz = { qux = 1<caret>} }
    """, "/foo/bar/baz/qux")

    fun `test inline array value`() = assertPosition("""
        foo.bar = [1, 2, 3<caret>, 4]
    """, "/foo/bar/2")

    fun `test inline table and array value`() = assertPosition("""
        foo.bar = { baz = [ 1, 2, 3, 4, { qux = 1<caret>} } }
    """, "/foo/bar/baz/4/qux")

    fun `test value with multiple array tables`() = assertPosition("""
        [[foo]]
        
        [[foo]]
        bar = 1<caret>
        
        [[foo]]
    """, "/foo/1/bar")

    fun `test inline array`() = assertPosition("""
        [[foo]]
        
        [[foo]]
        bar = 1<caret>
        
        [[foo]]
    """, "/foo/1/bar")

    private fun assertPosition(@Language("TOML") code: String, expectedPosition: String) {
        myFixture.configureByText("example.toml", code.trimIndent())
        val caretOffset = myFixture.caretOffset - 1
        val element = myFixture.file.findElementAt(caretOffset)!!
        assertElementPosition(element, expectedPosition)
    }

    private fun assertElementPosition(element: PsiElement, expectedPosition: String) {
        val position = TomlJsonPsiWalker.findPosition(element, true)
        assertEquals(expectedPosition, position.toJsonPointer())
    }
}