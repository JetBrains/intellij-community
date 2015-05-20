/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.server.embedder;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.DefaultArtifactFactory;
import org.apache.maven.artifact.versioning.VersionRange;

public class CustomMaven3ArtifactFactory extends DefaultArtifactFactory {
  private static final VersionRange UNKNOWN_VERSION_RANGE = VersionRange.createFromVersion("unknown");

  private boolean myCustomized;

  public void customize() {
    myCustomized = true;
  }

  public void reset() {
    myCustomized = false;
  }

  @Override
  public Artifact createArtifact(String groupId, String artifactId, String version, String scope, String type) {
      return wrap(super.createArtifact(checkValue(groupId), checkValue(artifactId), checkVersion(version), scope, type));
  }

  @Override
  public Artifact createArtifactWithClassifier(String groupId, String artifactId, String version, String type, String classifier) {
      return wrap(super.createArtifactWithClassifier(checkValue(groupId), checkValue(artifactId), checkVersion(version), type, classifier));
  }

  @Override
  public Artifact createDependencyArtifact(String groupId, String artifactId, VersionRange versionRange, String type, String classifier, String scope) {
      return wrap(super.createDependencyArtifact(checkValue(groupId), checkValue(artifactId), checkVersionRange(versionRange), type, classifier, scope));
  }

  @Override
  public Artifact createDependencyArtifact(String groupId, String artifactId, VersionRange versionRange, String type, String classifier, String scope, boolean optional) {
      return wrap(super.createDependencyArtifact(checkValue(groupId), checkValue(artifactId), checkVersionRange(versionRange), type, classifier, scope, optional));
  }

  @Override
  public Artifact createDependencyArtifact(String groupId, String artifactId, VersionRange versionRange, String type, String classifier, String scope, String inheritedScope) {
      return wrap(super.createDependencyArtifact(checkValue(groupId), checkValue(artifactId), checkVersionRange(versionRange), type, classifier, scope, inheritedScope));
  }

  @Override
  public Artifact createDependencyArtifact(String groupId, String artifactId, VersionRange versionRange, String type, String classifier, String scope, String inheritedScope, boolean optional) {
      return wrap(super.createDependencyArtifact(checkValue(groupId), checkValue(artifactId), checkVersionRange(versionRange), type, classifier, scope, inheritedScope, optional));
  }

  @Override
  public Artifact createBuildArtifact(String groupId, String artifactId, String version, String packaging) {
      return wrap(super.createBuildArtifact(checkValue(groupId), checkValue(artifactId), checkVersion(version), packaging));
  }

  @Override
  public Artifact createProjectArtifact(String groupId, String artifactId, String version) {
      return wrap(super.createProjectArtifact(checkValue(groupId), checkValue(artifactId), checkVersion(version)));
  }

  @Override
  public Artifact createParentArtifact(String groupId, String artifactId, String version) {
      return wrap(super.createParentArtifact(checkValue(groupId), checkValue(artifactId), checkVersion(version)));
  }

  @Override
  public Artifact createPluginArtifact(String groupId, String artifactId, VersionRange versionRange) {
      return wrap(super.createPluginArtifact(checkValue(groupId), checkValue(artifactId), checkVersionRange(versionRange)));
  }

  @Override
  public Artifact createProjectArtifact(String groupId, String artifactId, String version, String scope) {
      return wrap(super.createProjectArtifact(checkValue(groupId), checkValue(artifactId), checkVersion(version), scope));
  }

  @Override
  public Artifact createExtensionArtifact(String groupId, String artifactId, VersionRange versionRange) {
      return wrap(super.createExtensionArtifact(checkValue(groupId), checkValue(artifactId), checkVersionRange(versionRange)));
  }

  private Artifact wrap(Artifact a) {
    if (!myCustomized) return a;
    return a != null ? new CustomMaven3Artifact(a) : null;
  }

  private String checkValue(String value) {
    return value == null || value.trim().length() == 0 ? "error" : value;
  }

  private String checkVersion(String value) {
    return value == null ? "unknown" : value;
  }

  private VersionRange checkVersionRange(VersionRange range) {
    return range == null ? UNKNOWN_VERSION_RANGE : range;
  }
}
