// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.ide.util.TreeChooser
import com.intellij.ide.util.gotoByName.GotoSymbolModel2
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.QualifiedName
import com.intellij.ui.TextAccessor
import com.intellij.util.ProcessingContext
import com.intellij.util.TextFieldCompletionProvider
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.jetbrains.extensions.getQName
import com.jetbrains.extenstions.ContextAnchor
import com.jetbrains.extenstions.QNameResolveContext
import com.jetbrains.extenstions.resolveToElement
import com.jetbrains.python.PyGotoSymbolContributor
import com.jetbrains.python.PyNames
import com.jetbrains.python.PyTreeChooserDialog
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.stubs.PyClassNameIndex
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex
import com.jetbrains.python.psi.types.PyModuleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Text field to enter python symbols and browse button (from [PyGotoSymbolContributor]).
 * Supports auto-completion for symbol fully qualified names inside of textbox (except first symbol).
 * @param filter lambda to filter symbols
 * @param startFromDirectory symbols resolved against module, but may additionally be resolved against this folder if provided like in [QNameResolveContext.folderToStart]
 *
 */
class PySymbolFieldWithBrowseButton(contextAnchor: ContextAnchor,
                                    filter: ((PsiElement) -> Boolean)? = null,
                                    startFromDirectory: (() -> VirtualFile)? = null) : TextAccessor, ComponentWithBrowseButton<TextFieldWithCompletion>(
  TextFieldWithCompletion(contextAnchor.project, PyNameCompletionProvider(contextAnchor, filter, startFromDirectory), "", true, true, true), null) {
  init {
    addActionListener {
      val dialog = PySymbolChooserDialog(contextAnchor.project, contextAnchor.scope, filter)
      dialog.showDialog()
      val element = dialog.selected
      if (element is PyQualifiedNameOwner) {
        childComponent.setText(element.qualifiedName)
      }
      if (element is PyFile) {
        childComponent.setText(element.getQName()?.toString())
      }
    }
  }

  override fun setText(text: String?) {
    childComponent.setText(text)
  }

  override fun getText() = childComponent.text
}

private fun PyType.getVariants(element: PsiElement): Array<LookupElement> =
  this.getCompletionVariants("", element, ProcessingContext()).filterIsInstance(LookupElement::class.java).toTypedArray()

private class PyNameCompletionProvider(private val contextAnchor: ContextAnchor,
                                       private val filter: ((PsiElement) -> Boolean)?,
                                       private val startFromDirectory: (() -> VirtualFile)? = null) : TextFieldCompletionProvider() {
  override fun addCompletionVariants(text: String, offset: Int, prefix: String, result: CompletionResultSet) {

    val evalContext = TypeEvalContext.userInitiated(contextAnchor.project, null)
    var name = QualifiedName.fromDottedString(text)
    val resolveContext = QNameResolveContext(contextAnchor, evalContext = evalContext, allowInaccurateResult = false,
                                             folderToStart = startFromDirectory?.invoke())
    var element = name.resolveToElement(resolveContext, stopOnFirstFail = true)

    if (element == null && name.componentCount > 1) {
      name = name.removeLastComponent()
      element = name.resolveToElement(resolveContext, stopOnFirstFail = true)
    }
    if (element == null) {
      return
    }

    val lookups: Array<LookupElement> = when (element) {
      is PyFile -> PyModuleType(element).getVariants(element)
      is PsiDirectory -> {
        val init = PyUtil.turnDirIntoInit(element) as? PyFile ?: return
        PyModuleType(init).getVariants(element) +
        element.children.filterIsInstance(PsiFileSystemItem::class.java)
          // For package we need all symbols in initpy and all filesystem children of this folder except initpy itself
          .filterNot { it.name == PyNames.INIT_DOT_PY }
          .map { LookupElementBuilder.create(it, it.virtualFile.nameWithoutExtension) }
      }
      is PyTypedElement -> {
        evalContext.getType(element)?.getVariants(element) ?: return
      }
      else -> return
    }
    result.addAllElements(lookups
                            .filter { it.psiElement != null }
                            .filter { filter?.invoke(it.psiElement!!) ?: true }
                            .map { LookupElementBuilder.create("$name.${it.lookupString}") })
  }
}

private class PySymbolChooserDialog(project: Project, scope: GlobalSearchScope, private val filter: ((PsiElement) -> Boolean)?)
  : PyTreeChooserDialog<PsiNamedElement>("Choose Symbol", PsiNamedElement::class.java,
                                         project,
                                         scope,
                                         TreeChooser.Filter { filter?.invoke(it) ?: true }, null) {
  override fun findElements(name: String, searchScope: GlobalSearchScope): Collection<PsiNamedElement> {
    return PyClassNameIndex.find(name, project, searchScope) + PyFunctionNameIndex.find(name, project, searchScope)
  }

  override fun createChooseByNameModel() = GotoSymbolModel2(project, arrayOf(PyGotoSymbolContributor()))
}