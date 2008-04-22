package org.jetbrains.idea.maven.repository;

import com.intellij.openapi.progress.ProgressIndicator;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.WildcardQuery;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.settings.Proxy;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.jetbrains.idea.maven.project.TransferListenerAdapter;
import org.sonatype.nexus.index.ArtifactInfo;
import org.sonatype.nexus.index.NexusIndexer;
import org.sonatype.nexus.index.context.IndexContextInInconsistentStateException;
import org.sonatype.nexus.index.context.IndexingContext;
import org.sonatype.nexus.index.context.UnsupportedExistingLuceneIndexException;
import org.sonatype.nexus.index.updater.IndexUpdater;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

public class MavenRepositoryIndex {
  private MavenEmbedder myEmbedder;
  private NexusIndexer myIndexer;
  private IndexUpdater myUpdater;
  private File myIndexDir;

  private LinkedHashMap<MavenRepositoryInfo, IndexingContext> myContexts = new LinkedHashMap<MavenRepositoryInfo, IndexingContext>();

  public MavenRepositoryIndex(MavenEmbedder e, File indexDir) throws MavenRepositoryException {
    myEmbedder = e;
    myIndexDir = indexDir;

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

  private void load() throws MavenRepositoryException {
    try {
      File f = getListFile();
      if (!f.exists()) return;

      FileInputStream fs = new FileInputStream(f);
      
      try {
        DataInputStream is = new DataInputStream(fs);
        myContexts = new LinkedHashMap<MavenRepositoryInfo, IndexingContext>();
        int size = is.readInt();
        while (size-- > 0) {
          add(new MavenRepositoryInfo(is));
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

  public void save() {
    try {
      FileOutputStream fs = new FileOutputStream(getListFile());
      try {
        DataOutputStream os = new DataOutputStream(fs);
        List<MavenRepositoryInfo> infos = getInfos();
        os.writeInt(infos.size());
        for (MavenRepositoryInfo i : infos) {
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
    return new File(myIndexDir, "list.dat");
  }

  public void close() {
    try {
      Collection<IndexingContext> contexts = myIndexer.getIndexingContexts().values();
      for (IndexingContext c : new ArrayList<IndexingContext>(contexts)) {
        myIndexer.removeIndexingContext(c, false);
      }
    }
    catch (IOException e) {
      // shouldn't throw any exception, since we are not deleting the
      // content from the disk.
      throw new RuntimeException(e);
    }
  }

  public void add(MavenRepositoryInfo i) throws MavenRepositoryException {
    try {
      myContexts.put(i, createContext(i));
    }
    catch (IOException e) {
      throw new MavenRepositoryException(e);
    }
    catch (UnsupportedExistingLuceneIndexException e) {
      throw new MavenRepositoryException(e);
    }
  }

  public void change(MavenRepositoryInfo i, String id, String repositoryPathOrUrl, boolean isRemote) throws MavenRepositoryException {
    try {
      IndexingContext c = createContext(new MavenRepositoryInfo(id, repositoryPathOrUrl, isRemote));
      i.set(id, repositoryPathOrUrl, isRemote);
      IndexingContext oldContext = myContexts.get(i);
      deleteContext(oldContext);
      myContexts.put(i, c);
    }
    catch (IOException e) {
      throw new MavenRepositoryException(e);
    }
    catch (UnsupportedExistingLuceneIndexException e) {
      throw new MavenRepositoryException(e);
    }
  }

  private IndexingContext createContext(MavenRepositoryInfo i) throws IOException, UnsupportedExistingLuceneIndexException {
    return myIndexer.addIndexingContext(
        i.getId(),
        i.getId(),
        i.getRepositoryFile(),
        new File(myIndexDir, i.getId()),
        i.getRepositoryUrl(),
        null, // repo update url
        NexusIndexer.FULL_INDEX);
  }

  public void remove(MavenRepositoryInfo i) throws MavenRepositoryException {
    try {
      IndexingContext c = myContexts.remove(i);
      deleteContext(c);
    }
    catch (IOException e) {
      throw new MavenRepositoryException(e);
    }
  }

  public void update(MavenRepositoryInfo i, ProgressIndicator progress) throws MavenRepositoryException {
    try {
      progress.setText("Updating [" + i.getId() + "]");
      if (i.isRemote()) {
        IndexingContext c = myContexts.get(i);

        Proxy proxy = myEmbedder.getSettings().getActiveProxy();
        ProxyInfo proxyInfo = null;
        if(proxy != null) {
          proxyInfo = new ProxyInfo();
          proxyInfo.setHost(proxy.getHost());
          proxyInfo.setPort(proxy.getPort());
          proxyInfo.setNonProxyHosts(proxy.getNonProxyHosts());
          proxyInfo.setUserName(proxy.getUsername());
          proxyInfo.setPassword(proxy.getPassword());
        }
        progress.setIndeterminate(false);
        myUpdater.fetchAndUpdateIndex(c, new TransferListenerAdapter(progress), proxyInfo);
      } else {
        progress.setIndeterminate(true);
        deleteContext(myContexts.get(i));
        IndexingContext c = createContext(i);
        myContexts.put(i, c);
        myIndexer.scan(c);
      }
    }
    catch (IOException e) {
      throw new MavenRepositoryException(e);
    }
    catch (UnsupportedExistingLuceneIndexException e) {
      throw new MavenRepositoryException(e);
    }
  }

  private void deleteContext(IndexingContext c) throws IOException {
    myIndexer.removeIndexingContext(c, true);
  }

  public List<MavenRepositoryInfo> getInfos() {
    return new ArrayList<MavenRepositoryInfo>(myContexts.keySet());
  }

  public List<ArtifactInfo> findByGroupId(String pattern) throws MavenRepositoryException {
    return doFind(pattern, ArtifactInfo.GROUP_ID);
  }

  public List<ArtifactInfo> findByArtifactId(String pattern) throws MavenRepositoryException {
    return doFind(pattern, ArtifactInfo.ARTIFACT_ID);
  }

  private List<ArtifactInfo> doFind(String pattern, String term) throws MavenRepositoryException {
    try {
      Query q = new WildcardQuery(new Term(term, pattern));
      Collection<ArtifactInfo> result = myIndexer.searchFlat(ArtifactInfo.VERSION_COMPARATOR, q);
      return new ArrayList<ArtifactInfo>(result);
    }
    catch (IOException e) {
      throw new MavenRepositoryException(e);
    }
    catch (IndexContextInInconsistentStateException e) {
      throw new MavenRepositoryException(e);
    }
  }
}