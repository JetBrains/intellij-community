// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.lombok.inspections

import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.configuration.inspections.AbstractKotlinCompilerPluginInspection
import org.jetbrains.kotlin.idea.lombok.LombokKotlinBundle
import org.jetbrains.kotlin.lombok.k2.FirLombokJavaRegistrar
import org.jetbrains.kotlin.psi.KtFile

class LombokKotlinCompilerPluginInspection : AbstractKotlinCompilerPluginInspection("lombok") {

    override val descriptionTemplate: String
        get() = LombokKotlinBundle.message("lombok.kotlin.no.kotlin.lombok.compiler.plugin.inspection.problem.descriptor")

    override val familyName: String
        get() = LombokKotlinBundle.message("lombok.kotlin.no.kotlin.lombok.compiler.plugin.inspection.problem.quick.fix")

    override fun isAvailableForFileInModule(
        ktFile: KtFile,
        module: Module
    ): Boolean =
        JavaLibraryUtil.hasLibraryClass(module, LOMBOK_FQN)
                && compilerPluginProjectConfigurators(module).isNotEmpty()
                && !ktFile.hasCompilerPluginExtension { it is FirLombokJavaRegistrar }

    override fun isCompilerPluginRequired(file: KtFile): Boolean = true
}

private const val LOMBOK_FQN: String = "lombok.Lombok"
