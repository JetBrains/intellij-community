// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.regexp

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.util.IncorrectOperationException
import org.intellij.lang.regexp.RegExpFile

class DjangoRegexpFile(viewProvider: FileViewProvider) : RegExpFile(viewProvider, PythonRegexpLanguage.INSTANCE), ContributedReferenceHost {
  override fun getReferences(): Array<PsiReference> = PsiReferenceService.getService().getContributedReferences(this)
}


class DjangoRegexpUrlPathFileManipulator : AbstractElementManipulator<DjangoRegexpFile>() {
  @Throws(IncorrectOperationException::class)
  override fun handleContentChange(file: DjangoRegexpFile, range: TextRange, newContent: String): DjangoRegexpFile {
    val document = FileDocumentManager.getInstance().getDocument(file.virtualFile)
    document!!.replaceString(range.startOffset, range.endOffset, newContent)
    PsiDocumentManager.getInstance(file.project).commitDocument(document)
    return file
  }
}
