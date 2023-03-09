package org.intellij.plugins.markdown.model.psi

import com.intellij.navigation.TargetPresentationBuilder
import com.intellij.psi.PsiFile

internal fun TargetPresentationBuilder.withLocationIn(file: PsiFile): TargetPresentationBuilder {
  val virtualFile = file.containingFile.virtualFile ?: return this
  val fileType = virtualFile.fileType
  return locationText(virtualFile.name, fileType.icon)
}
