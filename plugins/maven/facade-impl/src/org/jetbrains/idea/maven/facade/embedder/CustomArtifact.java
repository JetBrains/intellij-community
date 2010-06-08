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
package org.jetbrains.idea.maven.facade.embedder;

import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashMap;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.jetbrains.idea.maven.facade.MavenFacadeGlobalsManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CustomArtifact implements Artifact {
  private static final Map<String, File> ourStubCache = new THashMap<String, File>();
  private static final ReentrantReadWriteLock ourCacheLock = new ReentrantReadWriteLock();
  private static final Lock ourCacheReadLock = ourCacheLock.readLock();
  private static final Lock ourCacheWriteLock = ourCacheLock.writeLock();

  private final Artifact myWrapee;
  private volatile boolean isStub;

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
      ensurePomFileExists();
    }
  }

  private void ensurePomFileExists() {
    File f = myWrapee.getFile();
    if (f == null || f.exists()) return;

    isStub = true;

    ourCacheReadLock.lock();
    try {
      f = ourStubCache.get(getId());
    }
    finally {
      ourCacheReadLock.unlock();
    }

    if (f != null) {
      myWrapee.setFile(f);
      return;
    }

    ourCacheWriteLock.lock();
    try {
      f = ourStubCache.get(getId());
      if (f != null) {
        myWrapee.setFile(f);
        return;
      }

      f = FileUtil.createTempFile("idea.maven.stub", ".pom");
      f.deleteOnExit();

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
      }
      finally {
        s.close();
      }

      myWrapee.setFile(f);
      ourStubCache.put(getId(), f);
    }
    catch (IOException e) {
      MavenFacadeGlobalsManager.getLogger().warn(e);
    }
    finally {
      ourCacheWriteLock.unlock();
    }
  }

  public void setFile(File destination) {
    myWrapee.setFile(destination);
  }

  public boolean isStub() {
    return isStub;
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

  public ArtifactMetadata getMetadata(Class<?> metadataClass) {
    return myWrapee.getMetadata(metadataClass);
  }

  public void addMetadata(ArtifactMetadata metadata) {
    myWrapee.addMetadata(metadata);
  }

  public Collection<ArtifactMetadata> getMetadataList() {
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

  public List<String> getDependencyTrail() {
    return myWrapee.getDependencyTrail();
  }

  public void setDependencyTrail(List<String> dependencyTrail) {
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

  public List<ArtifactVersion> getAvailableVersions() {
    return myWrapee.getAvailableVersions();
  }

  public void setAvailableVersions(List<ArtifactVersion> versions) {
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

  public int compareTo(Artifact o) {
    return myWrapee.compareTo(o);
  }

  @Override
  public String toString() {
    return myWrapee.toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof CustomArtifact) obj = ((CustomArtifact)obj).myWrapee;
    return myWrapee.equals(obj);
  }

  @Override
  public int hashCode() {
    return myWrapee.hashCode();
  }
}
