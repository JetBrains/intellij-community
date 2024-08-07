// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.conda

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.Product
import com.jetbrains.python.sdk.Resource
import com.jetbrains.python.sdk.ResourceType
import com.jetbrains.python.sdk.SdksKeeper
import com.jetbrains.python.sdk.installer.*
import java.nio.file.Path
import kotlin.io.path.absolutePathString

object CondaInstallManager {

  class WindowsInstaller(private val installPath: String) : ResourceTypeBinaryInstaller(ResourceType.MICROSOFT_WINDOWS_EXECUTABLE) {
    /**
     * Miniconda3-{version}-Windows-x86_64.exe {options}
     *
     * /InstallationType=[JustMe|AllUsers] - Default is JustMe.
     * /AddToPath=[0|1] - Default is 0.
     * /RegisterPython=[0|1] - Make this the system’s default Python.
     *                         Default is 0.
     * /S - Install in silent mode.
     * /D=<installation path> - Destination installation path.
     *                          Must be the last argument.
     *                          Do not wrap in quotation marks.
     *                          Required if installing in silent mode.
     */
    override fun buildCommandLine(resource: Resource, path: Path): GeneralCommandLine {
      return GeneralCommandLine(
        path.absolutePathString(),
        "/InstallationType=JustMe",
        "/AddToPath=0",
        "/RegisterPython=0",
        "/S",
        "/D=${installPath}"
      )
    }
  }

  private val shellScriptInstaller = object : ResourceTypeBinaryInstaller(ResourceType.SHELL_SCRIPT) {
    /**
     * os: MacOSX / Linux
     * cpuArch: x86_64 / (arm64 | aarch64) / s390x / ppc64le
     * usage: ./Miniconda3-{version}-{os}-{cpuArch}.sh {options}
     *
     * -b           run install in batch mode (without manual intervention),
     *              it is expected the license terms (if any) are agreed upon
     * -f           no error if install prefix already exists
     * -h           print this help message and exit
     * -p PREFIX    install prefix, defaults to /Users/UserName/miniconda3, must not contain spaces.
     * -s           skip running pre/post-link/install scripts
     * -u           update an existing installation
     * -t           run package tests after installation (may install conda-build)
     */
    override fun buildCommandLine(resource: Resource, path: Path): GeneralCommandLine {
      if (!path.toFile().setExecutable(true, true)) {
        throw MakeShellScriptExecutableException(path)
      }
      return GeneralCommandLine(path.absolutePathString(), "-b", "-u")
    }
  }

  private val applePkgInstaller = object : ResourceTypeBinaryInstaller(ResourceType.APPLE_SOFTWARE_PACKAGE) {
    override fun buildCommandLine(resource: Resource, path: Path): GeneralCommandLine {
      return GeneralCommandLine("installer", "-pkg", path.absolutePathString(), "-target", "CurrentUserHomeDirectory")
    }
  }

  private val productInstallers = mapOf(
    Product.Miniconda to listOf(WindowsInstaller("%UserProfile%\\miniconda3"), shellScriptInstaller, applePkgInstaller),
    Product.Anaconda to listOf(WindowsInstaller("%UserProfile%\\anaconda3"), shellScriptInstaller, applePkgInstaller),
  )

  @RequiresEdt
  fun installLatest(project: Project?, product: Product = Product.Miniconda) {
    val latestRelease = SdksKeeper.condaReleases(product).firstOrNull()
                        ?: error("There is no available conda releases of the product $product")
    val installers = productInstallers.getOrElse(latestRelease.product) { emptyList() }
    val installations = latestRelease.selectInstallations(installers)

    val unknownLevel = LanguageLevel.SUPPORTED_LEVELS.first()
    val installationWithHighestPython = installations.maxByOrNull { bi ->
      LanguageLevel.SUPPORTED_LEVELS.lastOrNull { level ->
        bi.binary.tags?.let { tags -> level.name in tags } ?: false
      } ?: unknownLevel
    }

    if (installationWithHighestPython != null) {
      installBinary<Nothing>(installationWithHighestPython, project)
    }
    else {
      error("There is no supported conda installation for the release $latestRelease")
    }
  }
}