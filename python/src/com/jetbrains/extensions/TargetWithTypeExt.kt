/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.extensions

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.targetBasedConfiguration.TargetWithType
import com.jetbrains.python.run.targetBasedConfiguration.targetAsPsiElement
import com.jetbrains.python.run.targetBasedConfiguration.targetAsVirtualFile


/**
 * @see targetAsPsiElement
 */
fun TargetWithType.asPsiElement(configuration: AbstractPythonRunConfiguration<*>,
                                workingDirectory: VirtualFile?
                                = LocalFileSystem.getInstance().findFileByPath(configuration.getWorkingDirectorySafe())) =
  target?.let { targetAsPsiElement(targetType, it, configuration, workingDirectory) }


/**
 * @see targetAsVirtualFile
 */
fun TargetWithType.asVirtualFile() = target?.let { targetAsVirtualFile(targetType, it) }
