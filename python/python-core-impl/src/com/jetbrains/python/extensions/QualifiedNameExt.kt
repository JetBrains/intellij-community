// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.extensions

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.resolve.*
import com.jetbrains.python.psi.stubs.PyModuleNameIndex
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.sdk.PySdkUtil
import java.util.*


interface ContextAnchor {
  val sdk: Sdk?
  val project: Project
  val qualifiedNameResolveContext: PyQualifiedNameResolveContext?
  val scope: GlobalSearchScope
  fun getRoots(): Array<VirtualFile> {
    return sdk?.rootProvider?.getFiles(OrderRootType.CLASSES) ?: emptyArray()
  }
}

class ModuleBasedContextAnchor(val module: Module) : ContextAnchor {
  override val sdk: Sdk? = module.getSdk()
  override val project: Project = module.project
  override val qualifiedNameResolveContext: PyQualifiedNameResolveContext = fromModule(module)
  override val scope: GlobalSearchScope = module.moduleContentScope
  override fun getRoots(): Array<VirtualFile> {
    val manager = ModuleRootManager.getInstance(module)
    return super.getRoots() + manager.contentRoots + manager.sourceRoots
  }
}

class ProjectSdkContextAnchor(override val project: Project, override val sdk: Sdk?) : ContextAnchor {
  override val qualifiedNameResolveContext: PyQualifiedNameResolveContext? = sdk?.let { fromSdk(project, it) }
  override val scope: GlobalSearchScope = GlobalSearchScope.projectScope(project) //TODO: Check if project scope includes SDK
  override fun getRoots(): Array<VirtualFile> {
    val manager = ProjectRootManager.getInstance(project)
    return super.getRoots() + manager.contentRoots + manager.contentSourceRoots
  }
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
  if (Collections.indexOfSubList(components, root.components) == -1) {
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
  if (PySdkUtil.getLanguageLevelForSdk(context.sdk).isPy3K || context.allowInaccurateResult) {
    resolveContext = resolveContext.copyWithPlainDirectories()
  }

  if (context.folderToStart != null) {
    psiDirectory = PsiManager.getInstance(context.contextAnchor.project).findDirectory(context.folderToStart)
  }


  // Drill as deep, as we can
  while (currentName.componentCount > 0 && element == null) {
    if (psiDirectory != null) { // Resolve against folder
      // There could be folder and module on the same level. Empty folder should be ignored in this case.
      element = resolveModuleAt(currentName, psiDirectory, resolveContext).filterNot {
        it is PsiDirectory && it.children.filterIsInstance<PyFile>().isEmpty()
      }.firstOrNull()
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
