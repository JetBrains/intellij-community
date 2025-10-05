package com.intellij.python.sdk.ui.evolution.sdk

import com.intellij.icons.AllIcons
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.python.sdk.ui.evolution.tool.conda.sdk.CondaEvoSdkProvider
import com.intellij.python.sdk.ui.evolution.tool.hatch.sdk.HatchEvoSdkProvider
import com.intellij.python.sdk.ui.evolution.tool.pip.sdk.VenvEvoSdkProvider
import com.intellij.python.sdk.ui.evolution.tool.pip.sdk.getPythonVersion
import com.intellij.python.sdk.ui.evolution.tool.poetry.sdk.PoetryEvoSdkProvider
import com.jetbrains.python.PythonBinary
import java.nio.file.Path
import javax.swing.Icon
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.toVersionOrNull
import kotlinx.coroutines.runBlocking
import kotlin.io.path.isExecutable
import com.jetbrains.python.errorProcessing.MessageError

fun Path.resolvePythonExecutable(): Path? {
  val possibleExecutablePaths: List<Path> = if (SystemInfo.isWindows) {
    listOf(Path.of("bin", "python.exe"))
  }
  else {
    listOf(Path.of("bin", "python"))
  }
  return possibleExecutablePaths.firstNotNullOfOrNull { executablePath ->
    this.resolve(executablePath).takeIf { it.isExecutable() }
  }
}

sealed class EvoError(message: String) : MessageError(message)

class EvoWarning(message: String) : EvoError(message)
class EvoCriticalError(message: String) : EvoError(message)


interface EvoSdkProvider {
  fun parsePySdk(module: Module, sdk: Sdk): EvoSdk?
  //fun buildPySdk(evoSdk: EvoSdk): Sdk
}


val EVO_SDK_PROVIDERS: List<EvoSdkProvider> = listOf(
  HatchEvoSdkProvider,
  CondaEvoSdkProvider,
  PoetryEvoSdkProvider,
  VenvEvoSdkProvider,
  UnsupportedProvider
)

fun String?.parsePythonVersion(): Version? = this?.trim()?.removePrefix("Python ")?.toVersionOrNull()

private object UnsupportedProvider : EvoSdkProvider {
  override fun parsePySdk(module: Module, sdk: Sdk): EvoSdk {
    return EvoSdk(
      name = sdk.name,
      pythonBinaryPath = sdk.homePath?.let { Path.of(it) },
      icon = AllIcons.Nodes.Unknown
    )
  }
}

enum class EvoSdkOption { SUDO }

@Suppress("UnstableApiUsage")
data class EvoSdk(
  val target: String = "local",
  val icon: Icon,
  val name: String?,
  val pythonBinaryPath: PythonBinary?,
  val options: List<EvoSdkOption> = emptyList(),
) {

  val pythonVersion: Version? by lazy {
    runBlocking {
      pythonBinaryPath?.getPythonVersion()
    }
  }

  fun getAddress(): @NlsSafe String {
    val schema = if (this.target != "local") "${this.target}://" else ""
    val name = this.name ?: ""
    val options = this.options.joinToString("") { option ->
      when (option) {
        EvoSdkOption.SUDO -> " --sudo"
      }
    }
    val address = "$schema$name$options"
    return address
  }
}
