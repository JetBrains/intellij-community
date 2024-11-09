// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.RunCanceledByUserException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.python.packaging.PyExecutionException
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * Returns the string representation of the Python executable ("py" on Windows or "python" on Unix-like OS) based on the current system.
 *
 * @return the string representation of the Python executable
 */
@Internal
fun getPythonExecutableString() = if (SystemInfo.isWindows) "py" else "python"


@Service(Service.Level.APP)
internal class PackageInstallationFilesService {
  val ktorClient = HttpClient(CIO) {
    install(HttpTimeout)
  }
  val urlToFilePathMap = mutableMapOf<URL, Path>()
}

/**
 * Installs a package with Python using the given URL and Python executable.
 *
 * @param [url] The [URL] from which to download the package.
 * @param pythonExecutable The path to the Python executable (could be "py" or "python").
 * @return A [Result] object that represents the [ProcessOutput] of the installation command.
 */
internal suspend fun installPackageWithPython(url: URL, pythonExecutable: String): Result<String> {
  val installationFile = downloadFile(url).getOrThrow()
  val command = GeneralCommandLine(pythonExecutable, installationFile.absolutePathString())
  return runCommandLine(command)
}

/**
 * Downloads a file from the specified URL.
 *
 * @param[url] The [URL] from which to download the file.
 * @return A [Result] object that represents the [Path] to the downloaded file.
 */
internal suspend fun downloadFile(url: URL): Result<Path> {
  val installationService = service<PackageInstallationFilesService>()
  installationService.urlToFilePathMap[url]?.let { return Result.success(it) }
  return withContext(Dispatchers.IO) {
    val installationFile = FileUtil.createTempFile("_installation_file.py", null)
    installationService.ktorClient.get(url).bodyAsChannel().copyAndClose(installationFile.writeChannel())
    installationService.urlToFilePathMap[url] = installationFile.toPath()

    Result.success(installationFile.toPath())
  }
}

/**
 * Checks if a package is installed by running the specified command(s) with the "--version"
 * argument and checking the success of the command.
 *
 * @param [commands] The commands to execute. These commands should include the package and any required arguments.
 * @return true if the package is installed, false otherwise
 */
@Internal
suspend fun isPackageInstalled(vararg commands: String): Boolean {
  val command = GeneralCommandLine(*commands, "--version")
  return runCommandLine(command).isSuccess
}

/**
 * Installs an executable via pip.
 *
 * @param [executableName] The name of the executable to install.
 * @param [pythonExecutable] The path to the Python executable (could be "py" or "python").
 * @param [isUserSitePackages] Whether to install the executable in the user's site packages directory. Defaults to true.
 */
@Internal
suspend fun installExecutableViaPip(
  executableName: String,
  pythonExecutable: String,
  isUserSitePackages: Boolean = true,
) {
  val commandList = mutableListOf(pythonExecutable, "-m", "pip", "install", executableName)
  if (isUserSitePackages) {
    commandList.add("--user")
  }

  runCommandLine(GeneralCommandLine(commandList)).getOrThrow()
}

internal suspend fun installPipIfNeeded(pythonExecutable: String) {
  if (!isPackageInstalled(pythonExecutable, "-m", "pip") && !isPackageInstalled("pip")) {
    installPackageWithPython(URL("https://bootstrap.pypa.io/get-pip.py"), pythonExecutable).getOrThrow()
  }
}

/**
 * Installs an executable via a Python script.
 *
 * @param [scriptPath] The [Path] to the Python script used for installation.
 * @param [pythonExecutable] The path to the Python executable (could be "py" or "python").
 *
 * @throws [RunCanceledByUserException] if the user cancels the command execution.
 * @throws [PyExecutionException] if the command execution fails.
 */
@Internal
suspend fun installExecutableViaPythonScript(scriptPath: Path, pythonExecutable: String) =
  runCommandLine(GeneralCommandLine(pythonExecutable, scriptPath.absolutePathString())).getOrThrow()