/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.extensions

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant
import com.jetbrains.python.run.targetBasedConfiguration.TargetWithVariant
import com.jetbrains.python.run.targetBasedConfiguration.targetAsPsiElement
import com.jetbrains.python.run.targetBasedConfiguration.targetAsVirtualFile


/**
 * @see targetAsPsiElement
 */
fun TargetWithVariant.asPsiElement(configuration: AbstractPythonRunConfiguration<*>,
                                   workingDirectory: VirtualFile?
                                   = LocalFileSystem.getInstance().findFileByPath(configuration.getWorkingDirectorySafe())): PsiElement? =
  target?.let { targetAsPsiElement(targetType, it, configuration, workingDirectory) }


/**
 * @see targetAsVirtualFile
 */
fun TargetWithVariant.asVirtualFile(): VirtualFile? = target?.let { targetAsVirtualFile(targetType, it) }

/**
 * Sanity check for "target" value. Does not resolve target, only check its syntax
 * CUSTOM type is not checked.
 */
fun TargetWithVariant.isWellFormed(): Boolean = when (targetType) {
  PyRunTargetVariant.PYTHON -> Regex("^[a-zA-Z0-9._]+[a-zA-Z0-9_]$").matches(target ?: "")
  PyRunTargetVariant.PATH -> !VfsUtil.isBadName(target)
  else -> true
}