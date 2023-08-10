// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.util.asSafely
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter
import com.jetbrains.jsonSchema.impl.fixes.AddOptionalPropertiesIntention
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.schema.YamlJsonPsiWalker

class YAMLAddOptionalPropertiesIntention : AddOptionalPropertiesIntention() {
  override fun findContainingObjectNode(editor: Editor, file: PsiFile): PsiElement? {
    val offset = editor.caretModel.offset
    return file.findElementAt(offset)?.parentOfType<YAMLMapping>(false)
           ?: file.asSafely<YAMLFile>()?.documents?.singleOrNull()?.takeIf { it.children.isEmpty() }
  }

  override fun getSyntaxAdapter(project: Project): JsonLikeSyntaxAdapter {
    return YamlJsonPsiWalker.INSTANCE.getSyntaxAdapter(project)
  }
}