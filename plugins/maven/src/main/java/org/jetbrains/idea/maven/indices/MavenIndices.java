package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import org.apache.maven.embedder.MavenEmbedder;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.jetbrains.idea.maven.core.MavenLog;
import org.sonatype.nexus.index.NexusIndexer;
import org.sonatype.nexus.index.updater.IndexUpdater;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MavenIndices {
  private MavenEmbedder myEmbedder;
  private NexusIndexer myIndexer;
  private IndexUpdater myUpdater;

  private File myIndicesDir;

  private List<MavenIndex> myIndices = new ArrayList<MavenIndex>();

  public MavenIndices(MavenEmbedder e, File indicesDir) {
    myEmbedder = e;
    myIndicesDir = indicesDir;

    PlexusContainer p = myEmbedder.getPlexusContainer();
    try {
      myIndexer = (NexusIndexer)p.lookup(NexusIndexer.class);
      myUpdater = (IndexUpdater)p.lookup(IndexUpdater.class);
    }
    catch (ComponentLookupException ex) {
      throw new RuntimeException(ex);
    }

    load();
  }

  private void load() {
    if (!myIndicesDir.exists()) return;

    File[] indices = myIndicesDir.listFiles();
    if (indices == null) return;
    for (File each : indices) {
      try {
        myIndices.add(new MavenIndex(each));
      }
      catch (Exception e) {
        FileUtil.delete(each);
        MavenLog.info(e);
      }
    }
  }

  public synchronized void close() {
    try {
      closeOpenIndices();
    }
    catch (MavenIndexException e) {
      MavenLog.info(e);
    }
  }

  private void closeOpenIndices() throws MavenIndexException {
    try {
      for (MavenIndex data : myIndices) {
        data.close();
      }
    }
    finally {
      myIndices.clear();
    }
  }

  public synchronized MavenIndex add(String repositoryPathOrUrl, MavenIndex.Kind kind) throws MavenIndexException {
    File dir = getAvailableIndexDir();
    MavenIndex index = new MavenIndex(dir, repositoryPathOrUrl, kind);
    myIndices.add(index);
    return index;
  }

  private File getAvailableIndexDir() {
    return findAvailableDir(myIndicesDir, "Index", 1000);
  }

  static File findAvailableDir(File parent, String prefix, int max) {
    for (int i = 0; i < max; i++) {
      String name = prefix + i;
      File f = new File(parent, name);
      if (!f.exists()) return f;
    }
    throw new RuntimeException("No available dir found");
  }

  public synchronized void remove(MavenIndex i) throws MavenIndexException {
    i.delete();
    myIndices.remove(i);
  }

  public void change(MavenIndex i, String repositoryPathOrUrl) throws MavenIndexException {
    i.change(repositoryPathOrUrl);
  }

  public void update(MavenIndex i, ProgressIndicator progress) throws MavenIndexException,
                                                                      ProcessCanceledException {
    i.update(myEmbedder, myIndexer, myUpdater, progress);
  }

  public void repair(MavenIndex i, ProgressIndicator progress) throws MavenIndexException,
                                                                      ProcessCanceledException {
    i.repair(myIndexer, progress);
  }

  public synchronized List<MavenIndex> getIndices() {
    return new ArrayList<MavenIndex>(myIndices);
  }

  public Set<String> getGroupIds() throws MavenIndexException {
    Set<String> result = new HashSet<String>();
    for (MavenIndex each : getIndices()) {
      result.addAll(each.getGroupIds());
    }
    return result;
  }

  public Set<String> getArtifactIds(final String groupId) throws MavenIndexException {
    Set<String> result = new HashSet<String>();
    for (MavenIndex each : getIndices()) {
      result.addAll(each.getArtifactIds(groupId));
    }
    return result;
  }

  public Set<String> getVersions(final String groupId, final String artifactId) throws MavenIndexException {
    Set<String> result = new HashSet<String>();
    for (MavenIndex each : getIndices()) {
      result.addAll(each.getVersions(groupId, artifactId));
    }
    return result;
  }

  public boolean hasGroupId(final String groupId) throws MavenIndexException {
    for (MavenIndex each : getIndices()) {
      if (each.hasGroupId(groupId)) return true;
    }
    return false;
  }

  public boolean hasArtifactId(final String groupId, final String artifactId) throws MavenIndexException {
    for (MavenIndex each : getIndices()) {
      if (each.hasArtifactId(groupId, artifactId)) return true;
    }
    return false;
  }

  public boolean hasVersion(final String groupId, final String artifactId, final String version) throws MavenIndexException {
    for (MavenIndex each : getIndices()) {
      if (each.hasVersion(groupId, artifactId, version)) return true;
    }
    return false;
  }
}