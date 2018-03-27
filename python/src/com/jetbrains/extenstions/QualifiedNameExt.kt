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
package com.jetbrains.extenstions

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.QualifiedName
import com.jetbrains.extensions.getSdk
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.resolve.*
import com.jetbrains.python.psi.stubs.PyModuleNameIndex
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.PythonSdkType

interface ContextAnchor {
  val sdk: Sdk?
  val project: Project
  val qualifiedNameResolveContext: PyQualifiedNameResolveContext?
  val scope: GlobalSearchScope
}

class ModuleBasedContextAnchor(module: Module) : ContextAnchor {
  override val sdk = module.getSdk()
  override val project = module.project
  override val qualifiedNameResolveContext = fromModule(module)
  override val scope = module.moduleContentScope
}

class ProjectSdkContextAnchor(override val project: Project, override val sdk: Sdk?) : ContextAnchor {
  override val qualifiedNameResolveContext = sdk?.let { fromSdk(project, it) }
  override val scope = GlobalSearchScope.projectScope(project) //TODO: Check if project scope includes SDK
}


data class QNameResolveContext(
  val contextAnchor: ContextAnchor,
  /**
   * Used for language level etc
   */
  val sdk: Sdk? = contextAnchor.sdk,

  val evalContext: TypeEvalContext,
  /**
   * If not provided resolves against roots only. Resolved also against this folder otherwise
   */
  val folderToStart: VirtualFile? = null,
  /**
   * Use index, plain dirs with Py2 and so on. May resolve names unresolvable in other cases, but may return false results.
   */
  val allowInaccurateResult: Boolean = false
)

/**
 * @return qname part relative to root
 */
fun QualifiedName.getRelativeNameTo(root: QualifiedName): QualifiedName? {
  if (!toString().startsWith(root.toString())) {
    return null
  }
  return subQualifiedName(root.componentCount, componentCount)
}

/**
 * Resolves qname of any symbol to appropriate PSI element.
 * Shortcut for [getElementAndResolvableName]
 * @see [getElementAndResolvableName]
 */
fun QualifiedName.resolveToElement(context: QNameResolveContext, stopOnFirstFail: Boolean = false): PsiElement? {
  return getElementAndResolvableName(context, stopOnFirstFail)?.element
}


data class NameAndElement(val name: QualifiedName, val element: PsiElement)

/**
 * Resolves qname of any symbol to PSI element popping tail until element becomes resolved or only one time if stopOnFirstFail
 * @return element and longest name that was resolved successfully.
 * @see [resolveToElement]
 */
fun QualifiedName.getElementAndResolvableName(context: QNameResolveContext, stopOnFirstFail: Boolean = false): NameAndElement? {
  var currentName = QualifiedName.fromComponents(this.components)


  var element: PsiElement? = null


  var lastElement: String? = null
  var psiDirectory: PsiDirectory? = null

  var resolveContext = context.contextAnchor.qualifiedNameResolveContext?.copyWithMembers() ?: return null
  if (PythonSdkType.getLanguageLevelForSdk(context.sdk).isPy3K || context.allowInaccurateResult) {
    resolveContext = resolveContext.copyWithPlainDirectories()
  }

  if (context.folderToStart != null) {
    psiDirectory = PsiManager.getInstance(context.contextAnchor.project).findDirectory(context.folderToStart)
  }


  // Drill as deep, as we can
  while (currentName.componentCount > 0 && element == null) {
    if (psiDirectory != null) { // Resolve against folder
      element = resolveModuleAt(currentName, psiDirectory, resolveContext).firstOrNull()
    }

    if (element == null) { // Resolve against roots
      element = resolveQualifiedName(currentName, resolveContext).firstOrNull()
    }

    if (element != null || stopOnFirstFail) {
      break
    }
    lastElement = currentName.lastComponent!!
    currentName = currentName.removeLastComponent()
  }

  if (lastElement != null && element is PyClass) {
    // Drill in class

    //TODO: Support nested classes
    val method = element.findMethodByName(lastElement, true, context.evalContext)
    if (method != null) {
      return NameAndElement(currentName.append(lastElement), method)
    }

  }

  if (element == null && this.firstComponent != null && context.allowInaccurateResult) {
    // If name starts with file which is not in root nor in folders -- use index.
    val nameToFind = this.firstComponent!!
    val pyFile = PyModuleNameIndex.find(nameToFind, context.contextAnchor.project, false).firstOrNull() ?: return element

    val folder =
      if (pyFile.name == PyNames.INIT_DOT_PY) { // We are in folder
        pyFile.virtualFile.parent.parent
      }
      else {
        pyFile.virtualFile.parent
      }
    return getElementAndResolvableName(context.copy(folderToStart = folder))
  }
  return if (element != null) NameAndElement(currentName, element) else null
}