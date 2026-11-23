// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.configuration.findModuleInterpreterIfReady
import org.jetbrains.annotations.ApiStatus

/** The interpreter of the Python project of [psiFile], read from the snapshot. It walks the PSI, so it needs a read action. */
@ApiStatus.Internal
fun getPythonInterpreter(psiFile: PsiFile): PythonInterpreter? =
  findModuleInterpreterIfReady(ModuleUtil.findModuleForPsiElement(psiFile))

/** The interpreter of the Python project of [virtualFile] in [project], read from the snapshot. */
@ApiStatus.Internal
fun getPythonInterpreter(project: Project, virtualFile: VirtualFile): PythonInterpreter? =
  findModuleInterpreterIfReady(ModuleUtil.findModuleForFile(virtualFile, project))
