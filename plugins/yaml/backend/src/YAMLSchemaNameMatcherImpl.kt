// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml

import com.intellij.json.JsonSchemaSpellcheckerClient
import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

internal class YAMLSchemaNameMatcherImpl : YAMLSchemaNameMatcher {
  override fun matches(element: PsiElement): Boolean = JsonSchemaSpellcheckerClientForYaml(element).matchesNameFromSchema()

  private class JsonSchemaSpellcheckerClientForYaml(private val element: PsiElement) : JsonSchemaSpellcheckerClient() {
    override fun getElement(): PsiElement = element

    override fun getValue(): String? {
      val parent = element.parent
      return when {
        element.node.elementType == YAMLTokenTypes.SCALAR_KEY -> (parent as YAMLKeyValue).keyText
        parent is YAMLKeyValue -> parent.valueText
        parent is YAMLScalar -> parent.textValue
        else -> null
      }
    }

    override fun isXIntellijInjection(service: JsonSchemaService, rootSchema: JsonSchemaObject): Boolean = false
  }
}
