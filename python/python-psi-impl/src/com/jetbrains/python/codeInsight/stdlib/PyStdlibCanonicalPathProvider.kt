// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.stdlib

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.resolve.PyCanonicalPathProvider
import com.jetbrains.python.psi.resolve.pythonSdkIfReady
import com.jetbrains.python.sdk.skeleton.PySkeletonUtil


class PyStdlibCanonicalPathProvider : PyCanonicalPathProvider {
  override fun getCanonicalPath(symbol: PsiElement?, qName: QualifiedName, foothold: PsiElement?): QualifiedName? {
    val virtualFile = PsiUtilCore.getVirtualFile(symbol)
    if (virtualFile != null && foothold != null) {
      val canonicalPath = restoreStdlibCanonicalPath(qName)
      if (canonicalPath != null && PySkeletonUtil.isStdLib(virtualFile, foothold.pythonSdkIfReady())) {
        return canonicalPath
      }
    }
    return null
  }
}

private fun QualifiedName.replaceHead(newHead: String): QualifiedName {
  return QualifiedName.fromDottedString(newHead).append(removeHead(1))
}

fun restoreStdlibCanonicalPath(qName: QualifiedName): QualifiedName? {
  if (qName.matchesPrefix(QualifiedName.fromComponents("mock", "mock"))) {
    return qName.removeHead(1)
  }
  return when (qName.firstComponent) {
    "_abcoll", "_collections" -> qName.replaceHead("collections")
    "_collections_abc" -> qName.replaceHead("collections.abc")
    "posix", "nt" -> qName.replaceHead("os")
    "_functools" -> qName.replaceHead("functools")
    "_struct" -> qName.replaceHead("struct")
    "_io", "_pyio", "_fileio" -> qName.replaceHead("io")
    "_datetime" -> qName.replaceHead("datetime")
    "ntpath", "posixpath", "path", "macpath", "os2emxpath", "genericpath" -> qName.replaceHead("os.path")
    "_sqlite3" -> qName.replaceHead("sqlite3")
    "_pickle" -> qName.replaceHead("pickle")
    "_decimal" -> qName.replaceHead("decimal")
    else -> null
  }
}
