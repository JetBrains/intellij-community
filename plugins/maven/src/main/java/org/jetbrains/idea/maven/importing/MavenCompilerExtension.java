// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.ResolveContext;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;

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
  @NotNull
  String getMavenCompilerId();

  /**
   * Returns null if the IDE backend compiler can not be registered
   */
  @Nullable
  BackendCompiler getCompiler(Project project);

  /**
   * Allow to specify default IDEA compiler during the project resolve phase.
   * Use with caution! The only single default compiler is supported for the all IDE modules.
   */
  default boolean resolveDefaultCompiler(@NotNull Project project,
                                         @NotNull MavenProject mavenProject,
                                         @NotNull NativeMavenProjectHolder nativeMavenProject,
                                         @NotNull MavenEmbedderWrapper embedder,
                                         @NotNull ResolveContext context) throws MavenProcessCanceledException { return false; }
}
