package com.intellij.python.hatch

import com.intellij.ide.util.PropertiesComponent
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.LocalEelApi
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.where
import com.intellij.python.hatch.runtime.getHatchCommand
import com.jetbrains.python.Result
import java.nio.file.Path
import kotlin.io.path.isExecutable

object HatchConfiguration {
  private const val PYCHARM_HATCH_LOCAL_EXECUTABLE_PATH_SETTING: String = "PyCharm.Hatch.Local.Executable.Path"

  private var localHatchExecutablePath: Path?
    get() = PropertiesComponent.getInstance().getValue(PYCHARM_HATCH_LOCAL_EXECUTABLE_PATH_SETTING)?.let { Path.of(it) }
    set(value) = PropertiesComponent.getInstance().setValue(PYCHARM_HATCH_LOCAL_EXECUTABLE_PATH_SETTING, value?.toString())

  fun getPersistedPathForTarget(eelApi: EelApi = localEel): Path? = when {
    eelApi is LocalEelApi -> localHatchExecutablePath
    else -> null
  }

  fun persistPathForTarget(eelApi: EelApi = localEel, hatchExecutablePath: Path?) {
    if (eelApi is LocalEelApi) localHatchExecutablePath = hatchExecutablePath
  }

  suspend fun getOrDetectHatchExecutablePath(eelApi: EelApi = localEel): Result<Path, HatchError> {
    val path = getPersistedPathForTarget(eelApi) ?: run {
      detectHatchExecutable(eelApi)?.also { persistPathForTarget(eelApi, it) }
    }

    val result = when {
      path?.isExecutable() != true -> Result.failure(HatchExecutableNotFoundHatchError(path))
      else -> Result.success(path)
    }

    return result
  }

  suspend fun detectHatchExecutable(eelApi: EelApi): Path? {
    val hatchCommand = eelApi.getHatchCommand()
    val hatchPath = eelApi.exec.where(hatchCommand)?.asNioPath()
    return hatchPath
  }
}

