// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.injection

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.mermaid.MermaidBundle.message
import com.intellij.mermaid.lang.psi.MermaidDirectiveValue
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType

class DirectiveJsonSchemaFileProvider(private val project: Project) : JsonSchemaFileProvider {
  override fun isAvailable(file: VirtualFile): Boolean {
    return runReadAction { isInjectedJson(file) }
  }

  private fun isInjectedJson(file: VirtualFile): Boolean {
    if (!file.isValid) {
      return false
    }
    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return false
    val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
    val host = injectedLanguageManager.getInjectionHost(psiFile) ?: return false
    return host is MermaidDirectiveValue
  }

  override fun getName(): String {
    return message("mermaid.directives.json.schema.name")
  }

  override fun getSchemaFile(): VirtualFile? {
    return JsonSchemaProviderFactory.getResourceFile(this::class.java, schemaFileName)
  }

  override fun getSchemaType(): SchemaType {
    return SchemaType.schema
  }

  companion object {
    @Suppress("ConstPropertyName")
    private const val schemaFileName = "directive_schema.json"
  }
}
