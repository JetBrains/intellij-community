// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.server.m40.utils;

import org.apache.maven.api.Artifact;
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
}
