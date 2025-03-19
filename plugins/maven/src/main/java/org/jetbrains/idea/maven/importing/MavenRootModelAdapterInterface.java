// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenPathWrapper;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;

public interface MavenRootModelAdapterInterface {
  void init(boolean isNewlyCreatedModule);

  ModifiableRootModel getRootModel();

  Module getModule();

  <P extends JpsElement> void addSourceFolder(String path, JpsModuleSourceRootType<P> rootType);

  void addGeneratedJavaSourceFolder(String path, JavaSourceRootType rootType);

  boolean isAlreadyExcluded(File f);

  void addExcludedFolder(String path);

  void unregisterAll(String path, boolean under, boolean unregisterSources);

  boolean hasCollision(String sourceRootPath);

  void useModuleOutput(String production, String test);

  MavenPathWrapper toPath(String path);

  void addModuleDependency(@NotNull String moduleName,
                           @NotNull DependencyScope scope,
                           boolean testJar);

  @Nullable
  Module findModuleByName(String moduleName);

  void addSystemDependency(MavenArtifact artifact, DependencyScope scope);

  LibraryOrderEntry addLibraryDependency(MavenArtifact artifact,
                                         DependencyScope scope,
                                         IdeModifiableModelsProvider provider,
                                         MavenProject project);

  Library findLibrary(@NotNull MavenArtifact artifact);

  void setLanguageLevel(LanguageLevel level);

}
