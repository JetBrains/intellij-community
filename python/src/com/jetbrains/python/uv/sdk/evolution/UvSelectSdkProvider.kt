package com.jetbrains.python.uv.sdk.evolution

import com.intellij.openapi.module.Module
import com.intellij.python.sdk.backend.evolution.EvoSelectSdkProvider
import com.intellij.python.sdk.backend.evolution.evoWarning
import com.intellij.python.sdk.backend.evolution.venvStyleSections
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.uv.backend.PyUvBundle
import com.intellij.python.uv.common.icons.PythonUvCommonIcons
import com.jetbrains.python.sdk.uv.impl.getUvExecutableLocal
import javax.swing.Icon

internal class UvSelectSdkProvider : EvoSelectSdkProvider {
  override val id: String get() = "uv"
  override val label: String get() = "uv"
  override val icon: Icon get() = PythonUvCommonIcons.UV

  override suspend fun loadSections(module: Module): EvoLoadResultDto {
    getUvExecutableLocal() ?: return evoWarning(PyUvBundle.message("evolution.uv.executable.is.not.found"))
    return EvoLoadResultDto.Ok(venvStyleSections(module, icon))
  }
}
