// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Dependency;
import org.jetbrains.idea.maven.model.MavenArtifact;

import java.io.File;
import java.nio.file.Path;

public final class Maven40ApiModelConverter {
  private static String convertExtension(Artifact artifact) {
    return artifact.getExtension();
  }

  public static MavenArtifact convertArtifactAndPath(Artifact artifact, Path artifactPath, File localRepository) {
    return new MavenArtifact(artifact.getGroupId(),
                             artifact.getArtifactId(),
                             artifact.getVersion().toString(),
                             artifact.getVersion().toString(),
                             "", //artifact.getType(),
                             artifact.getClassifier(),

                             "", //artifact.getScope(),
                             false, //artifact.isOptional(),

                             convertExtension(artifact),

                             null == artifactPath ? null : artifactPath.toFile(),
                             localRepository,

                             null != artifactPath,
                             false /*artifact instanceof CustomMaven3Artifact && ((CustomMaven3Artifact)artifact).isStub()*/);
  }

  /**
   * Converts an artifact that carries a known type and scope.
   *
   * <p>Use this instead of {@link #convertArtifactAndPath} when the caller knows the type and the scope. The
   * {@code baseVersion} comes from {@link Artifact#getBaseVersion()}, because
   * {@code MavenArtifact.getRelativePath()} builds the local repository path from the base version. A timestamped
   * snapshot needs the real base version.
   *
   * @param artifactPath the resolved path, or {@code null} when Maven did not resolve the artifact
   */
  public static MavenArtifact convertArtifactAndPath(Artifact artifact,
                                                     Path artifactPath,
                                                     File localRepository,
                                                     String type,
                                                     String scope,
                                                     boolean optional) {
    return new MavenArtifact(artifact.getGroupId(),
                             artifact.getArtifactId(),
                             artifact.getVersion().toString(),
                             artifact.getBaseVersion().toString(),
                             type,
                             artifact.getClassifier(),

                             scope,
                             optional,

                             convertExtension(artifact),

                             null == artifactPath ? null : artifactPath.toFile(),
                             localRepository,

                             null != artifactPath,
                             false);
  }

  /** Converts a resolved or collected dependency, taking the type, the scope and the optional flag from it. */
  public static MavenArtifact convertDependencyAndPath(Dependency dependency, Path artifactPath, File localRepository) {
    return convertArtifactAndPath(dependency,
                                  artifactPath,
                                  localRepository,
                                  dependency.getType().id(),
                                  dependency.getScope().id(),
                                  dependency.isOptional());
  }
}
