// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.index

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.CommonProcessors
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeaderImpl

class MarkdownHeadersIndex : StringStubIndexExtension<MarkdownHeaderImpl>() {
  override fun getKey(): StubIndexKey<String, MarkdownHeaderImpl> = KEY

  companion object {
    val KEY: StubIndexKey<String, MarkdownHeaderImpl> = StubIndexKey.createIndexKey("markdown.header")

    fun collectFileHeaders(suggestHeaderRef: String, project: Project, psiFile: PsiFile?): Collection<PsiElement> {
      val list = mutableListOf<PsiElement>()
      StubIndex.getInstance().processElements(
        KEY, suggestHeaderRef, project, psiFile?.let { GlobalSearchScope.fileScope(it) }, MarkdownHeaderImpl::class.java,
        CommonProcessors.CollectProcessor(list)
      )
      return list
    }
  }
}
