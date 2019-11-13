/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.LocalTimeCounter

class TomlPsiFactory(private val project: Project, private val markGenerated: Boolean = true) {
    fun createFile(text: CharSequence): TomlFile =
        PsiFileFactory.getInstance(project)
            .createFileFromText(
                "DUMMY.toml",
                TomlFileType,
                text,
                /*modificationStamp =*/ LocalTimeCounter.currentTime(), // default value
                /*eventSystemEnabled =*/ false, // default value
                /*markAsCopy =*/ markGenerated // `true` by default
            ) as TomlFile

    private inline fun <reified T : TomlElement> createFromText(code: String): T? =
        createFile(code).descendantOfTypeStrict()

    // Copied from org.rust.lang.core.psi.ext as it's not available here
    private inline fun <reified T : PsiElement> PsiElement.descendantOfTypeStrict(): T? =
        PsiTreeUtil.findChildOfType(this, T::class.java, /* strict */ true)

    fun createLiteral(value: String): TomlLiteral =
        // If you're creating a string value, like `serde = "1.0.90"` make sure that the `value` parameter actually
        // contains the quote in the beginning and the end. E.g.: `createValue("\"1.0.90\"")`
        createFromText("dummy = $value") ?: error("Failed to create TomlLiteral")

    fun createKey(key: String): TomlKey =
        createFromText("$key = \"dummy\"") ?: error("Failed to create TomlKey")

    fun createKeyValue(text: String): TomlKeyValue =
        // Make sure that `text` includes the equals sign in the middle like so: "serde = \"1.0.90\""
        createFromText(text) ?: error("Failed to create TomlKeyValue")

    fun createKeyValue(key: String, value: String): TomlKeyValue =
        createFromText("$key = $value") ?: error("Failed to create TomlKeyValue")

    fun createTable(name: String): TomlTable =
        createFromText("[$name]") ?: error("Failed to create TomlTableHeader")

    fun createTableHeader(name: String): TomlTableHeader =
        createFromText("[$name]") ?: error("Failed to create TomlTableHeader")

    fun createArray(contents: String): TomlArray =
        createFromText("dummy = [$contents]") ?: error("Failed to create TomlArray")

    fun createInlineTable(contents: String): TomlInlineTable =
        createFromText("dummy = {$contents}") ?: error("Failed to create TomlInlineTable")
}
