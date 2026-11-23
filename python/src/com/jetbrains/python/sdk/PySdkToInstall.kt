// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.impl.installer.BinaryInstallerUsagesCollector
import com.intellij.python.community.impl.installer.PySdkToInstallManager
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.add.v2.InstallablePythonSdk
import com.jetbrains.python.sdk.installer.BinaryInstallation
import com.jetbrains.python.sdk.installer.installBinary
import com.jetbrains.python.sdk.installer.toResourcePreview
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.CalledInAny

internal val LOGGER: Logger = Logger.getInstance(PySdkToInstall::class.java)

/**
 * The Python versions that the IDE can install. The first call parses `sdks.json`.
 */
@RequiresBackgroundThread
@Internal
internal fun getSdksToInstall(): List<PySdkToInstall> {
  return PySdkToInstallManager.getAvailableVersionsToInstall().map {
    PySdkToInstall(it.value)
  }
}

/**
 * Generic PySdkToInstall. Compatible with all OS / CpuArch.
 */
@Internal
internal class PySdkToInstall(
  val installation: BinaryInstallation,
) : InstallablePythonSdk {
  override val name: String = installation.release.title

  /**
   * Customize [renderer], which is typically either [com.intellij.ui.ColoredListCellRenderer] or [com.intellij.ui.ColoredTreeCellRenderer].
   */
  @CalledInAny
  @Internal
  fun renderInList(renderer: SimpleColoredComponent) {
    renderer.append(name)
    val preview = installation.toResourcePreview()
    renderer.append(" ${preview.description}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)  // NON-NLS
    renderer.icon = AllIcons.Actions.Download
  }

  @RequiresEdt
  @Internal
  override fun install(): Result<Unit> = installBinary(installation, null) { }
}

/**
 * The Python of [languageLevel] that the system Python detection finds on the local machine. The detection runs again,
 * so it also finds a Python that was installed a moment ago. The IDE installs Python only on the local machine.
 */
@Internal
suspend fun findInstalledPython(languageLevel: LanguageLevel): PythonBinary? {
  val python = SystemPythonService().findSystemPythons(localEel, forceRefresh = true)
    .firstOrNull { it.pythonInfo.languageLevel == languageLevel }
  LOGGER.debug { "Installed Python of $languageLevel: ${python?.pythonBinary}" }
  BinaryInstallerUsagesCollector.logLookupEvent(
    null,
    Product.CPython,
    languageLevel.toString(),
    if (python != null) BinaryInstallerUsagesCollector.LookupResult.FOUND else BinaryInstallerUsagesCollector.LookupResult.NOT_FOUND,
  )
  return python?.pythonBinary
}
