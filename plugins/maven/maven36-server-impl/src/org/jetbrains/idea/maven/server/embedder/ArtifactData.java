// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.embedder;

import org.eclipse.aether.artifact.Artifact;

import java.util.Map;
import java.util.Objects;

class ArtifactData {
  private final String artifactId;
  private final String groupId;
  private final String version;
  private final String classifier;
  private final String extension;
  private final Map<String, String> properties;

  ArtifactData(String artifactId, String groupId, String version, String classifier, String extension, Map<String, String> properties) {
    this.artifactId = artifactId;
    this.groupId = groupId;
    this.version = version;
    this.classifier = classifier;
    this.extension = extension;
    this.properties = properties;
  }

  ArtifactData(Artifact artifact) {
    this(artifact.getArtifactId(),
         artifact.getGroupId(),
         artifact.getVersion(),
         artifact.getClassifier(),
         artifact.getExtension(),
         artifact.getProperties());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ArtifactData data = (ArtifactData)o;

    if (!Objects.equals(artifactId, data.artifactId)) return false;
    if (!Objects.equals(groupId, data.groupId)) return false;
    if (!Objects.equals(version, data.version)) return false;
    if (!Objects.equals(classifier, data.classifier)) return false;
    if (!Objects.equals(extension, data.extension)) return false;
    if (!Objects.equals(properties, data.properties)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = artifactId != null ? artifactId.hashCode() : 0;
    result = 31 * result + (groupId != null ? groupId.hashCode() : 0);
    result = 31 * result + (version != null ? version.hashCode() : 0);
    result = 31 * result + (classifier != null ? classifier.hashCode() : 0);
    result = 31 * result + (extension != null ? extension.hashCode() : 0);
    result = 31 * result + (properties != null ? properties.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "ArtifactData{" +
           "groupId='" + groupId + '\'' +
           ", artifactId='" + artifactId + '\'' +
           ", version='" + version + '\'' +
           '}';
  }
}
