// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.compiler.impl.javaCompiler.BackendCompiler
import com.intellij.compiler.impl.javaCompiler.eclipse.EclipseCompiler
import com.intellij.openapi.project.Project

/**
 * @author Vladislav.Soroka
 */
class MavenCompilerEclipseExtension : MavenCompilerExtension {
  override val mavenCompilerId: String = "eclipse"

  override fun getCompiler(project: Project): BackendCompiler? {
    val compilerConfiguration = CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl
    return compilerConfiguration.registeredJavaCompilers.find { it is EclipseCompiler }
  }
}
