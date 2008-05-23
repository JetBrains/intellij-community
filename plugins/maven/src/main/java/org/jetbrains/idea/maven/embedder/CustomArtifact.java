package org.jetbrains.idea.maven.embedder;

import com.intellij.openapi.util.io.FileUtil;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.jetbrains.idea.maven.core.MavenLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomArtifact implements Artifact {
  private static Map<String, File> ourCache = new HashMap<String, File>();

  private Artifact myWrapee;

  public CustomArtifact(Artifact a) {
    myWrapee = a;
  }

  public String getGroupId() {
    return myWrapee.getGroupId();
  }

  public String getArtifactId() {
    return myWrapee.getArtifactId();
  }

  public String getVersion() {
    return myWrapee.getVersion();
  }

  public void setVersion(String version) {
    myWrapee.setVersion(version);
  }

  public String getScope() {
    return myWrapee.getScope();
  }

  public String getType() {
    return myWrapee.getType();
  }

  public String getClassifier() {
    return myWrapee.getClassifier();
  }

  public boolean hasClassifier() {
    return myWrapee.hasClassifier();
  }

  public File getFile() {
    update();
    return myWrapee.getFile();
  }

  private void update() {
    if ("pom".equals(getType()) && isResolved()) {
      ensurePosFileExists();
    }
  }

  private void ensurePosFileExists() {
    File f = myWrapee.getFile();
    if (f == null || f.exists()) return;

    f = ourCache.get(getId());
    if (f != null) {
      myWrapee.setFile(f);
      return;
    }

    try {
      f = createTempFile(".pom");

      FileOutputStream s = new FileOutputStream(f);
      try {
        PrintWriter w = new PrintWriter(s);
        w.println("<project>");
        w.println("<modelVersion>4.0.0</modelVersion>");
        w.println("<packaging>pom</packaging>");
        w.println("<groupId>" + getGroupId() + "</groupId>");
        w.println("<artifactId>" + getArtifactId() + "</artifactId>");
        w.println("<version>" + getVersion() + "</version>");
        w.println("</project>");
        w.flush();
      } finally {
        s.close();
      }

      myWrapee.setFile(f);
      ourCache.put(getId(), f);
    }
    catch (IOException e) {
      MavenLog.LOG.warn(e);
    }
  }

  private File createTempFile(String suffix) throws IOException {
    File f = FileUtil.createTempFile("idea.maven.fake", suffix);
    f.deleteOnExit();
    return f;
  }

  public void setFile(File destination) {
    myWrapee.setFile(destination);
  }

  public String getBaseVersion() {
    return myWrapee.getBaseVersion();
  }

  public void setBaseVersion(String baseVersion) {
    myWrapee.setBaseVersion(baseVersion);
  }

  public String getId() {
    return myWrapee.getId();
  }

  public String getDependencyConflictId() {
    return myWrapee.getDependencyConflictId();
  }

  public void addMetadata(ArtifactMetadata metadata) {
    myWrapee.addMetadata(metadata);
  }

  public Collection getMetadataList() {
    return myWrapee.getMetadataList();
  }

  public void setRepository(ArtifactRepository remoteRepository) {
    myWrapee.setRepository(remoteRepository);
  }

  public ArtifactRepository getRepository() {
    return myWrapee.getRepository();
  }

  public void updateVersion(String version, ArtifactRepository localRepository) {
    myWrapee.updateVersion(version, localRepository);
  }

  public String getDownloadUrl() {
    return myWrapee.getDownloadUrl();
  }

  public void setDownloadUrl(String downloadUrl) {
    myWrapee.setDownloadUrl(downloadUrl);
  }

  public ArtifactFilter getDependencyFilter() {
    return myWrapee.getDependencyFilter();
  }

  public void setDependencyFilter(ArtifactFilter artifactFilter) {
    myWrapee.setDependencyFilter(artifactFilter);
  }

  public ArtifactHandler getArtifactHandler() {
    return myWrapee.getArtifactHandler();
  }

  public List getDependencyTrail() {
    return myWrapee.getDependencyTrail();
  }

  public void setDependencyTrail(List dependencyTrail) {
    myWrapee.setDependencyTrail(dependencyTrail);
  }

  public void setScope(String scope) {
    myWrapee.setScope(scope);
  }

  public VersionRange getVersionRange() {
    return myWrapee.getVersionRange();
  }

  public void setVersionRange(VersionRange newRange) {
    myWrapee.setVersionRange(newRange);
  }

  public void selectVersion(String version) {
    myWrapee.selectVersion(version);
  }

  public void setGroupId(String groupId) {
    myWrapee.setGroupId(groupId);
  }

  public void setArtifactId(String artifactId) {
    myWrapee.setArtifactId(artifactId);
  }

  public boolean isSnapshot() {
    return myWrapee.isSnapshot();
  }

  public void setResolved(boolean resolved) {
    myWrapee.setResolved(resolved);
  }

  public boolean isResolved() {
    return myWrapee.isResolved();
  }

  public void setResolvedVersion(String version) {
    myWrapee.setResolvedVersion(version);
  }

  public void setArtifactHandler(ArtifactHandler handler) {
    myWrapee.setArtifactHandler(handler);
  }

  public boolean isRelease() {
    return myWrapee.isRelease();
  }

  public void setRelease(boolean release) {
    myWrapee.setRelease(release);
  }

  public List getAvailableVersions() {
    return myWrapee.getAvailableVersions();
  }

  public void setAvailableVersions(List versions) {
    myWrapee.setAvailableVersions(versions);
  }

  public boolean isOptional() {
    return myWrapee.isOptional();
  }

  public void setOptional(boolean optional) {
    myWrapee.setOptional(optional);
  }

  public ArtifactVersion getSelectedVersion() throws OverConstrainedVersionException {
    return myWrapee.getSelectedVersion();
  }

  public boolean isSelectedVersionKnown() throws OverConstrainedVersionException {
    return myWrapee.isSelectedVersionKnown();
  }

  public int compareTo(Object o) {
    return myWrapee.compareTo(o);
  }

  @Override
  public String toString() {
    return myWrapee.toString();
  }

  @Override
  public boolean equals(Object obj) {
    return myWrapee.equals(obj);
  }

  @Override
  public int hashCode() {
    return myWrapee.hashCode();
  }
}
