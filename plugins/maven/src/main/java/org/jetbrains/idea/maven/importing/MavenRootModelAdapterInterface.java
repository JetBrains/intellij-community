// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.Path;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;

public interface MavenRootModelAdapterInterface {
  void init(boolean isNewlyCreatedModule);

  ModifiableRootModel getRootModel();

  String @NotNull [] getSourceRootUrls(boolean includingTests);

  Module getModule();

  void clearSourceFolders();

  <P extends JpsElement> void addSourceFolder(String path, JpsModuleSourceRootType<P> rootType);

  void addGeneratedJavaSourceFolder(String path, JavaSourceRootType rootType, boolean ifNotEmpty);

  void addGeneratedJavaSourceFolder(String path, JavaSourceRootType rootType);

  boolean hasRegisteredSourceSubfolder(@NotNull File f);

  @Nullable
  SourceFolder getSourceFolder(File folder);

  boolean isAlreadyExcluded(File f);

  void addExcludedFolder(String path);

  void unregisterAll(String path, boolean under, boolean unregisterSources);

  boolean hasCollision(String sourceRootPath);

  void useModuleOutput(String production, String test);

  Path toPath(String path);

  void addModuleDependency(@NotNull String moduleName,
                           @NotNull DependencyScope scope,
                           boolean testJar);

  @Nullable
  Module findModuleByName(String moduleName);

  void addSystemDependency(MavenArtifact artifact, DependencyScope scope);

  LibraryOrderEntry addLibraryDependency(MavenArtifact artifact,
                                         DependencyScope scope,
                                         ModifiableModelsProviderProxy provider,
                                         MavenProject project);

  Library findLibrary(@NotNull MavenArtifact artifact);

  void setLanguageLevel(LanguageLevel level);

}
