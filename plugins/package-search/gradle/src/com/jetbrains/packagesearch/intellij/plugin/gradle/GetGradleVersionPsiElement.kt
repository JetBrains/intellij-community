package com.jetbrains.packagesearch.intellij.plugin.gradle

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

internal fun getGradleVersionPsiElement(
  dependency: UnifiedDependency,
  file: PsiFile
): PsiElement? {
    val groupId = dependency.coordinates.groupId ?: return null
    val artifactId = dependency.coordinates.artifactId ?: return null
    return GradleModuleTransformer.findDependencyElement(file, groupId, artifactId)
}
