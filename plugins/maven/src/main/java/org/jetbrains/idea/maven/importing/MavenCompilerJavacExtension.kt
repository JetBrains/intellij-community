// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.compiler.impl.javaCompiler.BackendCompiler
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.openapi.project.Project
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions

/**
 * @author Vladislav.Soroka
 */
class MavenCompilerJavacExtension : MavenCompilerExtension {
  override fun getMavenCompilerId(): String = "javac"

  override fun getCompiler(project: Project): BackendCompiler {
    return (CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl).javacCompiler
  }

  override fun getOptions(project: Project): JpsJavaCompilerOptions {
    return JavacConfiguration.getOptions(project, JavacConfiguration::class.java)
  }
}
