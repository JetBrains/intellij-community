/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.run.targetBasedConfiguration

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.extensions.ModuleBasedContextAnchor
import com.jetbrains.extensions.QNameResolveContext
import com.jetbrains.extensions.resolveToElement
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.run.PythonRunConfigurationForm
import org.jetbrains.annotations.Nls

/**
 * Types of target (symbol, path or custom) many python runners may have
 */
enum class PyRunTargetVariant(@Nls private val customName: String? = null) {
  PYTHON(PythonRunConfigurationForm.getModuleNameText()), PATH(PythonRunConfigurationForm.getScriptPathText()), CUSTOM;

  @Nls fun getCustomName(): String = customName ?: PythonRunConfigurationForm.getCustomNameText()
}

/**
 * Converts target to PSI element if possible resolving it against roots and working directory
 */
fun targetAsPsiElement(targetType: PyRunTargetVariant,
                       target: String,
                       configuration: AbstractPythonRunConfiguration<*>,
                       workingDirectory: VirtualFile? = LocalFileSystem.getInstance().findFileByPath(
                         configuration.getWorkingDirectorySafe()))
  : PsiElement? {
  if (targetType == PyRunTargetVariant.PYTHON) {
    val module = configuration.getModule() ?: return null
    val context = TypeEvalContext.userInitiated(configuration.getProject(), null)

    return runReadAction {
      QualifiedName.fromDottedString(target)
        .resolveToElement(QNameResolveContext(ModuleBasedContextAnchor(module), configuration.getSdk(), context, workingDirectory, true))
    }
  }
  return null
}

/**
 * Converts target to file if possible
 */
fun targetAsVirtualFile(targetType: PyRunTargetVariant, target: String): VirtualFile? {
  if (targetType == PyRunTargetVariant.PATH) {
    return LocalFileSystem.getInstance().findFileByPath(target)
  }
  return null
}


/**
 * Implement it to obtain extension methods for [targetAsPsiElement]
 * and [targetAsVirtualFile]
 */
interface TargetWithVariant {
  val target: String?
  /**
   * Do not rename this field: its may be used as key in serialized configurations
   */
  val targetType: PyRunTargetVariant
}
