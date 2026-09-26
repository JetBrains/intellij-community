package com.jetbrains.python.codeInsight.typeRepresentation

import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PythonFileType
import org.jetbrains.annotations.NonNls

object PyTypeRepresentationFileType : PythonFileType(PyTypeRepresentationDialect) {
  override fun getName(): @NonNls String {
    return "PythonTypeRepresentation"
  }

  override fun getDescription(): String {
    return PyPsiBundle.message("filetype.python.type.representation.description")
  }

  override fun getDefaultExtension(): String {
    return "pythonTypeRepresentation"
  }
}