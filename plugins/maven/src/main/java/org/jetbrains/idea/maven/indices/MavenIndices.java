package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.settings.Proxy;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.jetbrains.idea.maven.core.MavenLog;
import org.sonatype.nexus.index.NexusIndexer;
import org.sonatype.nexus.index.ArtifactContextProducer;
import org.sonatype.nexus.index.updater.IndexUpdater;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MavenIndices {
  private final MavenEmbedder myEmbedder;
  private final NexusIndexer myIndexer;
  private final IndexUpdater myUpdater;
  private final ArtifactContextProducer myArtifactContextProducer;

  private final File myIndicesDir;
  private final MavenIndex.IndexListener myListener;

  private final List<MavenIndex> myIndices = new ArrayList<MavenIndex>();
  private static final Object ourDirectoryLock = new Object();

  public MavenIndices(MavenEmbedder e, File indicesDir, MavenIndex.IndexListener listener) {
    myEmbedder = e;
    myIndicesDir = indicesDir;
    myListener = listener;

    PlexusContainer p = myEmbedder.getPlexusContainer();
    try {
      myIndexer = (NexusIndexer)p.lookup(NexusIndexer.class);
      myUpdater = (IndexUpdater)p.lookup(IndexUpdater.class);
      myArtifactContextProducer = (ArtifactContextProducer)p.lookup(ArtifactContextProducer.class);
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
        MavenIndex index = new MavenIndex(myIndexer, myArtifactContextProducer, each, myListener);
        if (findExisting(index.getRepositoryPathOrUrl(), index.getKind()) != null) {
          index.close();
          FileUtil.delete(each);
          continue;
        }
        myIndices.add(index);
      }
      catch (Exception e) {
        FileUtil.delete(each);
        MavenLog.LOG.warn(e);
      }
    }
  }

  public synchronized void close() {
    for (MavenIndex each : myIndices) {
      each.close();
    }
    myIndices.clear();
  }

  public synchronized List<MavenIndex> getIndices() {
    return new ArrayList<MavenIndex>(myIndices);
  }

  public synchronized MavenIndex add(String repositoryPathOrUrl, MavenIndex.Kind kind) throws MavenIndexException {
    MavenIndex index = findExisting(repositoryPathOrUrl, kind);
    if (index != null) return index;

    File dir = getAvailableIndexDir();
    index = new MavenIndex(myIndexer, myArtifactContextProducer, dir, repositoryPathOrUrl, kind, myListener);
    myIndices.add(index);
    return index;
  }

  private MavenIndex findExisting(String repositoryPathOrUrl, MavenIndex.Kind kind) {
    File file = kind == MavenIndex.Kind.LOCAL ? new File(repositoryPathOrUrl.trim()) : null;
    for (MavenIndex each : myIndices) {
      switch (kind) {
        case LOCAL:
          if (each.isForLocal(file)) return each;
          break;
        case REMOTE:
          if (each.isForRemote(repositoryPathOrUrl)) return each;
          break;
      }
    }
    return null;
  }

  private File getAvailableIndexDir() {
    return findAvailableDir(myIndicesDir, "Index", 1000);
  }

  static File findAvailableDir(File parent, String prefix, int max) {
    synchronized (ourDirectoryLock) {
      for (int i = 0; i < max; i++) {
        String name = prefix + i;
        File f = new File(parent, name);
        if (!f.exists()) {
          f.mkdirs();
          assert f.exists();
          return f;
        }
      }
      throw new RuntimeException("No available dir found");
    }
  }

  public void updateOrRepair(MavenIndex i, boolean fullUpdate, ProgressIndicator progress) throws ProcessCanceledException {
    i.updateOrRepair(myUpdater, getProxyInfo(), fullUpdate, progress);
  }

  private ProxyInfo getProxyInfo() {
    Proxy proxy = myEmbedder.getSettings().getActiveProxy();
    ProxyInfo result = null;
    if (proxy != null) {
      result = new ProxyInfo();
      result.setHost(proxy.getHost());
      result.setPort(proxy.getPort());
      result.setNonProxyHosts(proxy.getNonProxyHosts());
      result.setUserName(proxy.getUsername());
      result.setPassword(proxy.getPassword());
    }
    return result;
  }
}