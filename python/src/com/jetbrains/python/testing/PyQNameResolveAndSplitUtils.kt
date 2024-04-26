/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.testing

import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.extensions.QNameResolveContext
import com.jetbrains.python.extensions.getQName
import com.jetbrains.python.extensions.getRelativeNameTo
import com.jetbrains.python.extensions.resolveToElement
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyQualifiedNameOwner

/**
 * Utilities to find which part of [com.intellij.psi.util.QualifiedName] resolves to file and which one to the element
 * @author Ilya.Kazakevich
 */

data class QualifiedNameParts(val fileName: QualifiedName, val elementName: QualifiedName, val file: PyFile) {
  override fun toString() = elementName.toString()

  /**
   * @param folderToGetFileRelativePathTo parts of file are relative to this folder (or filename itself if folder is null)
   * @return element qname + several parts of file qname.
   */
  fun getElementNamePrependingFile(folderToGetFileRelativePathTo: PsiDirectory? = null): QualifiedName {

    var relativeFileName: QualifiedName? = null
    if (folderToGetFileRelativePathTo != null) {
      val folderQName = folderToGetFileRelativePathTo.getQName()
      relativeFileName = folderQName?.let { fileName.getRelativeNameTo(folderQName) } ?: fileName
    }

    if (relativeFileName == null) {
      relativeFileName = QualifiedName.fromComponents(fileName.lastComponent)
    }

    return relativeFileName.append(elementName)
  }
}

/**
 * Splits name owner's qname to [QualifiedNameParts]: filesystem part and element(symbol) part.
 *
 * @see [QualifiedName] extension
 * @return null if no file part found
 */
internal fun PyQualifiedNameOwner.tryResolveAndSplit(context: QNameResolveContext): QualifiedNameParts? {
  val qualifiedNameDottedString = this.qualifiedName ?: return getEmulatedQNameParts()

  val qualifiedName = QualifiedName.fromDottedString(qualifiedNameDottedString)
  val parts = qualifiedName.tryResolveAndSplit(context)
  if (parts != null) {
    return parts
  }
  val pyFile = containingFile as? PyFile ?: return null
  val fileQName = pyFile.getQName() ?: return null
  val relativePath = qualifiedName.getRelativeNameTo(fileQName) ?: return null
  return QualifiedNameParts(fileQName, relativePath, pyFile)
}

/**
 * For element containing dashes [PyQualifiedNameOwner.getQualifiedName] does not work.
 * this method creates [QualifiedNameParts] with file and qname path inside of it so even files with dashes could be supported
 * by test runners
 */
private fun PyQualifiedNameOwner.getEmulatedQNameParts(): QualifiedNameParts? {
  val ourFile = this.containingFile as? PyFile ?: return null
  val result = ArrayList<String>()
  var element: PsiElement = this
  while (element !is PsiFile) {
    if (element is NavigationItem) {
      val name = element.name
      if (name != null) {
        result.add(name)
      }
    }
    element = element.parent ?: return null
  }
  return QualifiedNameParts(QualifiedName.fromComponents(ourFile.virtualFile.nameWithoutExtension),
                            QualifiedName.fromComponents(result.reversed()), ourFile)
}

/**
 * Splits qname to [QualifiedNameParts]: filesystem part and element(symbol) part.
 * Resolve parts sequentially until meets file. May be slow.
 *
 * Functions goes from right (symbol) to the left (file name)
 *
 * @see [com.jetbrains.python.psi.PyQualifiedNameOwner] extensions
 * @return null if no file part found
 */
internal fun QualifiedName.tryResolveAndSplit(context: QNameResolveContext): QualifiedNameParts? {
  //TODO: May be slow, cache in this case

  // Find first element that may be file
  var i = this.componentCount
  while (i > 0) {
    val possibleFileName = this.subQualifiedName(0, i)
    var possibleFile = possibleFileName.resolveToElement(context)

    if (possibleFile is PsiDirectory) {
      // test class may be declared directly in init.py, so "module.Class" is not module.py but module/__init__.py
      possibleFile = possibleFile.files.firstOrNull { it.name == PyNames.INIT_DOT_PY }
    }

    if (possibleFile is PyFile) {
      return QualifiedNameParts(possibleFileName,
        this.subQualifiedName(possibleFileName.componentCount, this.componentCount),
        possibleFile)
    }
    i--
  }
  return null
}
