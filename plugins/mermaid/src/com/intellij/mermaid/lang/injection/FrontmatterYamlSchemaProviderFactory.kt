// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.injection

import com.intellij.openapi.project.Project
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory

class FrontmatterYamlSchemaProviderFactory : JsonSchemaProviderFactory {
  override fun getProviders(project: Project): MutableList<JsonSchemaFileProvider> {
    return mutableListOf(FrontmatterYamlSchemaFileProvider(project))
  }
}
