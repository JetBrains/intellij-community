// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.jetbrains.python

import com.intellij.ide.util.AbstractTreeClassChooserDialog
import com.intellij.ide.util.TreeChooser
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.stubs.PyClassNameIndex
import javax.swing.tree.DefaultMutableTreeNode

/**
 * @author traff
 * @author ilya.kazakevich
 */

abstract class PyTreeChooserDialog<T : PsiNamedElement>(title: String,
                                                        clazz: Class<T>,
                                                        project: Project,
                                                        scope: GlobalSearchScope,
                                                        classFilter: TreeChooser.Filter<T>?,
                                                        initialValue: T?)
  : AbstractTreeClassChooserDialog<T>(title, project, scope, clazz, classFilter, initialValue) {
  override fun getSelectedFromTreeUserObject(node: DefaultMutableTreeNode?): Nothing? = null

  override fun getClassesByName(name: String?, checkBoxState: Boolean, pattern: String?, searchScope: GlobalSearchScope?): MutableList<T> =
    findElements(name!!, searchScope!!).filter(filter::isAccepted).toMutableList()

  abstract fun findElements(name: String, searchScope: GlobalSearchScope): Collection<T>
}

class PyClassTreeChooserDialog(title: String, project: Project, scope: GlobalSearchScope, classFilter: TreeChooser.Filter<PyClass>?,
                               initialClass: PyClass?)
  : PyTreeChooserDialog<PyClass>(title, PyClass::class.java, project, scope, classFilter, initialClass) {

  override fun findElements(name: String, searchScope: GlobalSearchScope): Collection<PyClass> =
    PyClassNameIndex.find(name, project, searchScope.isSearchInLibraries)!!
}
