package org.jetbrains.idea.maven.repository;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.PersistentEnumerator;
import org.apache.maven.embedder.MavenEmbedder;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.jetbrains.idea.maven.core.MavenLog;
import org.sonatype.nexus.index.NexusIndexer;
import org.sonatype.nexus.index.updater.IndexUpdater;

import java.io.*;
import java.util.*;

public class MavenIndices {
  protected static final String INDICES_LIST_FILE = "list.dat";

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
  }

  public synchronized void load() {
    try {
      File f = getListFile();
      if (!f.exists()) return;

      FileInputStream fs = new FileInputStream(f);

      try {
        DataInputStream is = new DataInputStream(fs);
        myIndices = new ArrayList<MavenIndex>();
        int size = is.readInt();
        while (size-- > 0) {
          MavenIndex i = MavenIndex.read(is);
          try {
            add(i);
          }
          catch (MavenIndexException e) {
            MavenLog.info(e);
          }
        }
      }
      finally {
        fs.close();
      }
    }
    catch (Exception e) {
      MavenLog.info(e);

      try {
        try {
          closeOpenIndices();
        }
        catch (IOException e1) {
          MavenLog.info(e1);
        }
      }
      finally {
        clearIndices();
      }
    }
  }

  private void clearIndices() {
    FileUtil.delete(myIndicesDir);
  }

  public synchronized void save() {
    try {
      FileOutputStream fs = new FileOutputStream(getListFile());
      try {
        DataOutputStream os = new DataOutputStream(fs);
        os.writeInt(myIndices.size());
        for (MavenIndex i : myIndices) {
          i.write(os);
        }
      }
      finally {
        fs.close();
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private File getListFile() {
    return new File(myIndicesDir, INDICES_LIST_FILE);
  }

  public synchronized void close() {
    try {
      closeOpenIndices();
    }
    catch (IOException e) {
      MavenLog.info(e);
    }
  }

  private void closeOpenIndices() throws IOException {
    try {
      for (MavenIndex data : myIndices) {
        data.close();
      }
    }
    finally {
      myIndices.clear();
    }
  }

  public synchronized void add(MavenIndex i) throws MavenIndexException {
    i.open(myIndicesDir);
    myIndices.add(i);
  }

  public synchronized void remove(MavenIndex i) throws MavenIndexException {
    i.remove();
    myIndices.remove(i);
  }

  public void change(MavenIndex i, String id, String repositoryPathOrUrl) throws MavenIndexException {
    i.change(id, repositoryPathOrUrl);
  }

  public void update(MavenIndex i, Project project, ProgressIndicator progress) throws MavenIndexException,
                                                                                       ProcessCanceledException {
    i.update(project, myEmbedder, myIndexer, myUpdater, progress);
  }

  public synchronized List<MavenIndex> getIndices() {
    return new ArrayList<MavenIndex>(myIndices);
  }

  public Set<String> getGroupIds() throws MavenIndexException {
    return collectCaches(new MavenIndex.DataProcessor<Collection<String>>() {
      public Collection<String> process(final MavenIndex.IndexData cache) throws Exception {
        final Set<String> result = new HashSet<String>();

        cache.groupIds.traverseAllRecords(new PersistentEnumerator.RecordsProcessor() {
          public void process(int record) throws IOException {
            result.add(cache.groupIds.valueOf(record));
          }
        });

        return result;
      }
    });
  }

  public Set<String> getArtifactIds(final String groupId) throws MavenIndexException {
    return collectCaches(new MavenIndex.DataProcessor<Collection<String>>() {
      public Collection<String> process(MavenIndex.IndexData cache) throws Exception {
        return cache.artifactIds.get(groupId);
      }
    });
  }

  public Set<String> getVersions(final String groupId, final String artifactId) throws MavenIndexException {
    return collectCaches(new MavenIndex.DataProcessor<Collection<String>>() {
      public Collection<String> process(MavenIndex.IndexData cache) throws Exception {
        return cache.versions.get(groupId + ":" + artifactId);
      }
    });
  }

  public boolean hasGroupId(final String groupId) throws MavenIndexException {
    return processCaches(new MavenIndex.DataProcessor<Boolean>() {
      public Boolean process(MavenIndex.IndexData cache) throws Exception {
        return cache.artifactIds.hasKey(groupId);
      }
    });
  }

  public boolean hasArtifactId(final String groupId, final String artifactId) throws MavenIndexException {
    return processCaches(new MavenIndex.DataProcessor<Boolean>() {
      public Boolean process(MavenIndex.IndexData cache) throws Exception {
        return cache.versions.hasKey(groupId + ":" + artifactId);
      }
    });
  }

  public boolean hasVersion(final String groupId, final String artifactId, final String version) throws MavenIndexException {
    return processCaches(new MavenIndex.DataProcessor<Boolean>() {
      public Boolean process(MavenIndex.IndexData cache) throws Exception {
        String key = groupId + ":" + artifactId;
        if (!cache.versions.hasKey(key)) return false;

        Set<String> versions = cache.versions.get(key);
        return versions != null && versions.contains(version);
      }
    });
  }

  private Set<String> collectCaches(MavenIndex.DataProcessor<Collection<String>> p) throws MavenIndexException {
    try {
      Set<String> result = new HashSet<String>();
      for (MavenIndex each : getIndices()) {
        Collection<String> values = each.process(p);
        if (values != null) result.addAll(values);
      }
      return result;
    }
    catch (Exception e) {
      throw new MavenIndexException(e);
    }
  }

  private boolean processCaches(MavenIndex.DataProcessor<Boolean> p) throws MavenIndexException {
    try {
      for (MavenIndex each : myIndices) {
        if (each.process(p)) return true;
      }
      return false;
    }
    catch (Exception e) {
      throw new MavenIndexException(e);
    }
  }
}