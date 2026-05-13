// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.lombok.configuration

import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.configuration.AbstractKotlinCompilerProjectPostConfigurator
import org.jetbrains.kotlin.lombok.k2.FirLombokKotlinRegistrar

internal class LombokCompilerPluginProjectPostConfigurator : AbstractKotlinCompilerProjectPostConfigurator("lombok") {
  override fun isApplicable(module: Module): Boolean =
    JavaLibraryUtil.hasLibraryClass(module, LOMBOK_FQN) &&
    compilerPluginProjectConfigurators(module).isNotEmpty() &&
    !module.hasCompilerPluginExtension { it is FirLombokKotlinRegistrar }
}

private const val LOMBOK_FQN: String = "lombok.Lombok"
