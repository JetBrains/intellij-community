// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.roots.DependencyScope;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;

import static org.jetbrains.idea.maven.utils.ManifestBuilder.getClasspathPrefix;

/**
 * Extension point for take additional MANIFEST.MF entries.
 * @author Vladislav.Soroka
 */
public abstract class ManifestImporter {
  public static final ExtensionPointName<ManifestImporter> EXTENSION_POINT_NAME =
    ExtensionPointName.create("org.jetbrains.idea.maven.manifestImporter");

  public static @NotNull ManifestImporter getManifestImporter(@NotNull String packaging) {
    for (ManifestImporter importer : EXTENSION_POINT_NAME.getExtensions()) {
      if (importer.isApplicable(packaging)) {
        return importer;
      }
    }
    return new DefaultManifestImporter();
  }

  public @NotNull String getClasspath(@NotNull MavenProject mavenProject,
                                      @Nullable Element manifestConfiguration) {
    StringBuilder classpath = new StringBuilder();
    String classpathPrefix = getClasspathPrefix(manifestConfiguration);
    for (MavenArtifact mavenArtifact : mavenProject.getDependencies()) {
      final DependencyScope scope = MavenProjectImporterUtil.selectScope(mavenArtifact.getScope());
      if (scope.isForProductionCompile() || scope.isForProductionRuntime()) {
        if (!classpath.isEmpty()) {
          classpath.append(" ");
        }
        classpath.append(classpathPrefix);
        String artifactFileName = mavenArtifact.getArtifactId() + "-" + mavenArtifact.getVersion() + "." + mavenArtifact.getExtension();
        classpath.append(doGetClasspathItem(mavenProject, mavenArtifact, artifactFileName));
      }
    }
    return classpath.toString();
  }

  protected abstract boolean isApplicable(String packaging);

  protected abstract String doGetClasspathItem(@NotNull MavenProject mavenProject,
                                               @NotNull MavenArtifact mavenArtifact,
                                               @NotNull String artifactFileName);

  private static class DefaultManifestImporter extends ManifestImporter {

    @Override
    protected boolean isApplicable(String packaging) {
      return true;
    }

    @Override
    protected String doGetClasspathItem(@NotNull MavenProject mavenProject,
                                        @NotNull MavenArtifact mavenArtifact,
                                        @NotNull String artifactFileName) {
      return artifactFileName;
    }
  }
}
