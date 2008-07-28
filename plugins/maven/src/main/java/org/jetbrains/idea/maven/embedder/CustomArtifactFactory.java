package org.jetbrains.idea.maven.embedder;

import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.factory.DefaultArtifactFactory;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.VersionRange;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;

import java.lang.reflect.Field;

public class CustomArtifactFactory implements ArtifactFactory, Contextualizable {
  private ArtifactFactory myWrapee = new DefaultArtifactFactory();
  private ArtifactHandlerManager artifactHandlerManager;

  public void contextualize(Context context) throws ContextException {
    try {
      Field f = myWrapee.getClass().getDeclaredField("artifactHandlerManager");
      f.setAccessible(true);
      f.set(myWrapee, artifactHandlerManager);
    }
    catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public Artifact createArtifact(String groupId, String artifactId, String version, String scope, String type) {
      return wrap(myWrapee.createArtifact(checkValue(groupId), checkValue(artifactId), checkVersion(version), scope, type));
  }

  public Artifact createArtifactWithClassifier(String groupId, String artifactId, String version, String type, String classifier) {
      return wrap(myWrapee.createArtifactWithClassifier(checkValue(groupId), checkValue(artifactId), checkVersion(version), type, classifier));
  }

  public Artifact createDependencyArtifact(String groupId, String artifactId, VersionRange versionRange, String type, String classifier, String scope) {
      return wrap(myWrapee.createDependencyArtifact(checkValue(groupId), checkValue(artifactId), checkVersionRange(versionRange), type, classifier, scope));
  }

  public Artifact createDependencyArtifact(String groupId, String artifactId, VersionRange versionRange, String type, String classifier, String scope, boolean optional) {
      return wrap(myWrapee.createDependencyArtifact(checkValue(groupId), checkValue(artifactId), checkVersionRange(versionRange), type, classifier, scope, optional));
  }

  public Artifact createDependencyArtifact(String groupId, String artifactId, VersionRange versionRange, String type, String classifier, String scope, String inheritedScope) {
      return wrap(myWrapee.createDependencyArtifact(checkValue(groupId), checkValue(artifactId), checkVersionRange(versionRange), type, classifier, scope, inheritedScope));
  }

  public Artifact createDependencyArtifact(String groupId, String artifactId, VersionRange versionRange, String type, String classifier, String scope, String inheritedScope, boolean optional) {
      return wrap(myWrapee.createDependencyArtifact(checkValue(groupId), checkValue(artifactId), checkVersionRange(versionRange), type, classifier, scope, inheritedScope, optional));
  }

  public Artifact createBuildArtifact(String groupId, String artifactId, String version, String packaging) {
      return wrap(myWrapee.createBuildArtifact(checkValue(groupId), checkValue(artifactId), checkVersion(version), packaging));
  }

  public Artifact createProjectArtifact(String groupId, String artifactId, String version) {
      return wrap(myWrapee.createProjectArtifact(checkValue(groupId), checkValue(artifactId), checkVersion(version)));
  }

  public Artifact createParentArtifact(String groupId, String artifactId, String version) {
      return wrap(myWrapee.createParentArtifact(checkValue(groupId), checkValue(artifactId), checkVersion(version)));
  }

  public Artifact createPluginArtifact(String groupId, String artifactId, VersionRange versionRange) {
      return wrap(myWrapee.createPluginArtifact(checkValue(groupId), checkValue(artifactId), checkVersionRange(versionRange)));
  }

  public Artifact createProjectArtifact(String groupId, String artifactId, String version, String scope) {
      return wrap(myWrapee.createProjectArtifact(checkValue(groupId), checkValue(artifactId), checkVersion(version), scope));
  }

  public Artifact createExtensionArtifact(String groupId, String artifactId, VersionRange versionRange) {
      return wrap(myWrapee.createExtensionArtifact(checkValue(groupId), checkValue(artifactId), checkVersionRange(versionRange)));
  }

  private Artifact wrap(Artifact a) {
    return a != null ? new CustomArtifact(a) : null;
  }

  private String checkValue(String value) {
    return value == null || value.trim().length() == 0 ? "error" : value;
  }

  private String checkVersion(String value) {
    return value == null ? "unknown" : value;
  }

  private VersionRange checkVersionRange(VersionRange range) {
    return range == null ? VersionRange.createFromVersion("unknown") : range;
  }
}
