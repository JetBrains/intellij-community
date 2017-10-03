// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.jetbrains

import com.intellij.ide.util.TreeChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ThreeState
import com.jetbrains.python.PyTreeChooserDialog
import com.jetbrains.python.psi.PyQualifiedNameOwner
import com.jetbrains.python.psi.stubs.PyClassNameIndex
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.testing.isTestElement


class PyTextFieldWithBrowseButton : TextFieldWithBrowseButton.NoPathCompletion() {
  fun switchToFileMode(descriptor: FileChooserDescriptor, project: Project) {
    removeListeners()
    addBrowseFolderListener("Choose File or Folder", null, project, descriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT)
    FileChooserFactory.getInstance().installFileCompletion(childComponent, descriptor, true, project)
  }

  fun switchToPythonMode(module: Module) {
    removeListeners()
    addActionListener {
      val dialog = PySymbolChooserDialog(module)
      dialog.showDialog()
      val element = dialog.selected
      if (element is PyQualifiedNameOwner) {
        textField.text = element.qualifiedName
      }
    }
  }

  private fun removeListeners() {
    button.actionListeners.forEach { button.removeActionListener(it) }
    textField.keyListeners.forEach { textField.removeKeyListener(it) }
  }
}

private class PySymbolChooserDialog(module: Module) : PyTreeChooserDialog<PsiNamedElement>("Test", PsiNamedElement::class.java,
                                                                                           module.project,
                                                                                           GlobalSearchScope.moduleRuntimeScope(module, true),
                                                                                           MyFilter(module.project), null) {
  private class MyFilter(private val project: Project) : TreeChooser.Filter<PsiNamedElement> {
    //TODO: May be slow
    override fun isAccepted(element: PsiNamedElement) = isTestElement(element, ThreeState.UNSURE,
                                                                      TypeEvalContext.userInitiated(project, null))
  }


  override fun findElements(name: String, searchScope: GlobalSearchScope): Collection<PsiNamedElement> {
    return PyClassNameIndex.find(name, project, searchScope) + PyFunctionNameIndex.find(name, project, searchScope)
  }
}