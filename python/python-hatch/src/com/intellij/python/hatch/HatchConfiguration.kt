package com.intellij.python.hatch

import com.intellij.ide.util.PropertiesComponent
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.LocalEelApi
import com.intellij.platform.eel.provider.localEel
import com.jetbrains.python.Result
import com.jetbrains.python.sdk.ToolCommandExecutor
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import java.nio.file.Path

object HatchConfiguration {
  private const val PYCHARM_HATCH_LOCAL_EXECUTABLE_PATH_SETTING: String = "PyCharm.Hatch.Local.Executable.Path"

  private var localHatchExecutablePath: Path?
    get() = PropertiesComponent.getInstance().getValue(PYCHARM_HATCH_LOCAL_EXECUTABLE_PATH_SETTING)?.let { Path.of(it) }
    set(value) = PropertiesComponent.getInstance().setValue(PYCHARM_HATCH_LOCAL_EXECUTABLE_PATH_SETTING, value?.toString())

  private val HATCH_TOOL = ToolCommandExecutor(
    "hatch",
    getToolPathFromSettings = { getValue(PYCHARM_HATCH_LOCAL_EXECUTABLE_PATH_SETTING) }
  )

  fun persistPathForTarget(eelApi: EelApi = localEel, hatchExecutablePath: Path?) {
    if (eelApi is LocalEelApi) localHatchExecutablePath = hatchExecutablePath
  }

  suspend fun <P : PathHolder> getOrDetectHatchExecutablePath(fileSystem: FileSystem<P>): Result<P, HatchError> {
    val path = HATCH_TOOL.getToolExecutable(fileSystem, null) ?: return Result.failure(HatchExecutableNotFoundHatchError(null))
    if (fileSystem.isLocal) {
      persistPathForTarget(localEel, Path.of(path.toString()))
    }
    return Result.success(path)
  }
}
