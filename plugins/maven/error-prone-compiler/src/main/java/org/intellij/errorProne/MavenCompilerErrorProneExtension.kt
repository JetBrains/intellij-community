// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.errorProne

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.compiler.impl.javaCompiler.BackendCompiler
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.importing.MavenCompilerExtension

/**
 * @author Vladislav.Soroka
 */
class MavenCompilerErrorProneExtension : MavenCompilerExtension {
  override val mavenCompilerId: String = "javac-with-errorprone"

  override fun getCompiler(project: Project): BackendCompiler? {
    val compilerConfiguration = CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl
    return compilerConfiguration.registeredJavaCompilers.find {
      // TODO move `intellij.maven.errorProne.compiler` module to the errorProne plugin module (contrib repo)
      it.javaClass.name == "org.intellij.errorProne.ErrorProneJavaBackendCompiler"
    }
  }
}

