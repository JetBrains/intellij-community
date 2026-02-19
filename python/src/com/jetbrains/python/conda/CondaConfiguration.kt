package com.jetbrains.python.conda

import com.intellij.ide.util.PropertiesComponent
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.LocalEelApi
import com.intellij.platform.eel.provider.localEel
import java.nio.file.Path
import kotlin.io.path.pathString

private object CondaConfiguration {
  private const val PYCHARM_CONDA_FULL_LOCAL_PATH: String = "PYCHARM_CONDA_FULL_LOCAL_PATH"

  private var localCondaExecutablePath: Path?
    get() = PropertiesComponent.getInstance().getValue(PYCHARM_CONDA_FULL_LOCAL_PATH)?.let { Path.of(it) }
    set(value) = PropertiesComponent.getInstance().setValue(PYCHARM_CONDA_FULL_LOCAL_PATH, value?.pathString)

  fun getPersistedPathForTarget(eelApi: EelApi): Path? = when {
    eelApi is LocalEelApi -> localCondaExecutablePath
    else -> TODO("Only local target is currently supported")
  }

  fun persistPathForTarget(eelApi: EelApi, condaExecutablePath: Path?): Unit = when {
    eelApi is LocalEelApi -> localCondaExecutablePath = condaExecutablePath
    else -> TODO("Only local target is currently supported")
  }
}

fun saveLocalPythonCondaPath(condaPath: Path?): Unit = CondaConfiguration.persistPathForTarget(localEel, condaExecutablePath = condaPath)

fun loadLocalPythonCondaPath(): Path? = CondaConfiguration.getPersistedPathForTarget(localEel)
