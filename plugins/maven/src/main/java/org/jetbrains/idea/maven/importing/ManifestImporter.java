/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 * @author Vladislav.Soroka
 * @since 5/26/2014
 */
public abstract class ManifestImporter {
  public static final ExtensionPointName<ManifestImporter> EXTENSION_POINT_NAME =
    ExtensionPointName.create("org.jetbrains.idea.maven.manifestImporter");

  @NotNull
  public static ManifestImporter getManifestImporter(@NotNull String packaging) {
    for (ManifestImporter importer : EXTENSION_POINT_NAME.getExtensions()) {
      if (importer.isApplicable(packaging)) {
        return importer;
      }
    }
    return new DefaultManifestImporter();
  }

  @NotNull
  public String getClasspath(@NotNull MavenProject mavenProject,
                             @Nullable Element manifestConfiguration) {
    StringBuilder classpath = new StringBuilder();
    String classpathPrefix = getClasspathPrefix(manifestConfiguration);
    for (MavenArtifact mavenArtifact : mavenProject.getDependencies()) {
      final DependencyScope scope = MavenModuleImporter.selectScope(mavenArtifact.getScope());
      if (scope.isForProductionCompile() || scope.isForProductionRuntime()) {
        if (classpath.length() > 0) {
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
