// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.plugins.groovy

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.compiler.impl.javaCompiler.BackendCompiler
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.importing.MavenCompilerExtension
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.jps.model.java.compiler.CompilerOptions
import org.jetbrains.plugins.groovy.compiler.GreclipseIdeaCompiler
import org.jetbrains.plugins.groovy.compiler.GreclipseIdeaCompilerSettings

/**
 *
 */
class MavenCompilerGrEclipseExtension : MavenCompilerExtension {
  override fun getMavenCompilerId(): String = "groovy-eclipse-compiler"

  override fun getCompiler(project: Project): BackendCompiler? {
    val compilerConfiguration = CompilerConfiguration.getInstance(project) as CompilerConfigurationImpl
    return compilerConfiguration.registeredJavaCompilers.find { it is GreclipseIdeaCompiler }
  }

  override fun configureOptions(compilerOptions: CompilerOptions?,
                                module: Module,
                                mavenProject: MavenProject,
                                compilerArgs: MutableList<String>) {
    val eclipseBatchId = mavenProject.plugins.filter { it.artifactId == "maven-compiler-plugin" && it.groupId == "org.apache.maven.plugins" }
      .flatMap { it.dependencies }
      .find { it.groupId == "org.codehaus.groovy" && it.artifactId == "groovy-eclipse-batch" }
    val batchLib = mavenProject.dependencies.find { it.mavenId == eclipseBatchId }

    if (batchLib != null) {
      GreclipseIdeaCompilerSettings.setGrEclipsePath(module.project, batchLib.file.absolutePath);
      GreclipseIdeaCompilerSettings.setGrCmdParams(module.project, compilerArgs.joinToString(" "));
    }
    else {
      MavenProjectsManager.getInstance(module.project).syncConsole.addWarning(
        SyncBundle.message("maven.sync.warnings.eclipse.batch.compiler.no.dependency"),
        SyncBundle.message("maven.sync.warnings.eclipse.batch.compiler.no.dependency.desc")
      )
    }

  }
}
