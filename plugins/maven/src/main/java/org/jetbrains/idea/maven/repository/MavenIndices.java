package org.jetbrains.idea.maven.repository;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.*;
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
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.project.TransferListenerAdapter;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.state.MavenProjectsManager;
import org.sonatype.nexus.index.ArtifactContext;
import org.sonatype.nexus.index.ArtifactInfo;
import org.sonatype.nexus.index.ArtifactScanningListener;
import org.sonatype.nexus.index.NexusIndexer;
import org.sonatype.nexus.index.context.IndexContextInInconsistentStateException;
import org.sonatype.nexus.index.context.IndexingContext;
import org.sonatype.nexus.index.context.UnsupportedExistingLuceneIndexException;
import org.sonatype.nexus.index.scan.ScanningResult;
import org.sonatype.nexus.index.updater.IndexUpdater;

import java.io.*;
import java.util.*;

public class MavenIndices {
  protected static final String INDICES_LIST_FILE = "list.dat";
  protected static final String CACHES_DIR = "caches";
  protected static final String GROUP_IDS_FILE = "groupIds.dat";
  protected static final String ARTIFACT_IDS_FILE = "artifactIds.dat";
  protected static final String VERSIONS_FILE = "versions.dat";

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

  public void load() {
    try {
      File f = getListFile();
      if (!f.exists()) return;

      FileInputStream fs = new FileInputStream(f);

      try {
        DataInputStream is = new DataInputStream(fs);
        myIndicesData = new LinkedHashMap<MavenIndex, IndexData>();
        int size = is.readInt();
        while (size-- > 0) {
          MavenIndex i = new MavenIndex(is);
          try {
            add(i);
          }
          catch (MavenIndexException e) {
            MavenLog.LOG.info(e);
          }
        }
      }
      finally {
        fs.close();
      }
    }
    catch (Exception e) {
      MavenLog.LOG.info(e);

      try {
        try {
          closeOpenIndices();
        }
        catch (IOException e1) {
          MavenLog.LOG.info(e1);
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
    try {
      closeOpenIndices();
    }
    catch (IOException e) {
      MavenLog.LOG.info(e);
    }
  }

  private void closeOpenIndices() throws IOException {
    try {
      for (IndexData data : myIndicesData.values()) {
        closeIndexData(data);
      }
    }
    finally {
      myIndicesData.clear();
    }
  }

  public void add(MavenIndex i) throws MavenIndexException {
    try {
      doAdd(i);
    }
    catch (Exception e) {
      MavenLog.LOG.info(e);
      try {
        doAdd(i);
      }
      catch (Exception e1) {
        throw new MavenIndexException(e1);
      }
    }
  }

  private void doAdd(MavenIndex i) throws Exception {
    IndexData data = new IndexData();

    try {
      data.context = createContext(i);
      data.cache = createCache(i);
    }
    catch (Exception e) {
      try {
        try {
          closeIndexData(data);
        }
        catch (IOException e1) {
          MavenLog.LOG.info(e1);
        }
      }
      finally {
        clearIndexData(i);
      }

      throw e;
    }

    myIndicesData.put(i, data);
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

  public void update(MavenIndex i, Project project, ProgressIndicator progress) throws MavenIndexException,
                                                                                       ProcessCanceledException {
    try {
      updateIndexContext(i, progress);
      updateIndexCache(i, project, progress);
    }
    catch (IOException e) {
      throw new MavenIndexException(e);
    }
    catch (UnsupportedExistingLuceneIndexException e) {
      throw new MavenIndexException(e);
    }
  }

  private void updateIndexContext(MavenIndex i, ProgressIndicator progress)
      throws IOException, UnsupportedExistingLuceneIndexException, MavenIndexException {
    switch (i.getKind()) {
      case LOCAL:
        // NexusIndexer.scan does not overwrite an existing index, so we have to
        // remove it manually.
        remove(i);
        add(i);

        progress.setIndeterminate(true);
        myIndexer.scan(myIndicesData.get(i).context, new MyScanningListener(progress), false);
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

  private void updateIndexCache(MavenIndex index, Project project, ProgressIndicator progress) throws IOException {
    IndexData data = myIndicesData.get(index);

    progress.setText2("Updating caches...");

    Set<String> groupIds = new HashSet<String>();
    Map<String, List<String>> artifactIds = new HashMap<String, List<String>>();
    Map<String, List<String>> versions = new HashMap<String, List<String>>();

    if (index.getKind() == MavenIndex.Kind.PROJECT) {
      List<MavenProjectModel> projects = MavenProjectsManager.getInstance(project).getProjects();
      for (MavenProjectModel each : projects) {
        MavenId id = each.getMavenId();

        groupIds.add(id.groupId);
        getOrCreate(artifactIds, id.groupId).add(id.artifactId);
        getOrCreate(versions, id.groupId + ":" + id.artifactId).add(id.version);
      }
    }
    else {
      IndexReader r = data.context.getIndexReader();
      int total = r.numDocs();
      for (int i = 0; i < total; i++) {
        progress.setFraction(i / total);

        if (r.isDeleted(i)) continue;

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

    progress.startNonCancelableSection();
    try {
      progress.setText2("Saving caches...");
      data.cache.close();
      FileUtil.delete(getCacheDir(index));
      data.cache = createCache(index);

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
    finally {
      progress.finishNonCancelableSection();
    }
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
      try {
        closeIndexData(myIndicesData.remove(i));
      }
      finally {
        clearIndexData(i);
      }
    }
    catch (IOException e) {
      throw new MavenIndexException(e);
    }
  }

  private void closeIndexData(IndexData data) throws IOException {
    try {
      if (data.context != null) myIndexer.removeIndexingContext(data.context, false);
    }
    finally {
      if (data.cache != null) data.cache.close();
    }
  }

  private void clearIndexData(MavenIndex i) {
    FileUtil.delete(getIndexDir(i));
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
    return collectCaches(new CacheProcessor<Collection<String>>() {
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
    return collectCaches(new CacheProcessor<Collection<String>>() {
      public Collection<String> process(IndexCache cache) throws Exception {
        return cache.artifactIds.get(groupId);
      }
    });
  }

  public Set<String> getVersions(final String groupId, final String artifactId) throws MavenIndexException {
    return collectCaches(new CacheProcessor<Collection<String>>() {
      public Collection<String> process(IndexCache cache) throws Exception {
        return cache.versions.get(groupId + ":" + artifactId);
      }
    });
  }

  public boolean hasGroupId(final String groupId) throws MavenIndexException {
    return processCaches(new CacheProcessor<Boolean>() {
      public Boolean process(IndexCache cache) throws Exception {
        return cache.artifactIds.hasKey(groupId);
      }
    });
  }

  public boolean hasArtifactId(final String groupId, final String artifactId) throws MavenIndexException {
    return processCaches(new CacheProcessor<Boolean>() {
      public Boolean process(IndexCache cache) throws Exception {
        return cache.versions.hasKey(groupId + ":" + artifactId);
      }
    });
  }

  public boolean hasVersion(final String groupId, final String artifactId, final String version) throws MavenIndexException {
    return processCaches(new CacheProcessor<Boolean>() {
      public Boolean process(IndexCache cache) throws Exception {
        String key = groupId + ":" + artifactId;
        if (!cache.versions.hasKey(key)) return false;

        List<String> versions = cache.versions.get(key);
        return versions != null && versions.contains(version);
      }
    });
  }

  private Set<String> collectCaches(CacheProcessor<Collection<String>> p) throws MavenIndexException {
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

  private boolean processCaches(CacheProcessor<Boolean> p) throws MavenIndexException {
    try {
      for (final IndexData data : myIndicesData.values()) {
        if (data.cache == null) continue;
        if (p.process(data.cache)) return true;
      }
      return false;
    }
    catch (Exception e) {
      throw new MavenIndexException(e);
    }
  }

  private static interface CacheProcessor<T> {
    T process(IndexCache cache) throws Exception;
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
      try {
        if (groupIds != null) groupIds.close();
      }
      finally {
        try {
          if (artifactIds != null) artifactIds.close();
        }
        finally {
          if (versions != null) versions.close();
        }
      }
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

  private class MyScanningListener implements ArtifactScanningListener {
    private ProgressIndicator p;

    public MyScanningListener(ProgressIndicator progress) {
      p = progress;
    }

    public void scanningStarted(IndexingContext ctx) {
      p.checkCanceled();
      p.setText2("Starting...");
    }

    public void scanningFinished(IndexingContext ctx, ScanningResult result) {
      p.checkCanceled();
      p.setText2("Done");
    }

    public void artifactError(ArtifactContext ac, Exception e) {
    }

    public void artifactDiscovered(ArtifactContext ac) {
      p.checkCanceled();
      p.setText2("Indexing " + ac.getArtifact());
    }
  }
}