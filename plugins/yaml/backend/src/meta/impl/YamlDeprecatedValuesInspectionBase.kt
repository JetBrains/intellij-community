// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.meta.impl

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.yaml.YAMLBundle
import org.jetbrains.yaml.meta.model.YamlEnumType
import org.jetbrains.yaml.psi.YAMLKeyValue

abstract class YamlDeprecatedValuesInspectionBase : YamlMetaTypeInspectionBase() {

  override fun doBuildVisitor(holder: ProblemsHolder, metaTypeProvider: YamlMetaTypeProvider): PsiElementVisitor = StructureChecker(holder, metaTypeProvider)

  private class StructureChecker(private val holder: ProblemsHolder, private val metaTypeProvider: YamlMetaTypeProvider) : SimpleYamlPsiVisitor() {
    override fun visitYAMLKeyValue(keyValue: YAMLKeyValue) {
      val yamlValue = keyValue.value ?: return

      val yamlValueText = keyValue.valueText
      if (yamlValueText.isEmpty()) return

      val yamlValueMetaType = metaTypeProvider.getValueMetaType(yamlValue)?.metaType ?: return
      when (yamlValueMetaType) {
        is YamlEnumType -> {
          if (yamlValueMetaType.isLiteralDeprecated(yamlValueText)) {
            holder.registerProblem(
              yamlValue,
              YAMLBundle.message("YamlDeprecatedKeysInspectionBase.deprecated.value", yamlValueText),
              ProblemHighlightType.LIKE_DEPRECATED
            )
          }
        }
      }
    }
  }
}