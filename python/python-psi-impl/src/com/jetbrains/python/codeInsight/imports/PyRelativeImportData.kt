package com.jetbrains.python.codeInsight.imports

import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.resolve.QualifiedNameFinder

/**
 * @author Aleksei Kniazev
 */
class PyRelativeImportData private constructor(val relativeLocation: String, val relativeLevel: Int)  {

  val locationWithDots: String
    get() = ".".repeat(relativeLevel) + relativeLocation

  companion object {
    @JvmStatic
    fun fromString(location: String, file: PyFile): PyRelativeImportData? {
      val qName = QualifiedName.fromDottedString(location)
      val fileQName = QualifiedNameFinder.findCanonicalImportPath(file, null) ?: return null

      if (qName.firstComponent != fileQName.firstComponent) return null

      val common = fileQName.components.asSequence()
        .zip(qName.components.asSequence()) { s1, s2 -> s1 == s2 }
        .takeWhile { it }
        .count()

      val remainingFileQname = fileQName.removeHead(common)
      val remainingQname = qName.removeHead(common)
      val implicitLevel = if (file.name == PyNames.INIT_DOT_PY) 1 else 0

      return PyRelativeImportData(remainingQname.toString(), remainingFileQname.componentCount + implicitLevel)
    }
  }
}
