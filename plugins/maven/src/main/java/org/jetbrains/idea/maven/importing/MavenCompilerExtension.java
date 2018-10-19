// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;

/**
 * @author Vladislav.Soroka
 */
public interface MavenCompilerExtension {
  ExtensionPointName<MavenCompilerExtension> EP_NAME = ExtensionPointName.create("org.jetbrains.idea.maven.compiler");

  /**
   * Id of the maven compiler, see the role-hint of the @plexus.component with role="org.codehaus.plexus.compiler.Compiler".
   * Note, this can be not equal to {@link BackendCompiler#getId()}
   *
   * @return maven compiler id
   */
  String getMavenCompilerId();

  BackendCompiler getCompiler(Project project);

  JpsJavaCompilerOptions getOptions(Project project);
}
