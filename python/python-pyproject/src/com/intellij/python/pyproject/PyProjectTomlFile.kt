package com.intellij.python.pyproject

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.pyproject.icons.PythonPyprojectIcons
import com.jetbrains.python.requirements.PyDependenciesFile
import javax.swing.Icon


data class PyProjectTomlFile(override val virtualFile: VirtualFile) : PyDependenciesFile {
  override val icon: Icon = PythonPyprojectIcons.Toml
}
