package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import org.apache.maven.embedder.MavenEmbedder;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.jetbrains.idea.maven.core.MavenLog;
import org.sonatype.nexus.index.NexusIndexer;
import org.sonatype.nexus.index.updater.IndexUpdater;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        catch (MavenIndexException e1) {
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

  public synchronized void save() throws MavenIndexException {
    try {
      getListFile().getParentFile().mkdirs();
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
      throw new MavenIndexException(e);
    }
  }

  private File getListFile() {
    return new File(myIndicesDir, INDICES_LIST_FILE);
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

  public synchronized void add(MavenIndex i) throws MavenIndexException {
    i.open(myIndicesDir);
    myIndices.add(i);
  }

  public synchronized void remove(MavenIndex i) throws MavenIndexException {
    i.remove();
    myIndices.remove(i);
  }

  public void change(MavenIndex i, String repositoryPathOrUrl) throws MavenIndexException {
    i.change(repositoryPathOrUrl);
  }

  public void update(MavenIndex i, Project project, ProgressIndicator progress) throws MavenIndexException,
                                                                                       ProcessCanceledException {
    i.update(project, myEmbedder, myIndexer, myUpdater, progress);
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