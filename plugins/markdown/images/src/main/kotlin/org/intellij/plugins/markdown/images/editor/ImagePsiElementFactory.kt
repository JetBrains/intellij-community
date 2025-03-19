package org.intellij.plugins.markdown.images.editor

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ImagePsiElementFactory {
  fun createImage(project: Project, description: String?, path: String, title: String?): PsiElement {
    val text = ImageUtils.createMarkdownImageText(description.orEmpty(), path, title.orEmpty())
    return MarkdownPsiElementFactory.createFile(project, text).firstChild.firstChild
  }

  fun createHtmlBlockWithImage(project: Project, imageData: MarkdownImageData): PsiElement {
    val text = ImageUtils.createHtmlImageText(imageData)
    return MarkdownPsiElementFactory.createFile(project, text).firstChild
  }

  fun createHtmlImageTag(project: Project, imageData: MarkdownImageData): PsiElement {
    val text = ImageUtils.createHtmlImageText(imageData)
    val root = MarkdownPsiElementFactory.createFile(project, "Prefix text$text").firstChild
    return root.firstChild.nextSibling
  }
}
