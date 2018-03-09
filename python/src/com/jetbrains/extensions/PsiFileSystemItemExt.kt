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
package com.jetbrains.extensions

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyPsiFacade

/**
 * @author Ilya.Kazakevich
 */

fun PsiFileSystemItem.getQName():QualifiedName?  {
  val name = PyPsiFacade.getInstance(this.project).findShortestImportableName(this.virtualFile, this) ?: return null
  return QualifiedName.fromDottedString(name)
}

/**
 * @return pyfile or package
 */
fun PsiFileSystemItem.isPythonModule() = this is PyFile || (this is PsiDirectory && this.findFile(PyNames.INIT_DOT_PY) != null)