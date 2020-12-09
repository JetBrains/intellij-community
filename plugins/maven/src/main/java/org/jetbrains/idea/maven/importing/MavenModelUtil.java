// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;

public final class MavenModelUtil {

  @NotNull
  public static String getArtifactUrlForClassifierAndExtension(@NotNull MavenArtifact artifact,
                                                               @Nullable String classifier,
                                                               @Nullable String extension) {

    String newPath = artifact.getPathForExtraArtifact(classifier, extension);
    return VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, newPath) + JarFileSystem.JAR_SEPARATOR;
  }

  @NotNull
  public static String getArtifactUrl(@NotNull MavenArtifact artifact,
                                      @NotNull MavenExtraArtifactType artifactType,
                                      @NotNull MavenProject project) {

    Pair<String, String> result = project.getClassifierAndExtension(artifact, artifactType);
    String classifier = result.first;
    String extension = result.second;


    return getArtifactUrlForClassifierAndExtension(artifact, classifier, extension);
  }

}
