package com.intellij.python.venv.evolution

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.sdk.backend.evolution.EvoSdk
import com.intellij.python.sdk.backend.evolution.EvoSelectSdkProvider
import com.intellij.python.sdk.backend.evolution.venvStyleSections
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.venv.icons.PythonVenvIcons
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.exists

/** Contributes the "pip" (virtualenv) node to the Evo interpreter widget. */
internal class VenvEvoSelectSdkProvider : EvoSelectSdkProvider {
  override val id: String get() = "pip"
  override val label: String get() = "pip"
  override val icon: Icon get() = PythonVenvIcons.VirtualEnv

  override suspend fun loadSections(module: Module): EvoLoadResultDto =
    EvoLoadResultDto.Ok(venvStyleSections(module, icon))

  override suspend fun parseModuleSdk(module: Module, sdk: Sdk): EvoSdk? {
    val binary = sdk.homePath?.let { Path.of(it) } ?: return null
    // Recognize a virtualenv by its `pyvenv.cfg` marker so we don't claim conda/poetry/hatch interpreters.
    val marker = binary.parent?.parent?.resolve("pyvenv.cfg")
    if (marker == null || !marker.exists()) return null
    return EvoSdk(icon = icon, name = binary.parent?.parent?.fileName?.toString(), pythonBinaryPath = binary)
  }
}
