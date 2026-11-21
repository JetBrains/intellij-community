/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.run.targetBasedConfiguration

import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.run.PythonRunConfigurationForm
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Types of target (symbol, path or custom) many python runners may have
 */
enum class PyRunTargetVariant(@Nls private val customName: String? = null) {
  PYTHON(PythonRunConfigurationForm.getModuleNameText()), PATH(PythonRunConfigurationForm.getScriptPathText()), CUSTOM;

  @Nls fun getCustomName(): String = customName ?: PythonRunConfigurationForm.getCustomNameText()
}

/**
 * Converts target to file if possible
 */
@ApiStatus.Internal
fun targetAsVirtualFile(targetType: PyRunTargetVariant, target: String): VirtualFile? {
  if (targetType == PyRunTargetVariant.PATH) {
    return StandardFileSystems.local().findFileByPath(target)
  }
  return null
}


/**
 * Implement it to obtain extension methods for [com.jetbrains.python.extensions.targetAsPsiElement]
 * and [targetAsVirtualFile]
 */
interface TargetWithVariant {
  val target: String?
  /**
   * Do not rename this field: its may be used as key in serialized configurations
   */
  val targetType: PyRunTargetVariant
}
