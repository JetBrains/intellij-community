// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.compiler.impl.javaCompiler.BackendCompiler
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper
import org.jetbrains.jps.model.java.compiler.CompilerOptions
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions

/**
 * Import maven compiler configuration for different compilerIds if the related IDE compilers support is available.
 */
@ApiStatus.Internal
interface MavenCompilerExtension {
  /**
   * ID of the maven compiler, see the role-hint of the @plexus.component with role="org.codehaus.plexus.compiler.Compiler".
   * Note, this can be not equal to [BackendCompiler.getId]
   *
   * @return maven compiler id
   */
  val mavenCompilerId: String

  /**
   * Returns null if the IDE backend compiler cannot be registered
   */
  fun getCompiler(project: Project): BackendCompiler?

  /**
   * Allow specifying the default IDEA compiler during the project resolve phase.
   * Use with caution! The only single default compiler is supported for the all IDE modules.
   */
  fun resolveDefaultCompiler(project: Project, mavenProject: MavenProject, embedder: MavenEmbedderWrapper): Boolean {
    return false
  }

  fun getDefaultCompilerTargetLevel(mavenProject: MavenProject, module: Module): String? {
    return null
  }

  fun configureOptions(compilerOptions: CompilerOptions?, module: Module, mavenProject: MavenProject, compilerArgs: List<String>) {
    if (compilerOptions is JpsJavaCompilerOptions) {
      val compilerConfiguration = CompilerConfiguration.getInstance(module.getProject()) as CompilerConfigurationImpl
      compilerConfiguration.setAdditionalOptions(compilerOptions, module, compilerArgs)
    }
  }

  companion object {
    val EP_NAME: ExtensionPointName<MavenCompilerExtension> = create<MavenCompilerExtension>("org.jetbrains.idea.maven.compiler")
  }
}
