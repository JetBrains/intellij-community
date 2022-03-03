// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.images

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.util.parentOfType
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownImage

internal class MarkdownConfigureImageIntention: PsiElementBaseIntentionAction() {
  override fun getFamilyName(): String = text

  override fun getText(): String {
    return MarkdownBundle.message("markdown.configure.image.text")
  }

  override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
    val (targetElement, provider) = findElementAndProvider(element) ?: return
    ApplicationManager.getApplication().invokeLater {
      provider.performAction(targetElement)
    }
  }

  override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
    return findElementAndProvider(element) != null
  }

  override fun checkFile(file: PsiFile?): Boolean {
    return super.checkFile(file) && file?.viewProvider?.baseLanguage == MarkdownLanguage.INSTANCE
  }

  companion object {
    private val markdownProvider by lazy { ConfigureMarkdownImageLineMarkerProvider() }
    private val htmlProvider by lazy { ConfigureHtmlImageLineMarkerProvider() }
    private val textHtmlProvider by lazy { ConfigureTextHtmlImageLineMarkerProvider() }

    private val searchFunctions by lazy { arrayOf(::findForMarkdown, ::findForHtml, ::findForTextHtml) }

    private fun findForMarkdown(element: PsiElement): Pair<PsiElement, ConfigureImageLineMarkerProviderBase<*>>? {
      val targetElement = element.parentOfType<MarkdownImage>(withSelf = true) ?: return null
      return targetElement to markdownProvider
    }

    private fun findForHtml(element: PsiElement): Pair<PsiElement, ConfigureImageLineMarkerProviderBase<*>>? {
      val targetElement = element.parentOfType<HtmlTag>(withSelf = true)?.takeIf { it.name == "img" } ?: return null
      return targetElement to htmlProvider
    }

    private fun findForTextHtml(element: PsiElement): Pair<PsiElement, ConfigureImageLineMarkerProviderBase<*>>? {
      val targetElement = textHtmlProvider.obtainOuterElement(element) ?: return null
      return targetElement to textHtmlProvider
    }

    private fun findElementAndProvider(element: PsiElement): Pair<PsiElement, ConfigureImageLineMarkerProviderBase<*>>? {
      for (find in searchFunctions) {
        return find.invoke(element) ?: continue
      }
      return null
    }
  }
}

