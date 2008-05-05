package org.jetbrains.idea.maven.repository;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.*;
import com.intellij.util.xml.DomFileElement;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.WildcardQuery;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.settings.Proxy;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.dom.PomDescriptor;
import org.jetbrains.idea.maven.dom.model.MavenModel;
import org.jetbrains.idea.maven.project.TransferListenerAdapter;
import org.sonatype.nexus.index.ArtifactInfo;
import org.sonatype.nexus.index.NexusIndexer;
import org.sonatype.nexus.index.context.IndexContextInInconsistentStateException;
import org.sonatype.nexus.index.context.IndexingContext;
import org.sonatype.nexus.index.context.UnsupportedExistingLuceneIndexException;
import org.sonatype.nexus.index.updater.IndexUpdater;

import java.io.*;
import java.util.*;

public class MavenIndices {
  protected static final String INDICES_LIST_FILE = "list.dat";
  private static final String CACHES_DIR = "caches";
  private static final String GROUP_IDS_FILE = "groupIds.dat";
  private static final String ARTIFACT_IDS_FILE = "artifactIds.dat";
  private static final String VERSIONS_FILE = "versions.dat";

  private MavenEmbedder myEmbedder;
  private NexusIndexer myIndexer;

  private IndexUpdater myUpdater;
  private File myIndicesDir;
  private LinkedHashMap<MavenIndex, IndexData> myIndicesData = new LinkedHashMap<MavenIndex, IndexData>();

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

  public void load() throws MavenIndexException {
    try {
      try {
        File f = getListFile();
        if (!f.exists()) return;

        FileInputStream fs = new FileInputStream(f);

        try {
          DataInputStream is = new DataInputStream(fs);
          myIndicesData = new LinkedHashMap<MavenIndex, IndexData>();
          int size = is.readInt();
          while (size-- > 0) {
            add(new MavenIndex(is));
          }
        }
        finally {
          fs.close();
        }
      }
      catch (Exception e) {
        throw new MavenIndexException(e);
      }
    }
    catch (MavenIndexException e) {
      closeOpenIndices();
      clearIndices();

      throw e;
    }
  }

  private void clearIndices() {
    FileUtil.delete(myIndicesDir);
  }

  public void save() {
    try {
      FileOutputStream fs = new FileOutputStream(getListFile());
      try {
        DataOutputStream os = new DataOutputStream(fs);
        List<MavenIndex> infos = getIndices();
        os.writeInt(infos.size());
        for (MavenIndex i : infos) {
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

  public void close() {
    closeOpenIndices();
  }

  private void closeOpenIndices() {
    try {
      for (IndexData data : myIndicesData.values()) {
        closeIndexData(data);
      }
      myIndicesData.clear();
    }
    catch (IOException e) {
      // shouldn't throw any exception, since we are not deleting the
      // content from the disk.
      throw new RuntimeException(e);
    }
  }

  public void add(MavenIndex i) throws MavenIndexException {
    try {
      IndexData data = new IndexData();
      data.context = createContext(i);
      data.cache = createCache(i);
      myIndicesData.put(i, data);
    }
    catch (IOException e) {
      throw new MavenIndexException(e);
    }
    catch (UnsupportedExistingLuceneIndexException e) {
      throw new MavenIndexException(e);
    }
  }

  private IndexingContext createContext(MavenIndex i) throws IOException, UnsupportedExistingLuceneIndexException {
    return myIndexer
        .addIndexingContext(i.getId(),
                            i.getId(),
                            i.getRepositoryFile(),
                            getIndexDir(i),
                            i.getRepositoryUrl(),
                            null, // repo update url
                            NexusIndexer.FULL_INDEX);
  }

  private IndexCache createCache(MavenIndex i) throws IOException {
    File cacheDir = getCacheDir(i);
    cacheDir.mkdirs();
    return new IndexCache(cacheDir);
  }

  private File getIndexDir(MavenIndex i) {
    return new File(myIndicesDir, i.getId());
  }

  private File getCacheDir(MavenIndex info) {
    return new File(getIndexDir(info), CACHES_DIR);
  }

  public void change(MavenIndex i, String id, String repositoryPathOrUrl) throws MavenIndexException {
    remove(i);

    i.set(id, repositoryPathOrUrl, i.getKind());
    add(i);
  }

  public void update(MavenIndex i, Project project, ProgressIndicator progress) throws MavenIndexException {
    try {
      updateIndexContext(i, project, progress);
      updateIndexCache(i, project);
    }
    catch (IOException e) {
      throw new MavenIndexException(e);
    }
    catch (UnsupportedExistingLuceneIndexException e) {
      throw new MavenIndexException(e);
    }
  }

  private void updateIndexContext(MavenIndex i, Project project, ProgressIndicator progress)
      throws IOException, UnsupportedExistingLuceneIndexException, MavenIndexException {
    switch (i.getKind()) {
      case LOCAL:
        progress.setIndeterminate(true);
        // NexusIndexer.scan does not overwrite an existing index, so we have to
        // remove it manually.
        remove(i);
        add(i);

        myIndexer.scan(myIndicesData.get(i).context);
        return;
      case REMOTE:
        Proxy proxy = myEmbedder.getSettings().getActiveProxy();
        ProxyInfo proxyInfo = null;
        if (proxy != null) {
          proxyInfo = new ProxyInfo();
          proxyInfo.setHost(proxy.getHost());
          proxyInfo.setPort(proxy.getPort());
          proxyInfo.setNonProxyHosts(proxy.getNonProxyHosts());
          proxyInfo.setUserName(proxy.getUsername());
          proxyInfo.setPassword(proxy.getPassword());
        }
        progress.setIndeterminate(false);

        IndexingContext c = myIndicesData.get(i).context;
        myUpdater.fetchAndUpdateIndex(c, new TransferListenerAdapter(progress), proxyInfo);
        return;
    }
  }

  private void updateIndexCache(MavenIndex index, Project project) throws IOException {
    MavenIndices.IndexData data = myIndicesData.get(index);

    data.cache.close();
    FileUtil.delete(getCacheDir(index));
    data.cache = createCache(index);

    Set<String> groupIds = new HashSet<String>();
    Map<String, List<String>> artifactIds = new HashMap<String, List<String>>();
    Map<String, List<String>> versions = new HashMap<String, List<String>>();

    if (index.getKind() == MavenIndex.Kind.PROJECT) {
      List<DomFileElement<MavenModel>> poms = PomDescriptor.collectProjectPoms(project);
      for (DomFileElement<MavenModel> each : poms) {
        MavenId id = PomDescriptor.describe(each);

        groupIds.add(id.groupId);
        getOrCreate(artifactIds, id.groupId).add(id.artifactId);
        getOrCreate(versions, id.groupId + ":" + id.artifactId).add(id.version);
      }
    } else {
      IndexReader r = data.context.getIndexReader();
      for (int i = 0; i < r.numDocs(); i++) {
        Document doc = r.document(i);
        String uinfo = doc.get(ArtifactInfo.UINFO);
        if (uinfo == null) continue;

        List<String> parts = StringUtil.split(uinfo, "|");
        String groupId = parts.get(0);
        String artifactId = parts.get(1);
        String version = parts.get(2);

        groupIds.add(groupId);
        getOrCreate(artifactIds, groupId).add(artifactId);
        getOrCreate(versions, groupId + ":" + artifactId).add(version);
      }
    }

    for (String each : groupIds) {
      data.cache.groupIds.enumerate(each);
    }

    for (Map.Entry<String, List<String>> each : artifactIds.entrySet()) {
      data.cache.artifactIds.put(each.getKey(), each.getValue());
    }

    for (Map.Entry<String, List<String>> each : versions.entrySet()) {
      data.cache.versions.put(each.getKey(), each.getValue());
    }

    data.cache.groupIds.flush();
    data.cache.artifactIds.flush();
    data.cache.versions.flush();
  }

  private List<String> getOrCreate(Map<String, List<String>> map, String key) {
    List<String> result = map.get(key);
    if (result == null) {
      result = new ArrayList<String>();
      map.put(key, result);
    }
    return result;
  }

  public void remove(MavenIndex i) throws MavenIndexException {
    try {
      closeIndexData(myIndicesData.remove(i));
      FileUtil.delete(getIndexDir(i));
    }
    catch (IOException e) {
      throw new MavenIndexException(e);
    }
  }

  private void closeIndexData(IndexData data) throws IOException {
    myIndexer.removeIndexingContext(data.context, false);
    data.cache.close();
  }

  public List<MavenIndex> getIndices() {
    return new ArrayList<MavenIndex>(myIndicesData.keySet());
  }

  public List<ArtifactInfo> findByGroupId(String pattern) throws MavenIndexException {
    return doFind(pattern, ArtifactInfo.GROUP_ID);
  }

  public List<ArtifactInfo> findByArtifactId(String pattern) throws MavenIndexException {
    return doFind(pattern, ArtifactInfo.ARTIFACT_ID);
  }

  private List<ArtifactInfo> doFind(String pattern, String term) throws MavenIndexException {
    try {
      Query q = new WildcardQuery(new Term(term, pattern));
      Collection<ArtifactInfo> result = myIndexer.searchFlat(ArtifactInfo.VERSION_COMPARATOR, q);
      return new ArrayList<ArtifactInfo>(result);
    }
    catch (IOException e) {
      throw new MavenIndexException(e);
    }
    catch (IndexContextInInconsistentStateException e) {
      throw new MavenIndexException(e);
    }
  }

  public Collection<ArtifactInfo> search(Query q) throws MavenIndexException {
    try {
      return myIndexer.searchFlat(q);
    }
    catch (IOException e) {
      throw new MavenIndexException(e);
    }
    catch (IndexContextInInconsistentStateException e) {
      throw new MavenIndexException(e);
    }
  }

  public Set<String> getGroupIds() throws MavenIndexException {
    return processCaches(new CacheProcessor() {
      public Collection<String> process(final IndexCache cache) throws Exception {
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
    return processCaches(new CacheProcessor() {
      public Collection<String> process(IndexCache cache) throws Exception {
        return cache.artifactIds.get(groupId);
      }
    });
  }

  public Set<String> getVersions(final String groupId, final String artifactId) throws MavenIndexException {
    return processCaches(new CacheProcessor() {
      public Collection<String> process(IndexCache cache) throws Exception {
        return cache.versions.get(groupId + ":" + artifactId);
      }
    });
  }

  private Set<String> processCaches(CacheProcessor p) throws MavenIndexException {
    try {
      final Set<String> result = new HashSet<String>();
      for (final IndexData data : myIndicesData.values()) {
        if (data.cache == null) continue;
        Collection<String> values = p.process(data.cache);
        if (values != null) result.addAll(values);
      }
      return result;
    }
    catch (Exception e) {
      throw new MavenIndexException(e);
    }
  }

  private static interface CacheProcessor {
    Collection<String> process(IndexCache cache) throws Exception;
  }

  private static class IndexData {
    IndexingContext context;
    IndexCache cache;
  }

  private static class IndexCache {
    PersistentStringEnumerator groupIds;
    PersistentHashMap<String, List<String>> artifactIds;
    PersistentHashMap<String, List<String>> versions;

    public IndexCache(File cacheDir) throws IOException {
      groupIds = new PersistentStringEnumerator(new File(cacheDir, GROUP_IDS_FILE));
      artifactIds = createPersistentMap(new File(cacheDir, ARTIFACT_IDS_FILE));
      versions = createPersistentMap(new File(cacheDir, VERSIONS_FILE));
    }

    private PersistentHashMap<String, List<String>> createPersistentMap(File f) throws IOException {
      return new PersistentHashMap<String, List<String>>(f, new EnumeratorStringDescriptor(), new EnumeratorListDescriptor());
    }

    public void close() throws IOException {
      groupIds.close();
      artifactIds.close();
      versions.close();
    }
  }

  private static class EnumeratorListDescriptor implements DataExternalizer<List<String>> {
    public void save(DataOutput s, List<String> list) throws IOException {
      s.writeInt(list.size());
      for (String each : list) {
        s.writeUTF(each);
      }
    }

    public List<String> read(DataInput s) throws IOException {
      List<String> result = new ArrayList<String>();
      int count = s.readInt();
      while (count-- > 0) {
        result.add(s.readUTF());
      }
      return result;
    }
  }
}