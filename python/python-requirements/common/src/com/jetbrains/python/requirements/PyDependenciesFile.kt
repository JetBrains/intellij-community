package com.jetbrains.python.requirements

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.requirements.common.icons.PythonRequirementsCommonIcons
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
interface PyDependenciesFile {
  val virtualFile: VirtualFile
  val icon: Icon
}


@ApiStatus.Internal
data class SetupPyFile(override val virtualFile: VirtualFile) : PyDependenciesFile {
  override val icon: Icon = PythonRequirementsCommonIcons.Requirements
}

@ApiStatus.Internal
data class RequirementsTxtFile(override val virtualFile: VirtualFile) : PyDependenciesFile {
  override val icon: Icon = PythonRequirementsCommonIcons.Requirements
}