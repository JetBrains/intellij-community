/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.run

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.extenstions.ModuleBasedContextAnchor
import com.jetbrains.extenstions.QNameResolveContext
import com.jetbrains.extenstions.resolveToElement
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Types of target (symbol, path or custom) many python runners may have
 */
enum class PyTargetType(private val customName: String? = null) {
  PYTHON(PythonRunConfigurationForm.MODULE_NAME), PATH(PythonRunConfigurationForm.SCRIPT_PATH), CUSTOM;

  fun getCustomName() = customName ?: name.toLowerCase().capitalize()
}

/**
 * Converts target to PSI element if possible resolving it against roots and working directory
 */
fun targetAsPsiElement(targetType: PyTargetType,
                       target: String,
                       configuration: AbstractPythonRunConfiguration<*>,
                       workingDirectory: VirtualFile? = LocalFileSystem.getInstance().findFileByPath(
                         configuration.getWorkingDirectorySafe()))
  : PsiElement? {
  if (targetType == PyTargetType.PYTHON) {
    val module = configuration.getModule() ?: return null
    val context = TypeEvalContext.userInitiated(configuration.getProject(), null)

    val name = QualifiedName.fromDottedString(target)
    return name.resolveToElement(QNameResolveContext(ModuleBasedContextAnchor(module), configuration.getSdk(),
                                                     context, workingDirectory, true))
  }
  return null
}

/**
 * Converts target to file if possible
 */
fun targetAsVirtualFile(targetType: PyTargetType, target: String): VirtualFile? {
  if (targetType == PyTargetType.PATH) {
    return LocalFileSystem.getInstance().findFileByPath(target)
  }
  return null
}


/**
 * Implement it to obtain extension methods for [targetAsPsiElement]
 * and [targetAsVirtualFile]
 */
interface TargetWithType {
  val target: String?
  val targetType: PyTargetType
}