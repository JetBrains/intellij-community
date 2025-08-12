// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.wsl.WslPath
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.Strings
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelResult.Error
import com.intellij.platform.eel.EelResult.Ok
import com.intellij.platform.eel.fs.EelFileInfo
import com.intellij.platform.eel.fs.stat
import com.intellij.platform.eel.isMac
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.text.nullize
import com.intellij.util.xmlb.annotations.Property
import org.jetbrains.plugins.terminal.settings.TerminalLocalOptions
import java.nio.file.Files
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

@Service(Service.Level.PROJECT)
@State(name = "TerminalProjectNonSharedOptionsProvider", storages = [(Storage(StoragePathMacros.WORKSPACE_FILE))])
class TerminalProjectOptionsProvider(val project: Project) : PersistentStateComponent<TerminalProjectOptionsProvider.State> {

  private val state = State()

  override fun getState(): State {
    return state
  }

  override fun loadState(newState: State) {
    state.startingDirectory = newState.startingDirectory
    state.shellPath = newState.shellPath
    state.envDataOptions = newState.envDataOptions
  }

  fun getEnvData(): EnvironmentVariablesData {
    return state.envDataOptions.get()
  }

  fun setEnvData(envData: EnvironmentVariablesData) {
    state.envDataOptions.set(envData)
  }

  class State {
    var startingDirectory: String? = null
    var shellPath: String? = null
    @get:Property(surroundWithTag = false, flat = true)
    var envDataOptions: EnvironmentVariablesDataOptions = EnvironmentVariablesDataOptions()
  }

  var startingDirectory: String? by ValueWithDefault(state::startingDirectory) { defaultStartingDirectory }

  val defaultStartingDirectory: String?
    get() {
      var directory: String? = null
      for (customizer in LocalTerminalCustomizer.EP_NAME.extensions) {
        try {
          directory = customizer.getDefaultFolder(project)
          if (directory != null) {
            break
          }
        }
        catch (e: Exception) {
          LOG.error("Exception during getting default folder", e)
        }
      }
      if (directory == null) {
        directory = getDefaultWorkingDirectory()
      }
      return if (directory != null) FileUtil.toSystemDependentName(directory) else null
    }

  private fun getDefaultWorkingDirectory(): String? {
    return project.guessProjectDir()?.canonicalPath
  }

  var shellPath: String
    get() {
      return runBlockingMaybeCancellable {
        shellPathWithoutDefault ?: defaultShellPath()
      }
    }
    set(value) {
      return runBlockingMaybeCancellable {
        shellPathWithoutDefault = Strings.nullize(value, defaultShellPath())
      }
    }

  internal var shellPathWithoutDefault: String?
    get() {
      val workingDirectory = startingDirectory
      val shellPath = when {
        isProjectLevelShellPath(workingDirectory) && TrustedProjects.isProjectTrusted(project) -> state.shellPath
        else -> TerminalLocalOptions.getInstance().shellPath
      }
      return shellPath.nullize(nullizeSpaces = true)
    }
    set(value) {
      val valueToStore = value.nullize(nullizeSpaces = true)
      val workingDirectory = startingDirectory
      if (isProjectLevelShellPath(workingDirectory)) {
        state.shellPath = valueToStore
      }
      else {
        TerminalLocalOptions.getInstance().shellPath = valueToStore
      }
    }

  private fun isProjectLevelShellPath(workingDirectory: String?): Boolean {
    val eelDescriptor = toEelDescriptor(workingDirectory)
    return eelDescriptor !== LocalEelDescriptor
  }

  private fun toEelDescriptor(workingDirectory: String?): EelDescriptor {
    val path = workingDirectory?.let {
      NioFiles.toPath(it)
    }
    return path?.getEelDescriptor() ?: LocalEelDescriptor
  }

  suspend fun defaultShellPath(): String = findDefaultShellPath { startingDirectory }

  private suspend fun findDefaultShellPath(workingDirectory: () -> String?): String {
    if (shouldUseEelApi()) {
      return findDefaultShellPath(toEelDescriptor(workingDirectory()))
    }
    if (SystemInfo.isWindows) {
      val wslDistributionName = findWslDistributionName(workingDirectory())
      if (wslDistributionName != null) {
        return "wsl.exe --distribution $wslDistributionName"
      }
    }
    val shell = System.getenv("SHELL")?.let { NioFiles.toPath(it) }
    if (shell != null && Files.exists(shell)) {
      return shell.toString()
    }
    if (SystemInfo.isUnix) {
      val bashPath = NioFiles.toPath("/bin/bash")
      if (bashPath != null && Files.exists(bashPath)) {
        return bashPath.toString()
      }
      return "/bin/sh"
    }
    return "powershell.exe"
  }

  private fun findWslDistributionName(directory: String?): String? {
    return if (directory == null) null else WslPath.parseWindowsUncPath(directory)?.distributionId
  }

  private suspend fun findDefaultShellPath(eelDescriptor: EelDescriptor): String {
    if (eelDescriptor.osFamily.isWindows) {
      return "powershell.exe"
    }
    val eelApi = eelDescriptor.toEelApi()
    val envs = if (eelDescriptor == LocalEelDescriptor) System.getenv() else eelApi.exec.fetchLoginShellEnvVariables()
    val candidates = listOfNotNull(
      envs["SHELL"],
      "/bin/zsh".takeIf { eelApi.platform.isMac },
      "/bin/bash"
    ).distinct()
    return candidates.firstOrNull { isFile(it, eelApi) } ?: "/bin/sh"
  }

  private suspend fun isFile(absoluteFilePath: String, eelApi: EelApi): Boolean {
    val path = try {
      EelPath.parse(absoluteFilePath, eelApi.descriptor)
    }
    catch (_: Exception) {
      return false
    }
    return when (val result = eelApi.fs.stat(path).resolveAndFollow().eelIt()) {
      is Ok -> result.value.type is EelFileInfo.Type.Regular
      is Error -> false
    }
  }

  companion object {
    private val LOG = Logger.getInstance(TerminalProjectOptionsProvider::class.java)

    @JvmStatic
    fun getInstance(project: Project): TerminalProjectOptionsProvider {
      return project.getService(TerminalProjectOptionsProvider::class.java)
    }
  }

}

class ValueWithDefault<T : String?>(val prop: KMutableProperty0<T?>, val default: () -> T) {
  operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
    val value : T? = prop.get()
    return if (value !== null) value else default()
  }

  operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    prop.set(if (value == default() || value.isNullOrEmpty()) null else value)
  }
}
