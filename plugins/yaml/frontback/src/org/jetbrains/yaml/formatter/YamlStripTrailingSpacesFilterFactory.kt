// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.formatter

import com.intellij.lang.Language
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.PsiBasedStripTrailingSpacesFilter
import com.intellij.psi.PsiFile
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YamlRecursivePsiElementVisitor
import org.jetbrains.yaml.settingsSync.shouldDoNothingInBackendMode

private class YamlStripTrailingSpacesFilterFactory : PsiBasedStripTrailingSpacesFilter.Factory() {
  override fun createFilter(document: Document): PsiBasedStripTrailingSpacesFilter = object : PsiBasedStripTrailingSpacesFilter(document) {
    override fun process(psiFile: PsiFile) {
      if (shouldDoNothingInBackendMode()) return

      psiFile.accept(object : YamlRecursivePsiElementVisitor(){
        override fun visitScalar(scalar: YAMLScalar) {
          disableRange(scalar.textRange, false)
          super.visitScalar(scalar)
        }
      })
    }
  }

  override fun isApplicableTo(language: Language): Boolean = language.`is`(YAMLLanguage.INSTANCE)
}