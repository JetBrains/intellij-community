package org.toml.ide.resolve

import com.intellij.openapi.paths.WebReference
import org.intellij.lang.annotations.Language
import org.toml.TomlTestBase

class TomlWebReferenceProviderTest : TomlTestBase() {

    fun `test reference in top level key`() = checkUrlReference("""
        key = "<caret>http://localhost:8080"
    """, "http://localhost:8080")

    fun `test reference in table`() = checkUrlReference("""
        [package]
        homepage = '<caret>http://localhost:8080'
    """, "http://localhost:8080")

    fun `test reference in inline table`() = checkUrlReference("""
        [dependencies]
        foo = { git = "<caret>https://github.com/foo/bar" }
    """, "https://github.com/foo/bar")

    fun `test escaped value`() = checkUrlReference("""
        key = "<caret>https://github\u002Ecom/foo/bar"    
    """, "https://github.com/foo/bar")

    fun `test not url 1`() = checkNoUrlReference("""
        not-url = "http : // local"        
    """)

    fun `test not url 2`() = checkNoUrlReference("""
        regular-data = "some://not-url" 
    """)

    private fun checkUrlReference(@Language("TOML") code: String, url: String) {
        InlineFile(code, "example.toml")
        val reference = myFixture.getReferenceAtCaretPosition()
        assertInstanceOf(reference, WebReference::class.java)
        assertEquals(url, (reference as WebReference).url)
    }

    private fun checkNoUrlReference(@Language("TOML") code: String) {
        InlineFile(code, "example.toml")
        assertNull(myFixture.getReferenceAtCaretPosition())
    }
}
