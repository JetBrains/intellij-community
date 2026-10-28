/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.projectView

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.PlatformIcons
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.classRoots
import com.jetbrains.python.codeInsight.typing.PyBundledStubs
import com.jetbrains.python.navigation.PyNavigationBundle

class PyBundledStubsNode(project: Project?, interpreter: PythonInterpreter, viewSettings: ViewSettings) : ProjectViewNode<PythonInterpreter>(project, interpreter, viewSettings) {
  companion object {
    fun create(project: Project?, interpreter: PythonInterpreter, viewSettings: ViewSettings): PyBundledStubsNode? =
      if (project != null && interpreter.classRoots(project).any { PyBundledStubs.isInside(it) })
        PyBundledStubsNode(project, interpreter, viewSettings)
      else null

  }

  override fun getChildren(): MutableCollection<PsiDirectoryNode> {
    val p = project ?: return mutableListOf()
    val psiManager = PsiManager.getInstance(p)
    return value.classRoots(p)
      .asSequence()
      .filter { PyBundledStubs.isInside(it) }
      .map { psiManager.findDirectory(it) }
      .filterNotNull()
      .map { PsiDirectoryNode(p, it, settings) }
      .toMutableList()
  }

  override fun contains(file: VirtualFile): Boolean = PyBundledStubs.isInside(file)

  override fun update(presentation: PresentationData) {
    presentation.presentableText = PyNavigationBundle.message("python.project.view.bundled.stubs")
    presentation.setIcon(PlatformIcons.LIBRARY_ICON)
  }
}