package com.jetbrains.python.sdk.configuration

import com.jetbrains.python.PythonBinary
import com.jetbrains.python.project.PyProject
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PyProjectTomlConfigurationExtension : PyProjectSdkConfigurationExtension {

  suspend fun createSdkWithoutPyProjectTomlChecks(pyProject: PyProject, venvs: List<PythonBinary>): CreateInterpreterInfo?
}
