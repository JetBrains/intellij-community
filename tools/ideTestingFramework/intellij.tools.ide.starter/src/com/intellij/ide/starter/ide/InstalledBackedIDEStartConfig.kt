// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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