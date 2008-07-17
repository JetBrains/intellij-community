package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.*;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.settings.Proxy;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.project.TransferListenerAdapter;
import org.sonatype.nexus.index.ArtifactContext;
import org.sonatype.nexus.index.ArtifactInfo;
import org.sonatype.nexus.index.ArtifactScanningListener;
import org.sonatype.nexus.index.NexusIndexer;
import org.sonatype.nexus.index.context.IndexingContext;
import org.sonatype.nexus.index.context.UnsupportedExistingLuceneIndexException;
import org.sonatype.nexus.index.scan.ScanningResult;
import org.sonatype.nexus.index.updater.IndexUpdater;

import java.io.*;
import java.util.*;

public class MavenIndex {
  protected static final String INDEX_INFO_FILE = "index.properties";

  private static final String KIND_KEY = "kind";
  private static final String PATH_OR_URL_KEY = "pathOrUrl";
  private static final String TIMESTAMP_KEY = "lastUpdate";
  private static final String DATA_DIR_NAME_KEY = "dataDirName";
  private static final String FAILURE_MESSAGE_KEY = "failureMessage";

  private static final String CONTEXT_DIR = "context";

  private static final String DATA_DIR_PREFIX = "data";
  private static final String GROUP_IDS_FILE = "groupIds.dat";
  private static final String ARTIFACT_IDS_FILE = "artifactIds.dat";

  private static final String VERSIONS_FILE = "versions.dat";
  private static final String ARTIFACT_IDS_MAP_FILE = "artifactIds-map.dat";
  private static final String VERSIONS_MAP_FILE = "versions-map.dat";

  public enum Kind {
    LOCAL, REMOTE
  }

  private final File myDir;

  private final String myRepositoryPathOrUrl;
  private final Kind myKind;
  private Long myUpdateTimestamp;

  private String myDataDirName;
  private IndexData myData;

  private String myFailureMessage;

  public MavenIndex(File dir, String repositoryPathOrUrl, Kind kind) throws MavenIndexException {
    myDir = dir;
    myRepositoryPathOrUrl = repositoryPathOrUrl;
    myKind = kind;

    open();
    save();
  }

  public MavenIndex(File dir) throws MavenIndexException {
    myDir = dir;

    Properties props = new Properties();
    try {
      FileInputStream s = new FileInputStream(new File(dir, INDEX_INFO_FILE));
      try {
        props.load(s);
      }
      finally {
        s.close();
      }
    }
    catch (IOException e) {
      throw new MavenIndexException("Cannot read " + INDEX_INFO_FILE + " file", e);
    }

    myKind = Kind.valueOf(props.getProperty(KIND_KEY));
    myRepositoryPathOrUrl = props.getProperty(PATH_OR_URL_KEY);

    try {
      String timestamp = props.getProperty(TIMESTAMP_KEY);
      if (timestamp != null) myUpdateTimestamp = Long.parseLong(timestamp);
    }
    catch (Exception e) {
    }

    myDataDirName = props.getProperty(DATA_DIR_NAME_KEY);
    myFailureMessage = props.getProperty(FAILURE_MESSAGE_KEY);

    open();
  }

  private synchronized void save() {
    myDir.mkdirs();

    Properties props = new Properties();

    props.setProperty(KIND_KEY, myKind.toString());
    props.setProperty(PATH_OR_URL_KEY, myRepositoryPathOrUrl);
    if (myUpdateTimestamp != null) props.setProperty(TIMESTAMP_KEY, String.valueOf(myUpdateTimestamp));
    props.setProperty(DATA_DIR_NAME_KEY, myDataDirName);
    if (myFailureMessage != null) props.setProperty(FAILURE_MESSAGE_KEY, myFailureMessage);

    try {
      FileOutputStream s = new FileOutputStream(new File(myDir, INDEX_INFO_FILE));
      try {
        props.store(s, null);
      }
      finally {
        s.close();
      }
    }
    catch (IOException e) {
      MavenLog.info(e);
    }
  }

  private void open() throws MavenIndexException {
    try {
      doOpen();
    }
    catch (Exception e1) {
      MavenLog.info(e1);
      try {
        doOpen();
      }
      catch (Exception e2) {
        throw new MavenIndexException("Cannot open index " + myDir.getPath(), e2);
      }
    }
  }

  private void doOpen() throws Exception {
    try {
      if (myDataDirName == null) {
        myDataDirName = findAvailableDataDir();
      }
      myData = openData(myDataDirName);
    }
    catch (Exception e) {
      try {
        close();
      }
      catch (Exception ignore) {
      }
      FileUtil.delete(getCurrentDataDir());
      myUpdateTimestamp = null;
      throw e;
    }
  }

  public File getRepositoryFile() {
    return myKind == Kind.LOCAL ? new File(myRepositoryPathOrUrl) : null;
  }

  public String getRepositoryUrl() {
    return myKind == Kind.REMOTE ? myRepositoryPathOrUrl : null;
  }

  public String getRepositoryPathOrUrl() {
    return myRepositoryPathOrUrl;
  }

  public Kind getKind() {
    return myKind;
  }

  public synchronized long getUpdateTimestamp() {
    return myUpdateTimestamp == null ? -1 : myUpdateTimestamp;
  }

  public String getFailureMessage() {
    return myFailureMessage;
  }

  public synchronized void close() throws MavenIndexException {
    try {
      if (myData != null) myData.close();
    }
    catch (IOException e) {
      throw new MavenIndexException(e);
    }
  }

  public synchronized void delete() throws MavenIndexException {
    try {
      close();
    }
    finally {
      FileUtil.delete(myDir);
    }
  }

  public void update(MavenEmbedder embedder,
                     NexusIndexer indexer,
                     IndexUpdater updater,
                     ProgressIndicator progress) throws ProcessCanceledException {
    try {
      if (myKind == Kind.LOCAL) {
        FileUtil.delete(getContextDir());
      }
      IndexingContext context = createContext(indexer);
      try {
        updateContext(context, embedder, indexer, updater, progress);
        updateData(context, progress);
      }
      finally {
        indexer.removeIndexingContext(context, false);
      }
      myFailureMessage = null;
    }
    catch (IOException e) {
      myFailureMessage = e.getMessage();
      MavenLog.info(e);
    }
    catch (UnsupportedExistingLuceneIndexException e) {
      myFailureMessage = e.getMessage();
      MavenLog.info(e);
    }

    save();
  }

  private IndexingContext createContext(NexusIndexer indexer) throws IOException, UnsupportedExistingLuceneIndexException {
    // Nexus cannot update index if the id does not equal to the stored one.
    String id = getContextDir().exists() ? null : myDir.getName();
    return indexer.addIndexingContext(id,
                                      id,
                                      getRepositoryFile(),
                                      getContextDir(),
                                      getRepositoryUrl(),
                                      null, // repo update url
                                      NexusIndexer.FULL_INDEX);
  }

  private File getContextDir() {
    return new File(myDir, CONTEXT_DIR);
  }

  private void updateContext(IndexingContext context,
                             MavenEmbedder embedder,
                             NexusIndexer indexer,
                             IndexUpdater updater,
                             ProgressIndicator progress) throws IOException, UnsupportedExistingLuceneIndexException {
    switch (myKind) {
      case LOCAL:
        updateLocalContext(context, indexer, progress);
        break;
      case REMOTE:
        updateRemoteContext(context, embedder, updater);
        break;
    }
  }

  private void updateLocalContext(IndexingContext context,
                                  NexusIndexer indexer,
                                  ProgressIndicator progress) throws IOException {
    progress.setIndeterminate(true);
    try {
      indexer.scan(context, new MyScanningListener(), false);
    }
    finally {
      progress.setIndeterminate(true);
    }
  }

  private void updateRemoteContext(IndexingContext context,
                                   MavenEmbedder embedder,
                                   IndexUpdater updater) throws IOException, UnsupportedExistingLuceneIndexException {
    Proxy proxy = embedder.getSettings().getActiveProxy();
    ProxyInfo proxyInfo = null;
    if (proxy != null) {
      proxyInfo = new ProxyInfo();
      proxyInfo.setHost(proxy.getHost());
      proxyInfo.setPort(proxy.getPort());
      proxyInfo.setNonProxyHosts(proxy.getNonProxyHosts());
      proxyInfo.setUserName(proxy.getUsername());
      proxyInfo.setPassword(proxy.getPassword());
    }
    updater.fetchAndUpdateIndex(context, new TransferListenerAdapter(), proxyInfo);
  }

  private void updateData(IndexingContext context, ProgressIndicator progress) throws IOException {
    progress.setText2(IndicesBundle.message("maven.indices.updating.saving"));

    String newDataDir;
    IndexData newData;

    newDataDir = findAvailableDataDir();
    newData = openData(newDataDir);

    try {
      doUpdateIndexData(context, newData, progress);
      newData.flush();
    }
    catch (Throwable e) {
      newData.close();
      FileUtil.delete(getDataDir(newDataDir));

      if (e instanceof IOException) throw (IOException)e;
      throw new RuntimeException(e);
    }

    synchronized (this) {
      IndexData oldData = myData;
      String oldDataDir = myDataDirName;

      myData = newData;
      myDataDirName = newDataDir;

      myUpdateTimestamp = System.currentTimeMillis();

      oldData.close();
      FileUtil.delete(getDataDir(oldDataDir));
    }
  }

  private void doUpdateIndexData(IndexingContext context,
                                 IndexData data,
                                 ProgressIndicator progress) throws IOException {
    Set<String> groups = new HashSet<String>();
    Set<String> groupsWithArtifacts = new HashSet<String>();
    Set<String> groupsWithArtifactsWithVersions = new HashSet<String>();

    Map<String, Set<String>> groupToArtifactMap = new HashMap<String, Set<String>>();
    Map<String, Set<String>> groupWithArtifactToVersionMap = new HashMap<String, Set<String>>();

    IndexReader r = context.getIndexReader();
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

      groups.add(groupId);
      groupsWithArtifacts.add(groupId + ":" + artifactId);
      groupsWithArtifactsWithVersions.add(groupId + ":" + artifactId + ":" + version);

      getOrCreate(groupToArtifactMap, groupId).add(artifactId);
      getOrCreate(groupWithArtifactToVersionMap, groupId + ":" + artifactId).add(version);
    }

    persist(groups, data.groups);
    persist(groupsWithArtifacts, data.groupsWithArtifacts);
    persist(groupsWithArtifactsWithVersions, data.groupsWithArtifactsWithVersions);

    persist(groupToArtifactMap, data.groupToArtifactMap);
    persist(groupWithArtifactToVersionMap, data.groupWithArtifactToVersionMap);
  }

  private <T> Set<T> getOrCreate(Map<String, Set<T>> map, String key) {
    Set<T> result = map.get(key);
    if (result == null) {
      result = new HashSet<T>();
      map.put(key, result);
    }
    return result;
  }

  private <T> void persist(Map<String, T> map, PersistentHashMap<String, T> persistentMap) throws IOException {
    for (Map.Entry<String, T> each : map.entrySet()) {
      persistentMap.put(each.getKey(), each.getValue());
    }
  }

  private void persist(Set<String> groups, PersistentStringEnumerator persistent) throws IOException {
    for (String each : groups) {
      persistent.enumerate(each);
    }
  }

  private IndexData openData(String dataDir) throws IOException {
    File dir = getDataDir(dataDir);
    dir.mkdirs();
    return new IndexData(dir);
  }

  @TestOnly
  protected File getDir() {
    return myDir;
  }

  @TestOnly
  protected synchronized File getCurrentDataDir() {
    return getDataDir(myDataDirName);
  }

  private File getDataDir(String dataDir) {
    return new File(myDir, dataDir);
  }

  private String findAvailableDataDir() {
    return MavenIndices.findAvailableDir(myDir, DATA_DIR_PREFIX, 100).getName();
  }

  public synchronized void addArtifact(MavenId id) throws MavenIndexException {
    if (id.groupId == null) return;

    try {
      myData.groups.enumerate(id.groupId);
      myData.hasGroupCache.put(id.groupId, true);

      if (id.artifactId != null) {
        String groupWithArtifact = id.groupId + ":" + id.artifactId;

        myData.groupsWithArtifacts.enumerate(groupWithArtifact);
        myData.hasArtifactCache.put(groupWithArtifact, true);
        addToCache(myData.groupToArtifactMap, id.groupId, id.artifactId);

        if (id.version != null) {
          String groupWithArtifactWithVersion = groupWithArtifact + ":" + id.version;

          myData.groupsWithArtifactsWithVersions.enumerate(groupWithArtifactWithVersion);
          myData.hasVersionCache.put(groupWithArtifactWithVersion, true);
          addToCache(myData.groupWithArtifactToVersionMap, groupWithArtifact, id.version);
        }
      }
      myData.flush();
    }
    catch (IOException e) {
      String message = myRepositoryPathOrUrl +
          "\nopen: " + myData.isOpen +
          "\ndataDir: " + myDataDirName +
          "\nlastUpdate: " + myUpdateTimestamp +
          "\nfailureMessage: " + myFailureMessage;

      throw new MavenIndexException(message, e);
    }
  }

  private void addToCache(PersistentHashMap<String, Set<String>> cache, String key, String value) throws IOException {
    Set<String> values = cache.get(key);
    if (values == null) values = new HashSet<String>();
    values.add(value);
    cache.put(key, values);
  }

  public synchronized Set<String> getGroupIds() throws MavenIndexException {
    try {
      final Set<String> result = new HashSet<String>();
      myData.groups.traverseAllRecords(new PersistentEnumerator.RecordsProcessor() {
        public void process(int record) throws IOException {
          result.add(myData.groups.valueOf(record));
        }
      });
      return result;
    }
    catch (IOException e) {
      throw new MavenIndexException(e);
    }
  }

  public synchronized Set<String> getArtifactIds(String groupId) throws MavenIndexException {
    try {
      Set<String> result = myData.groupToArtifactMap.get(groupId);
      return result == null ? Collections.<String>emptySet() : result;
    }
    catch (IOException e) {
      throw new MavenIndexException(e);
    }
  }

  public synchronized Set<String> getVersions(String groupId, String artifactId) throws MavenIndexException {
    try {
      Set<String> result = myData.groupWithArtifactToVersionMap.get(groupId + ":" + artifactId);
      return result == null ? Collections.<String>emptySet() : result;
    }
    catch (IOException e) {
      throw new MavenIndexException(e);
    }
  }

  public synchronized boolean hasGroupId(String groupId) throws MavenIndexException {
    return hasValue(myData.groups, myData.hasGroupCache, groupId);
  }

  public synchronized boolean hasArtifactId(String groupId, String artifactId) throws MavenIndexException {
    return hasValue(myData.groupsWithArtifacts,
                    myData.hasArtifactCache,
                    groupId + ":" + artifactId);
  }

  public synchronized boolean hasVersion(String groupId, String artifactId, String version) throws MavenIndexException {
    return hasValue(myData.groupsWithArtifactsWithVersions,
                    myData.hasVersionCache,
                    groupId + ":" + artifactId + ":" + version);
  }

  private boolean hasValue(final PersistentStringEnumerator set, Map<String, Boolean> cache, final String value) throws MavenIndexException {
    Boolean cached = cache.get(value);
    if (cached != null) return cached;

    class FoundException extends RuntimeException {
    }

    boolean result = false;

    try {
      set.traverseAllRecords(new PersistentEnumerator.RecordsProcessor() {
        public void process(int record) throws IOException {
          if (value.equals(set.valueOf(record))) {
            throw new FoundException();
          }
        }
      });
    }
    catch (IOException e) {
      throw new MavenIndexException(e);
    }
    catch (FoundException ignore) {
      result = true;
    }

    cache.put(value, result);
    return result;
  }

  private static class IndexData {
    final PersistentStringEnumerator groups;
    final PersistentStringEnumerator groupsWithArtifacts;
    final PersistentStringEnumerator groupsWithArtifactsWithVersions;

    final PersistentHashMap<String, Set<String>> groupToArtifactMap;
    final PersistentHashMap<String, Set<String>> groupWithArtifactToVersionMap;

    final Map<String, Boolean> hasGroupCache = new HashMap<String, Boolean>();
    final Map<String, Boolean> hasArtifactCache = new HashMap<String, Boolean>();
    final Map<String, Boolean> hasVersionCache = new HashMap<String, Boolean>();

    volatile boolean isOpen = true;

    public IndexData(File dir) throws IOException {
      groups = new PersistentStringEnumerator(new File(dir, GROUP_IDS_FILE));
      groupsWithArtifacts = new PersistentStringEnumerator(new File(dir, ARTIFACT_IDS_FILE));
      groupsWithArtifactsWithVersions = new PersistentStringEnumerator(new File(dir, VERSIONS_FILE));

      groupToArtifactMap = createPersistentMap(new File(dir, ARTIFACT_IDS_MAP_FILE));
      groupWithArtifactToVersionMap = createPersistentMap(new File(dir, VERSIONS_MAP_FILE));
    }

    private PersistentHashMap<String, Set<String>> createPersistentMap(File f) throws IOException {
      return new PersistentHashMap<String, Set<String>>(f, new EnumeratorStringDescriptor(), new SetDescriptor());
    }

    public void close() throws IOException {
      isOpen = false;

      safeClose(groups);
      safeClose(groupsWithArtifacts);
      safeClose(groupsWithArtifactsWithVersions);

      safeClose(groupToArtifactMap);
      safeClose(groupWithArtifactToVersionMap);
    }

    private void safeClose(PersistentEnumerator enumerator) throws IOException {
      if (enumerator != null) enumerator.close();
    }

    public void flush() throws IOException {
      groups.flush();
      groupsWithArtifacts.flush();
      groupsWithArtifactsWithVersions.flush();

      groupToArtifactMap.flush();
      groupWithArtifactToVersionMap.flush();
    }
  }

  private static class SetDescriptor implements DataExternalizer<Set<String>> {
    public void save(DataOutput s, Set<String> set) throws IOException {
      s.writeInt(set.size());
      for (String each : set) {
        s.writeUTF(each);
      }
    }

    public Set<String> read(DataInput s) throws IOException {
      int count = s.readInt();
      Set<String> result = new HashSet<String>(count);
      while (count-- > 0) {
        result.add(s.readUTF());
      }
      return result;
    }
  }

  private class MyScanningListener implements ArtifactScanningListener {
    private ProgressIndicator p;

    public MyScanningListener() {
      p = ProgressManager.getInstance().getProgressIndicator();
      if (p == null) p = new EmptyProgressIndicator();
    }

    public void scanningStarted(IndexingContext ctx) {
      p.checkCanceled();
    }

    public void scanningFinished(IndexingContext ctx, ScanningResult result) {
      p.checkCanceled();
    }

    public void artifactError(ArtifactContext ac, Exception e) {
    }

    public void artifactDiscovered(ArtifactContext ac) {
      p.checkCanceled();
      p.setText2(IndicesBundle.message("maven.indices.updating.indexing", ac.getArtifact()));
    }
  }
}
