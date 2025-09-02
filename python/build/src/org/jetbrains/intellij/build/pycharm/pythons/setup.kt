// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm.pythons

import com.intellij.util.system.OS
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.downloadFileToCacheLocation
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

private val os: String = System.getProperty("os.name").lowercase().let { name ->
  when {
    name.startsWith("windows") -> "windows"
    name.startsWith("mac") -> "mac"
    name.startsWith("linux") -> "linux"
    else -> name
  }
}

private val arch: String = System.getProperty("os.arch").lowercase().let { arch ->
  when (arch) {
    "x86_64", "amd64" -> "x64"
    "aarch64", "arm64" -> "aarch64"
    else -> arch
  }
}


private const val pyEnvUrl = "https://github.com/pyenv/pyenv/archive/master.zip"
private const val pyEnvWinUrl = "https://raw.githubusercontent.com/pyenv-win/pyenv-win/master/pyenv-win/install-pyenv-win.ps1"
private val pipOptions = arrayOf("--trusted-host", "pypi.python.org", "--trusted-host", "pypi.org", "--trusted-host", "files.pythonhosted.org")
val Python.directoryName: String
  get() = "$pyenvDefinition-$name-$revision-$os-$arch"


private val buildRoot = BuildPaths.COMMUNITY_ROOT

private val pyEnvHome = buildRoot.communityRoot / "out" / "pyenv"
private val pythonsHome = buildRoot.communityRoot / "out" / "pythons"

fun runCommand(vararg args: String, env: Map<String, String> = emptyMap()) {
  println("Executing: ${args.joinToString(" ")}")
  val process = ProcessBuilder(*args)
    .apply { environment().putAll(env) }
    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
    .redirectError(ProcessBuilder.Redirect.INHERIT)
    .start()
  val isFinished = process.waitFor(60, TimeUnit.MINUTES)
  if (!isFinished) throw RuntimeException("Timeout on execute")
  if (process.exitValue() != 0) throw RuntimeException("Non-Zero exit code")
}


private val isWindows = OS.CURRENT == OS.Windows

suspend fun setupPyEnv() {
  if (pyEnvHome.exists()) return
  println("*** Setup pyenv ***")
  val pyEnv: Path
  if (isWindows) {
    val pyEnvPath = downloadFileToCacheLocation(pyEnvWinUrl, buildRoot)
    runCommand("powershell", "-ExecutionPolicy", "Bypass", "-File", "$pyEnvPath",
               env = mapOf("USERPROFILE" to pyEnvHome.toString()))
  } else {
    val pyEnvPath = downloadFileToCacheLocation(pyEnvUrl, buildRoot)
    val extracted = BuildDependenciesDownloader.extractFileToCacheLocation(buildRoot, pyEnvPath)
    pyEnv = extracted / "pyenv-master"
    val installSh = pyEnv / "plugins" / "python-build" / "install.sh"
    pyEnv.createDirectories()
    runCommand(installSh.toString(), env = mapOf("PREFIX" to pyEnvHome.toString()))
  }
}

@OptIn(ExperimentalPathApi::class)
suspend fun Python.installUsingPyEnv(): Path {
  setupPyEnv()
  val output = pythonsHome / directoryName
  if (output.exists()) return output
  if (isWindows) {
    if (pyenvDefinition.lowercase().contains("miniconda")) {
      val miniCondaFile = downloadFileToCacheLocation("https://repo.anaconda.com/miniconda/$pyenvDefinition-Windows-x86_64.exe", buildRoot)
      runCommand(miniCondaFile.toString(), "/InstallationType=JustMe", "/AddToPath=0", "/RegisterPython=0", "/S", "/D=$output")
    } else {
      runCommand((pyEnvHome / ".pyenv" / "pyenv-win" / "bin" / "pyenv.bat").toString(), "install", pyenvDefinition)
      (pyEnvHome / ".pyenv" / "pyenv-win" / "versions"/ pyenvDefinition).copyToRecursively(output, followLinks = false)
    }
  } else {
    runCommand((pyEnvHome / "bin" / "python-build").toString(), pyenvDefinition, output.toString())
  }
  return output
}

suspend fun Python.installPackages() {
  if (packages.isEmpty()) return
  val output = pythonsHome / directoryName
  if ((output / "condabin").exists()) {
    val conda = output / "condabin" / if (isWindows) "conda.bat" else "conda"
    runCommand(conda.toString(), "install", "-y", *packages.toTypedArray())
  } else {
    val pip = if (isWindows) output / "Scripts" / "pip.exe" else output / "bin" / "pip"
    runCommand(pip.toString(), "install", *pipOptions, *packages.toTypedArray())
  }
}

suspend fun Python.build() {
  val output = installUsingPyEnv()
  installPackages()
  runCommand("tar", "cfz", (buildRoot.communityRoot / "out" / "pythons" / "$directoryName.tar.gz").toString(), output.toString())
}
