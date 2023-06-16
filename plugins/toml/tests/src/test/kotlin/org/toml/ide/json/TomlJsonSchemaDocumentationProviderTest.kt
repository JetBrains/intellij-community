package org.toml.ide.json

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language

class TomlSchemaDocumentationProviderTest : TomlJsonSchemaTestBase() {
    fun `test doc from table header`() = doTest("""
        [package<caret>]
    """, """
        <div class='definition'><pre>package: object</pre></div><div class='content'>Package definition<br/>Defines a package</div>
    """, TomlJsonSchemaDocumentationProvider::generateDoc)

    fun `test quick doc from table header`() = doTest("""
        [package<caret>]
    """, """
        <b>package</b>: object<br/>Package definition
    """, TomlJsonSchemaDocumentationProvider::getQuickNavigateInfo)

    fun `test doc from first segment in table header`() = doTest("""
        [<caret>package.authors]
    """, """
        <div class='definition'><pre>package: object</pre></div><div class='content'>Package definition<br/>Defines a package</div>
    """, TomlJsonSchemaDocumentationProvider::generateDoc)

    fun `test doc from last segment in table header`() = doTest("""
        [package.authors<caret>]
    """, """
        <div class='definition'><pre>authors: array</pre></div><div class='content'>Package<br/>Lists people or organizations that are considered the &quot;authors&quot; of the package</div>
    """, TomlJsonSchemaDocumentationProvider::generateDoc)

    fun `test doc from first segment in the middle of table header`() = doTest("""
        [package<caret>.authors]
    """, """
        <div class='definition'><pre>package: object</pre></div><div class='content'>Package definition<br/>Defines a package</div>
    """, TomlJsonSchemaDocumentationProvider::generateDoc)

    fun `test doc from last segment in the middle of table header`() = doTest("""
        [package.<caret>authors]
    """, """
        <div class='definition'><pre>authors: array</pre></div><div class='content'>Package<br/>Lists people or organizations that are considered the &quot;authors&quot; of the package</div>
    """, TomlJsonSchemaDocumentationProvider::generateDoc)

    fun `test doc from key`() = doTest("""
        [package]
        name<caret> = "foo"
    """, """
        <div class='definition'><pre>name: string</pre></div><div class='content'>Package name<br/>An identifier used to refer to the package</div>
    """, TomlJsonSchemaDocumentationProvider::generateDoc)

    fun `test doc for first key segment`() = doTest("""
        <caret>package.name = "foo"
    """, """
        <div class='definition'><pre>package: object</pre></div><div class='content'>Package definition<br/>Defines a package</div>
    """, TomlJsonSchemaDocumentationProvider::generateDoc)

    fun `test doc for last key segment`() = doTest("""
        package.name<caret> = "foo"
    """, """
        <div class='definition'><pre>name: string</pre></div><div class='content'>Package name<br/>An identifier used to refer to the package</div>
    """, TomlJsonSchemaDocumentationProvider::generateDoc)

    fun `test doc for first key segment in the middle`() = doTest("""
        package<caret>.name = "foo"
    """, """
        <div class='definition'><pre>package: object</pre></div><div class='content'>Package definition<br/>Defines a package</div>
    """, TomlJsonSchemaDocumentationProvider::generateDoc)

    fun `test doc for last key segment in the middle`() = doTest("""
        package.<caret>name = "foo"
    """, """
        <div class='definition'><pre>name: string</pre></div><div class='content'>Package name<br/>An identifier used to refer to the package</div>
    """, TomlJsonSchemaDocumentationProvider::generateDoc)

    fun `test doc for inline table key`() = doTest("""
        package = { name<caret> = "foo" }
    """, """
        <div class='definition'><pre>name: string</pre></div><div class='content'>Package name<br/>An identifier used to refer to the package</div>
    """, TomlJsonSchemaDocumentationProvider::generateDoc)

    fun doTest(
        @Language("TOML") text: String,
        @Language("HTML") expected: String?,
        block: TomlJsonSchemaDocumentationProvider.(PsiElement, PsiElement?) -> String?
    ) {
        InlineFile(text, "Cargo.toml")

        val element = DocumentationManager.getInstance(project)
            .findTargetElement(myFixture.editor, myFixture.file, myFixture.elementAtCaret)!!
        val originalElement = myFixture.file.findElementAt(element.textOffset)!!

        val provider = DocumentationProvider.EP_NAME.findExtensionOrFail(TomlJsonSchemaDocumentationProvider::class.java)
        val actual = provider.block(originalElement, null)?.trim()
        if (expected == null) {
            assertNull(actual) { "Expected null, got `$actual`" }
        } else {
            assertNotNull(actual) { "Expected not null result" }
            assertEquals(expected.trimIndent(), actual)
        }
    }
}