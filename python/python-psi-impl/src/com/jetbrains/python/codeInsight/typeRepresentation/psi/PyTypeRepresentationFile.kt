package com.jetbrains.python.codeInsight.typeRepresentation.psi

import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.python.codeInsight.typeRepresentation.PyTypeRepresentationDialect
import com.jetbrains.python.codeInsight.typeRepresentation.PyTypeRepresentationFileType
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyExpressionCodeFragment
import com.jetbrains.python.psi.impl.PyFileImpl

class PyTypeRepresentationFile : PyFileImpl, PyExpressionCodeFragment {

  constructor(text: String, context: PsiElement) : super(
    PsiManagerEx.getInstanceEx(context.project)
      .fileManager
      .createFileViewProvider(
        LightVirtualFile(
          "foo.bar",
          PyTypeRepresentationFileType,
          // workaround for PY-86754: strip def types
          text.replace(Regex("(?:^|[^\"'])def [\\w.]+"), "")
        ),
        false)
  ) {
    myContext = context
  }

  constructor(viewProvider: FileViewProvider) : super(viewProvider, PyTypeRepresentationDialect) {
    myContext = null
  }

  private val myContext: PsiElement?

  override fun getFileType(): FileType = PyTypeRepresentationFileType

  override fun toString(): String = "TypeRepresentation:$name"

  override fun getLanguageLevel(): LanguageLevel =
    // The same as for .pyi files
    LanguageLevel.getLatest()

  override fun getContext(): PsiElement? = if (myContext?.isValid == true) myContext else super.getContext()

  val type: PyExpression?
    get() = findChildByClass(PyExpression::class.java)
}
