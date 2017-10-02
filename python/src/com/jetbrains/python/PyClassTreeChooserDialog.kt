/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python

import com.google.common.collect.Lists
import com.intellij.ide.util.AbstractTreeClassChooserDialog
import com.intellij.ide.util.TreeChooser
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.stubs.PyClassNameIndex

import javax.swing.tree.DefaultMutableTreeNode

/**
 * @author traff
 */
class PyClassTreeChooserDialog(title: String, project: Project, scope: GlobalSearchScope, classFilter: TreeChooser.Filter<PyClass>?,
                               initialClass: PyClass?) : AbstractTreeClassChooserDialog<PyClass>(title, project, scope, PyClass::class.java,
                                                                                                 classFilter, initialClass) {

  override fun getClassesByName(name: String,
                                checkBoxState: Boolean,
                                pattern: String,
                                searchScope: GlobalSearchScope): List<PyClass> {
    val classes = PyClassNameIndex.find(name, project, searchScope.isSearchInLibraries)
    val result = Lists.newArrayList<PyClass>()
    for (c in classes) {
      if (filter.isAccepted(c)) {
        result.add(c)
      }
    }

    return result
  }

  override fun getSelectedFromTreeUserObject(node: DefaultMutableTreeNode): PyClass? {
    return null
  }
}
