// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.python.pixi.backend.PixiPyTool
import com.intellij.python.pyproject.model.evolution.findEvoPyProjectIfReady
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKeyValue

internal class PixiDependenciesInspectionSuppressor : InspectionSuppressor, DumbAware {
  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
    if (toolId != "Dependencies") return false
    val file = element.containingFile as? TomlFile ?: return false
    if (file.name != "pyproject.toml") return false
    val declaration = PsiTreeUtil.getParentOfType(element, TomlKeyValue::class.java, false) ?: return false
    if (keyPath(declaration) != listOf("project", "dependencies")) return false
    val interpreter = file.findEvoPyProjectIfReady(mainForOrphans = false)?.interpreter ?: return false
    if (interpreter.ref.manager != PixiPyTool.getInstance().fusId) return false
    return PixiInspectionManifest(file).value(
      listOf("tool", "pixi", "environments", interpreter.ref.envRef.value, "no-default-feature"),
    ) == true
  }

  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> = SuppressQuickFix.EMPTY_ARRAY
}
