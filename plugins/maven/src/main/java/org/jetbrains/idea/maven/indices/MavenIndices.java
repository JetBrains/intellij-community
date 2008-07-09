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
import java.util.List;

public class MavenIndices {
  private final MavenEmbedder myEmbedder;
  private final NexusIndexer myIndexer;
  private final IndexUpdater myUpdater;

  private final File myIndicesDir;

  private final List<MavenIndex> myIndices = new ArrayList<MavenIndex>();

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
        MavenLog.warn(e);
      }
    }
  }

  public synchronized void close() {
    try {
      for (MavenIndex data : myIndices) {
        try {
          data.close();
        }
        catch (MavenIndexException e) {
          MavenLog.warn(e);
        }
      }
    }
    finally {
      myIndices.clear();
    }
  }

  public synchronized List<MavenIndex> getIndices() {
    return new ArrayList<MavenIndex>(myIndices);
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

  public synchronized void remove(MavenIndex i) {
    myIndices.remove(i);
    try {
      i.delete();
    }
    catch (MavenIndexException e) {
      MavenLog.warn(e);
    }
  }

  public void update(MavenIndex i, ProgressIndicator progress) throws ProcessCanceledException {
    i.update(myEmbedder, myIndexer, myUpdater, progress);
  }
}