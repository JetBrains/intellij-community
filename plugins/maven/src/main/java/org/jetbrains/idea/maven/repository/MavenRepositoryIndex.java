package org.jetbrains.idea.maven.repository;

import com.intellij.openapi.progress.ProgressIndicator;
import org.apache.lucene.index.Term;
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

  private LinkedHashMap<IndexInfo, IndexingContext> myContexts = new LinkedHashMap<IndexInfo, IndexingContext>();

  public MavenRepositoryIndex(MavenEmbedder e, File indexDir) throws MavenRepositoryIndexException {
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

  private void load() throws MavenRepositoryIndexException {
    try {
      File f = getListFile();
      if (!f.exists()) return;

      FileInputStream fs = new FileInputStream(f);
      
      try {
        DataInputStream is = new DataInputStream(fs);
        myContexts = new LinkedHashMap<IndexInfo, IndexingContext>();
        int size = is.readInt();
        while (size-- > 0) {
          add(new IndexInfo(is));
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
        List<IndexInfo> infos = getIndexInfos();
        os.writeInt(infos.size());
        for (IndexInfo i : infos) {
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

  public void add(IndexInfo i) throws MavenRepositoryIndexException {
    try {
      myContexts.put(i, createContext(i));
    }
    catch (IOException e) {
      throw new MavenRepositoryIndexException(e);
    }
    catch (UnsupportedExistingLuceneIndexException e) {
      throw new MavenRepositoryIndexException(e);
    }
  }

  public void change(IndexInfo i, String id, String repositoryPathOrUrl, boolean isRemote) throws MavenRepositoryIndexException {
    try {
      IndexingContext c = createContext(new IndexInfo(id, repositoryPathOrUrl, isRemote));
      i.set(id, repositoryPathOrUrl, isRemote);
      IndexingContext oldContext = myContexts.get(i);
      myIndexer.removeIndexingContext(oldContext, false);
      myContexts.put(i, c);
    }
    catch (IOException e) {
      throw new MavenRepositoryIndexException(e);
    }
    catch (UnsupportedExistingLuceneIndexException e) {
      throw new MavenRepositoryIndexException(e);
    }
  }

  private IndexingContext createContext(IndexInfo newInfo) throws IOException, UnsupportedExistingLuceneIndexException {
    return myIndexer.addIndexingContext(
        newInfo.id,
        newInfo.id,
        newInfo.getRepositoryFile(),
        new File(myIndexDir, newInfo.id),
        newInfo.getRepositoryUrl(),
        null, // repo update url
        NexusIndexer.FULL_INDEX);
  }

  public void remove(IndexInfo i) throws MavenRepositoryIndexException {
    try {
      IndexingContext c = myContexts.remove(i);
      myIndexer.removeIndexingContext(c, false);
    }
    catch (IOException e) {
      throw new MavenRepositoryIndexException(e);
    }
  }


  public void update(IndexInfo i, ProgressIndicator progress) throws MavenRepositoryIndexException {
    try {
      IndexingContext c = myContexts.get(i);

      progress.setText("Updating [" + i.getId() + "]");
      if (i.isRemote) {
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
        myIndexer.scan(c);
      }
    }
    catch (IOException e) {
      throw new MavenRepositoryIndexException(e);
    }
    catch (UnsupportedExistingLuceneIndexException e) {
      throw new MavenRepositoryIndexException(e);
    }
  }

  public List<ArtifactInfo> find(String pattern) throws MavenRepositoryIndexException {
    try {
      WildcardQuery q = new WildcardQuery(new Term(ArtifactInfo.ARTIFACT_ID, pattern));
      Collection<ArtifactInfo> result = myIndexer.searchFlat(ArtifactInfo.VERSION_COMPARATOR, q);
      return new ArrayList<ArtifactInfo>(result);
    }
    catch (IOException e) {
      throw new MavenRepositoryIndexException(e);
    }
    catch (IndexContextInInconsistentStateException e) {
      throw new MavenRepositoryIndexException(e);
    }
  }

  public List<IndexInfo> getIndexInfos() {
    return new ArrayList<IndexInfo>(myContexts.keySet());
  }

  public static class IndexInfo {
    private String id;
    private String repositoryPathOrUrl;
    private boolean isRemote;

    public IndexInfo(String id, String repositoryPathOrUrl, boolean isRemote) {
      set(id, repositoryPathOrUrl, isRemote);
    }

    public void set(String id, String repositoryPathOrUrl, boolean isRemote) {
      this.id = id;
      this.repositoryPathOrUrl = repositoryPathOrUrl;
      this.isRemote = isRemote;
    }

    public IndexInfo(DataInputStream s) throws IOException {
      this(s.readUTF(), s.readUTF(),  s.readBoolean());
    }

    public void write(DataOutputStream s) throws IOException {
      s.writeUTF(id);
      s.writeUTF(repositoryPathOrUrl);
      s.writeBoolean(isRemote);
    }

    public String getId() {
      return id;
    }

    public File getRepositoryFile() {
      return isRemote ? null : new File(repositoryPathOrUrl);
    }

    public String getRepositoryUrl() {
      return isRemote ? repositoryPathOrUrl : null;
    }

    public boolean isRemote() {
      return isRemote;
    }
  }
}