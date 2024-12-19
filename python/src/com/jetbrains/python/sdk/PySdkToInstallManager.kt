// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.installer.BinaryInstallation
import com.jetbrains.python.sdk.installer.BinaryInstallerUsagesCollector
import com.jetbrains.python.sdk.installer.ResourceTypeBinaryInstaller
import java.nio.file.Path
import kotlin.io.path.absolutePathString

object PySdkToInstallManager {
  /**
   * Software Release Installer for Apple Software Package (pkg) files
   */
  class PkgBinaryInstaller : ResourceTypeBinaryInstaller(ResourceType.APPLE_SOFTWARE_PACKAGE) {
    override fun buildCommandLine(resource: Resource, path: Path): GeneralCommandLine {
      return ExecUtil.sudoCommand(
        GeneralCommandLine("installer", "-pkg", path.absolutePathString(), "-target", "/"),
        PySdkBundle.message("python.sdk.running.sudo.prompt", resource.fileName)
      )
    }
  }

  /**
   * Software Release Installer for Microsoft Window Executable (exe) files
   */
  class ExeBinaryInstaller : ResourceTypeBinaryInstaller(ResourceType.MICROSOFT_WINDOWS_EXECUTABLE) {
    override fun buildCommandLine(resource: Resource, path: Path): GeneralCommandLine {
      return GeneralCommandLine(path.absolutePathString(), "/repair", "/quiet", "InstallAllUsers=0")
    }
  }

  private val cpythonInstallers = listOf(ExeBinaryInstaller(), PkgBinaryInstaller())

  fun getAvailableVersionsToInstall(): Map<LanguageLevel, BinaryInstallation> {
    return getLanguageLevelInstallations().mapNotNull { (k, v) ->
      v.firstOrNull()?.let { k to it }
    }.toMap()
  }

  private fun getLanguageLevelInstallations(product: Product = Product.CPython): Map<LanguageLevel, List<BinaryInstallation>> {
    return SdksKeeper.pythonReleasesByLanguageLevel().mapValues { (_, releases) ->
      val releaseBinaries = releases
        .filter { it.product == product }
        .flatMap { release ->
          release.binaries?.filter { it.isCompatible() }?.map { release to it } ?: emptyList()
        }

      releaseBinaries.flatMap { (release, binary) ->
        cpythonInstallers.filter { it.canInstall(binary) }.map { installer ->
          BinaryInstallation(release, binary, installer)
        }
      }
    }
  }


  fun findInstalledSdk(languageLevel: LanguageLevel?,
                               project: Project?,
                               systemWideSdksDetector: () -> List<PyDetectedSdk>): PyDetectedSdk? {
    LOGGER.debug("Resetting system-wide sdks detectors")
    resetSystemWideSdksDetectors()

    return systemWideSdksDetector()
      .also { sdks ->
        LOGGER.debug { sdks.joinToString(prefix = "Detected system-wide sdks: ") { it.homePath ?: it.name } }
      }
      .filter {
        val detectedLevel = PythonSdkFlavor.getFlavor(it)?.let { flavor ->
          PythonSdkFlavor.getLanguageLevelFromVersionStringStatic(PythonSdkFlavor.getVersionStringStatic(it.homePath!!))
        }
        languageLevel?.equals(detectedLevel) ?: true
      }
      .also {
        BinaryInstallerUsagesCollector.logLookupEvent(
          project,
          Product.CPython,
          languageLevel.toString(),
          when (it.isNotEmpty()) {
            true -> BinaryInstallerUsagesCollector.LookupResult.FOUND
            false -> BinaryInstallerUsagesCollector.LookupResult.NOT_FOUND
          }
        )
      }
      .firstOrNull()
  }

}