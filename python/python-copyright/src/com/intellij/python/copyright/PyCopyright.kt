// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.python.copyright

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFile
import com.maddyhome.idea.copyright.CopyrightProfile
import com.maddyhome.idea.copyright.options.LanguageOptions
import com.maddyhome.idea.copyright.psi.UpdateCopyright
import com.maddyhome.idea.copyright.psi.UpdateCopyrightsProvider
import com.maddyhome.idea.copyright.psi.UpdatePsiFileCopyright
import java.util.function.Predicate
import java.util.regex.Pattern

class PyUpdateCopyrightsProvider : UpdateCopyrightsProvider() {
  override fun createInstance(project: Project,
                              module: Module?,
                              file: VirtualFile,
                              base: FileType?,
                              options: CopyrightProfile?): UpdateCopyright = PyUpdateFileCopyright(project, module, file, options)

  override fun getDefaultOptions(): LanguageOptions {
    val options = super.getDefaultOptions()
    options.block = false
    options.fileTypeOverride = LanguageOptions.USE_TEXT
    return options
  }
}


class PyUpdateFileCopyright(project: Project, module: Module?, root: VirtualFile, options: CopyrightProfile?) : UpdatePsiFileCopyright(
  project, module, root, options) {

  override fun accept() = file is PyFile

  override fun scanFile() {
    val f = file as PyFile

    val first = if (f.firstChild != null) first(f) else f

    for (block in arrayOf(f.importBlock, f.topLevelClasses, f.topLevelAttributes, f.topLevelFunctions)) {
      if (block.size > 0) {
        checkComments(first, block.first(), true)
        return
      }
    }

    checkComments(first, f, true)
  }
}

private fun first(file: PyFile): PsiElement = coding(shebang(file.firstChild))

private fun isShebangComment(element: PsiElement) = element is PsiComment && element.text.startsWith("#!")

private fun shebang(element: PsiElement) = if (isShebangComment(element)) {
  skipWhitespaceForwardNotNull(element.nextSibling)
}
else {
  element
}

private fun skipWhitespaceForwardNotNull(e: PsiElement): PsiElement {
  return PsiTreeUtil.skipWhitespacesForward(e) ?: e
}

private val ENCODING_PATTERN: Predicate<String> = Pattern.compile("^[ \\t\\f]*#.*?coding[:=][ \\t]*([-_.a-zA-Z0-9]+)").asPredicate()


private fun isEncodingComment(element: PsiElement) = element is PsiComment && ENCODING_PATTERN.test(element.text)

private fun coding(element: PsiElement) = if (isEncodingComment(element)) {
  skipWhitespaceForwardNotNull(element.nextSibling)
}
else {
  element
}

