package org.intellij.plugins.markdown.model.psi

import com.intellij.platform.backend.presentation.TargetPresentationBuilder
import com.intellij.psi.PsiFile

internal fun TargetPresentationBuilder.withLocationIn(file: PsiFile): TargetPresentationBuilder {
  val virtualFile = file.containingFile.virtualFile ?: return this
  val fileType = virtualFile.fileType
  return locationText(virtualFile.name, fileType.icon)
}
