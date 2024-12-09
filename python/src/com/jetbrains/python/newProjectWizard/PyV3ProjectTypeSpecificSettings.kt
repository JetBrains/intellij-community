// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile

/**
 * Implementation must have fields and generate project-specific (like Django) things using them i.e:
 * ```kotlin
 * class SpamGeneratorSettings(var queueSize:Int): PyV3ProjectTypeSpecificSettings {
 *    override fun generateProject(module: Module, baseDir: VirtualFile, sdk: Sdk) {
 *       generateSpam(queueSize, module, baseDir, sdk)
 *    }
 * }
 * ```
 */
fun interface PyV3ProjectTypeSpecificSettings {
  /**
   * Generate project-specific things in [baseDir].
   * You might need to [installPackages] on [sdk]
   */
  suspend fun generateProject(module: Module, baseDir: VirtualFile, sdk: Sdk): Result<Boolean>
}