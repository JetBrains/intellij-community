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

import com.intellij.openapi.util.io.FileUtilRt;
import gnu.trove.THashMap;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.artifact.versioning.VersionRange;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CustomMaven3Artifact implements Artifact {
  private static final Map<String, File> ourStubCache = new THashMap<String, File>();
  private static final ReentrantReadWriteLock ourCacheLock = new ReentrantReadWriteLock();
  private static final Lock ourCacheReadLock = ourCacheLock.readLock();
  private static final Lock ourCacheWriteLock = ourCacheLock.writeLock();

  private final Artifact myWrapee;
  private volatile boolean isStub;

  public CustomMaven3Artifact(Artifact a) {
    myWrapee = a;
  }

  @Override
  public String getGroupId() {
    return myWrapee.getGroupId();
  }

  @Override
  public String getArtifactId() {
    return myWrapee.getArtifactId();
  }

  @Override
  public String getVersion() {
    return myWrapee.getVersion();
  }

  @Override
  public void setVersion(String version) {
    myWrapee.setVersion(version);
  }

  @Override
  public String getScope() {
    return myWrapee.getScope();
  }

  @Override
  public String getType() {
    return myWrapee.getType();
  }

  @Override
  public String getClassifier() {
    return myWrapee.getClassifier();
  }

  @Override
  public boolean hasClassifier() {
    return myWrapee.hasClassifier();
  }

  @Override
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

      f = FileUtilRt.createTempFile("idea.maven.stub", ".pom");
      f.deleteOnExit();

      FileOutputStream s = new FileOutputStream(f);
      PrintWriter w = new PrintWriter(s);
      try {
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
        w.close();
        s.close();
      }

      myWrapee.setFile(f);
      ourStubCache.put(getId(), f);
    }
    catch (IOException e) {
      // todo
      //try {
      //  MavenFacadeGlobalsManager.getLogger().warn(e);
      //}
      //catch (RemoteException e1) {
      //  throw new RuntimeRemoteException(e1);
      //}
    }
    finally {
      ourCacheWriteLock.unlock();
    }
  }

  @Override
  public void setFile(File destination) {
    myWrapee.setFile(destination);
  }

  public boolean isStub() {
    return isStub;
  }

  @Override
  public String getBaseVersion() {
    return myWrapee.getBaseVersion();
  }

  @Override
  public void setBaseVersion(String baseVersion) {
    myWrapee.setBaseVersion(baseVersion);
  }

  @Override
  public String getId() {
    try {
      return myWrapee.getId();
    }
    catch (NullPointerException e) {
      if (e.getMessage() != null && e.getMessage().contains("version was null")) {
        VersionRange range = getVersionRange();
        if (range != null) {
          setBaseVersion(range.toString());
          return myWrapee.getId();
        }
      }
      throw e;
    }
  }

  @Override
  public String getDependencyConflictId() {
    return myWrapee.getDependencyConflictId();
  }

  @Override
  public void addMetadata(ArtifactMetadata metadata) {
    myWrapee.addMetadata(metadata);
  }

  @Override
  public Collection<ArtifactMetadata> getMetadataList() {
    return myWrapee.getMetadataList();
  }

  @Override
  public void setRepository(ArtifactRepository remoteRepository) {
    myWrapee.setRepository(remoteRepository);
  }

  @Override
  public ArtifactRepository getRepository() {
    return myWrapee.getRepository();
  }

  @Override
  public void updateVersion(String version, ArtifactRepository localRepository) {
    myWrapee.updateVersion(version, localRepository);
  }

  @Override
  public String getDownloadUrl() {
    return myWrapee.getDownloadUrl();
  }

  @Override
  public void setDownloadUrl(String downloadUrl) {
    myWrapee.setDownloadUrl(downloadUrl);
  }

  @Override
  public ArtifactFilter getDependencyFilter() {
    return myWrapee.getDependencyFilter();
  }

  @Override
  public void setDependencyFilter(ArtifactFilter artifactFilter) {
    myWrapee.setDependencyFilter(artifactFilter);
  }

  @Override
  public ArtifactHandler getArtifactHandler() {
    return myWrapee.getArtifactHandler();
  }

  @Override
  public List<String> getDependencyTrail() {
    return myWrapee.getDependencyTrail();
  }

  @Override
  public void setDependencyTrail(List<String> dependencyTrail) {
    myWrapee.setDependencyTrail(dependencyTrail);
  }

  @Override
  public void setScope(String scope) {
    myWrapee.setScope(scope);
  }

  @Override
  public VersionRange getVersionRange() {
    return myWrapee.getVersionRange();
  }

  @Override
  public void setVersionRange(VersionRange newRange) {
    myWrapee.setVersionRange(newRange);
  }

  @Override
  public void selectVersion(String version) {
    myWrapee.selectVersion(version);
  }

  @Override
  public void setGroupId(String groupId) {
    myWrapee.setGroupId(groupId);
  }

  @Override
  public void setArtifactId(String artifactId) {
    myWrapee.setArtifactId(artifactId);
  }

  @Override
  public boolean isSnapshot() {
    return myWrapee.isSnapshot();
  }

  @Override
  public void setResolved(boolean resolved) {
    myWrapee.setResolved(resolved);
  }

  @Override
  public boolean isResolved() {
    return myWrapee.isResolved();
  }

  @Override
  public void setResolvedVersion(String version) {
    myWrapee.setResolvedVersion(version);
  }

  @Override
  public void setArtifactHandler(ArtifactHandler handler) {
    myWrapee.setArtifactHandler(handler);
  }

  @Override
  public boolean isRelease() {
    return myWrapee.isRelease();
  }

  @Override
  public void setRelease(boolean release) {
    myWrapee.setRelease(release);
  }

  @Override
  public List<ArtifactVersion> getAvailableVersions() {
    return myWrapee.getAvailableVersions();
  }

  @Override
  public void setAvailableVersions(List<ArtifactVersion> versions) {
    myWrapee.setAvailableVersions(versions);
  }

  @Override
  public boolean isOptional() {
    return myWrapee.isOptional();
  }

  @Override
  public void setOptional(boolean optional) {
    myWrapee.setOptional(optional);
  }

  @Override
  public ArtifactVersion getSelectedVersion() throws OverConstrainedVersionException {
    return myWrapee.getSelectedVersion();
  }

  @Override
  public boolean isSelectedVersionKnown() throws OverConstrainedVersionException {
    return myWrapee.isSelectedVersionKnown();
  }

  @Override
  public int compareTo(Artifact o) {
    if (o instanceof CustomMaven3Artifact) o = ((CustomMaven3Artifact)o).myWrapee;
    return myWrapee.compareTo(o);
  }

  @Override
  public String toString() {
    return myWrapee.toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof CustomMaven3Artifact) obj = ((CustomMaven3Artifact)obj).myWrapee;
    return myWrapee.equals(obj);
  }

  @Override
  public int hashCode() {
    return myWrapee.hashCode();
  }
}
