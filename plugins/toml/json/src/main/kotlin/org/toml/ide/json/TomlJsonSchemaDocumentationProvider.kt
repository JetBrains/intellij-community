package org.toml.ide.json

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonSchemaDocumentationProvider
import org.jetbrains.annotations.Nls

class TomlJsonSchemaDocumentationProvider : DocumentationProvider {
    @Nls
    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? =
        findSchemaAndGenerateDoc(element, true)

    @Nls
    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? =
        findSchemaAndGenerateDoc(element, false)

    @Nls
    private fun findSchemaAndGenerateDoc(element: PsiElement?, preferShort: Boolean): String? {
        if (element == null) return null
        val service = JsonSchemaService.Impl.get(element.project)
        val file = element.containingFile ?: return null
        val schema = service.getSchemaObject(file) ?: return null
        return JsonSchemaDocumentationProvider.generateDoc(element, schema, preferShort, null)
    }
}