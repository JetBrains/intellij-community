package com.intellij.ide.starter.ide

import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.models.VMOptionsDiff
import java.nio.file.Path

internal abstract class InstalledBackedIDEStartConfig(
  private val patchedVMOptionsFile: Path,
  private val finalVMOptions: VMOptions
) : IDEStartConfig {
  init {
    finalVMOptions.writeIntelliJVmOptionFile(patchedVMOptionsFile)
  }

  final override fun vmOptionsDiff(): VMOptionsDiff = finalVMOptions.diffIntelliJVmOptionFile(patchedVMOptionsFile)
}