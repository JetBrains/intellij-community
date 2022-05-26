// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.importing.tree.dependency.MavenImportDependency;

import java.util.Collections;
import java.util.List;

public class MavenModuleImportDataWithDependencies {
  @NotNull private final MavenProjectImportContextProvider.MavenProjectImportData mavenProjectImportData;
  @NotNull private final List<MavenImportDependency<?>> mainDependencies;
  @NotNull private final List<MavenImportDependency<?>> testDependencies;

  public MavenModuleImportDataWithDependencies(@NotNull MavenProjectImportContextProvider.MavenProjectImportData mavenProjectImportData,
                                               @NotNull List<MavenImportDependency<?>> mainDependencies,
                                               @NotNull List<MavenImportDependency<?>> testDependencies) {
    this.mavenProjectImportData = mavenProjectImportData;
    this.mainDependencies = mainDependencies;
    this.testDependencies = testDependencies;
  }

  public MavenModuleImportDataWithDependencies(@NotNull MavenProjectImportContextProvider.MavenProjectImportData mavenProjectImportData,
                                               @NotNull List<MavenImportDependency<?>> mainDependencies) {
    this(mavenProjectImportData, mainDependencies, Collections.emptyList());
  }

  public @NotNull MavenProjectImportContextProvider.MavenProjectImportData getModuleImportData() {
    return mavenProjectImportData;
  }

  public @NotNull List<MavenImportDependency<?>> getMainDependencies() {
    return mainDependencies;
  }

  public @NotNull List<MavenImportDependency<?>> getTestDependencies() {
    return testDependencies;
  }

  @Override
  public String toString() {
    return mavenProjectImportData.mavenProject.getMavenId().toString();
  }
}
