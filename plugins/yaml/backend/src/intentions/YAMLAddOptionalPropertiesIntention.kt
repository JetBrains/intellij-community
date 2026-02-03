// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter
import com.jetbrains.jsonSchema.impl.fixes.AddOptionalPropertiesIntention
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.schema.YamlJsonPsiWalker

class YAMLAddOptionalPropertiesIntention : AddOptionalPropertiesIntention() {
  override fun findContainingObjectNode(context: ActionContext, element: PsiElement): PsiElement? {
    element.parentOfType<YAMLMapping>(false)?.let { return it }
    val yamlFile = element.containingFile as? YAMLFile ?: return null
    return yamlFile.documents.singleOrNull()?.takeIf { it.children.isEmpty() }
  }

  override fun findPhysicalObjectNode(context: ActionContext, element: PsiElement): PsiElement? {
    val physLeaf = context.findLeaf() ?: return null
    return physLeaf.parentOfType<YAMLMapping>(false)
           ?: (context.file() as? YAMLFile)?.documents?.singleOrNull()?.takeIf { it.children.isEmpty() }
  }

  override fun getSyntaxAdapter(project: Project): JsonLikeSyntaxAdapter {
    return YamlJsonPsiWalker.INSTANCE.getSyntaxAdapter(project)
  }
}