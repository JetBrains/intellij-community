// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.extensions

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyNames
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant
import com.jetbrains.python.run.targetBasedConfiguration.TargetWithVariant
import com.jetbrains.python.run.targetBasedConfiguration.targetAsPsiElement
import com.jetbrains.python.run.targetBasedConfiguration.targetAsVirtualFile


/**
 * @see targetAsPsiElement
 */
fun TargetWithVariant.asPsiElement(
  configuration: AbstractPythonRunConfiguration<*>,
  workingDirectory: VirtualFile? = LocalFileSystem.getInstance().findFileByPath(configuration.workingDirectorySafe)
): PsiElement? =
  target?.let { targetAsPsiElement(targetType, it, configuration, workingDirectory) }

/**
 * @see targetAsVirtualFile
 */
fun TargetWithVariant.asVirtualFile(): VirtualFile? = target?.let { targetAsVirtualFile(targetType, it) }

/**
 * Sanity check for "target" value. Does not resolve target, only check its syntax CUSTOM type is not checked.
 */
fun TargetWithVariant.isWellFormed(): Boolean = when (targetType) {
  PyRunTargetVariant.PYTHON -> target?.let { it.split(".").all { id -> PyNames.isIdentifier(id) } } ?: true
  PyRunTargetVariant.PATH -> !isBadPath(target)
  else -> true
}

private fun isBadPath(name: String?): Boolean = name.isNullOrEmpty() || "/" == name || "\\" == name
