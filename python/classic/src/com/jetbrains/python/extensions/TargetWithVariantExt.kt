// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.extensions

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.targetBasedConfiguration.PyRunTargetVariant
import com.jetbrains.python.run.targetBasedConfiguration.TargetWithVariant
import com.jetbrains.python.run.targetBasedConfiguration.targetAsVirtualFile
import org.jetbrains.annotations.ApiStatus


/**
 * Converts target to PSI element if possible resolving it against roots and working directory
 */
fun targetAsPsiElement(targetType: PyRunTargetVariant,
                       target: String,
                       configuration: AbstractPythonRunConfiguration<*>,
                       workingDirectory: VirtualFile? = StandardFileSystems.local().findFileByPath(
                         configuration.getWorkingDirectorySafe()))
  : PsiElement? {
  if (targetType == PyRunTargetVariant.PYTHON) {
    val module = configuration.module ?: return null
    val context = TypeEvalContext.userInitiated(configuration.getProject(), null)

    return runReadAction {
      QualifiedName.fromDottedString(target)
        .resolveToElement(QNameResolveContext(ModuleBasedContextAnchor(module), configuration.pythonInterpreter, context, workingDirectory, true))
    }
  }
  return null
}

/**
 * @see targetAsPsiElement
 */
fun TargetWithVariant.asPsiElement(
  configuration: AbstractPythonRunConfiguration<*>,
  workingDirectory: VirtualFile? = StandardFileSystems.local().findFileByPath(configuration.workingDirectorySafe)
): PsiElement? =
  target?.let { targetAsPsiElement(targetType, it, configuration, workingDirectory) }

/**
 * @see targetAsVirtualFile
 */
@ApiStatus.Internal
fun TargetWithVariant.asVirtualFile(): VirtualFile? = target?.let { targetAsVirtualFile(targetType, it) }

/**
 * Sanity check for "target" value. Does not resolve target, only check its syntax CUSTOM type is not checked.
 */
@ApiStatus.Internal
fun TargetWithVariant.isWellFormed(): Boolean = when (targetType) {
  PyRunTargetVariant.PYTHON -> target?.let { it.split(".").all { id -> PyNames.isIdentifier(id) } } ?: true
  PyRunTargetVariant.PATH -> !isBadPath(target)
  else -> true
}

private fun isBadPath(name: String?): Boolean = name.isNullOrEmpty() || "/" == name || "\\" == name
