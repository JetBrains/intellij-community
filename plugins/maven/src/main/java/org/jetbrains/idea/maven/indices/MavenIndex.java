package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.*;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
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
  private volatile Long myUpdateTimestamp;

  private volatile String myDataDirName;
  private volatile IndexData myData;

  private volatile String myFailureMessage;

  private volatile boolean isBroken;
  private final IndexListener myListener;

  public MavenIndex(File dir, String repositoryPathOrUrl, Kind kind, IndexListener listener) throws MavenIndexException {
    myDir = dir;
    myRepositoryPathOrUrl = normalizePathOrUrl(repositoryPathOrUrl);
    myKind = kind;
    myListener = listener;

    open();
  }

  public MavenIndex(File dir, IndexListener listener) throws MavenIndexException {
    myDir = dir;
    myListener = listener;

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
    myRepositoryPathOrUrl = normalizePathOrUrl(props.getProperty(PATH_OR_URL_KEY));

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

  private String normalizePathOrUrl(String pathOrUrl) {
    pathOrUrl = pathOrUrl.trim();
    pathOrUrl = FileUtil.toSystemIndependentName(pathOrUrl);
    while (pathOrUrl.endsWith("/")) {
      pathOrUrl = pathOrUrl.substring(0, pathOrUrl.length() - 1);
    }
    return pathOrUrl;
  }

  private void open() throws MavenIndexException {
    try {
      try {
        doOpen();
      }
      catch (Exception e1) {
        MavenLog.LOG.info(e1);
        try {
          doOpen();
        }
        catch (Exception e2) {
          throw new MavenIndexException("Cannot open index " + myDir.getPath(), e2);
        }
        isBroken = true;
      }
    }
    finally {
      save();
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
      cleanupBrokenData();
      throw e;
    }
  }

  private void cleanupBrokenData() {
    close();
    FileUtil.delete(getCurrentDataDir());
    myDataDirName = null;
  }

  public synchronized void close() {
    try {
      if (myData != null) myData.close();
    }
    catch (IOException e) {
      MavenLog.LOG.warn(e);
    }
    myData = null;
  }

  private synchronized void save() {
    myDir.mkdirs();

    Properties props = new Properties();

    props.setProperty(KIND_KEY, myKind.toString());
    props.setProperty(PATH_OR_URL_KEY, myRepositoryPathOrUrl);
    if (myUpdateTimestamp != null) props.setProperty(TIMESTAMP_KEY, String.valueOf(myUpdateTimestamp));
    if (myDataDirName != null) props.setProperty(DATA_DIR_NAME_KEY, myDataDirName);
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
      MavenLog.LOG.info(e);
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

  public boolean isForLocal(File repository) {
    return myKind == Kind.LOCAL && getRepositoryFile().equals(repository);
  }

  public boolean isForRemote(String url) {
    return myKind == Kind.REMOTE && getRepositoryUrl().equalsIgnoreCase(normalizePathOrUrl(url));
  }

  public synchronized long getUpdateTimestamp() {
    return myUpdateTimestamp == null ? -1 : myUpdateTimestamp;
  }

  public synchronized String getFailureMessage() {
    return myFailureMessage;
  }

  public void updateOrRepair(NexusIndexer indexer,
                             IndexUpdater updater,
                             ProxyInfo proxyInfo,
                             boolean fullUpdate,
                             ProgressIndicator progress) {
    try {
      if (fullUpdate) {
        if (myKind == Kind.LOCAL) {
          FileUtil.delete(getContextDir());
        }
      }
      IndexingContext context = createContext(indexer);
      try {
        if (fullUpdate) updateContext(context, indexer, updater, proxyInfo, progress);
        updateData(context, progress);
      }
      finally {
        indexer.removeIndexingContext(context, false);
      }
      isBroken = false;
      myFailureMessage = null;
    }
    catch (IOException e) {
      handleUpdateException(e);
    }
    catch (UnsupportedExistingLuceneIndexException e) {
      handleUpdateException(e);
    }

    save();
  }

  private void handleUpdateException(Exception e) {
    myFailureMessage = e.getMessage();
    MavenLog.LOG.info("Failed to update Maven indices for: " + myRepositoryPathOrUrl, e);
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
                             NexusIndexer indexer,
                             IndexUpdater updater,
                             ProxyInfo proxyInfo,
                             ProgressIndicator progress) throws IOException, UnsupportedExistingLuceneIndexException {
    switch (myKind) {
      case LOCAL:
        updateLocalContext(context, indexer, progress);
        break;
      case REMOTE:
        updateRemoteContext(context, updater, proxyInfo);
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
      progress.setIndeterminate(false);
    }
  }

  private void updateRemoteContext(IndexingContext context,
                                   IndexUpdater updater,
                                   ProxyInfo proxyInfo) throws IOException, UnsupportedExistingLuceneIndexException {
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

  public synchronized void addArtifact(final MavenId id) {
    doIndexTask(new IndexTask<Object>() {
      public Object doTask() throws Exception {
        if (id.groupId == null) return null;

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

        return null;
      }
    }, null);
  }

  private void addToCache(PersistentHashMap<String, Set<String>> cache, String key, String value) throws IOException {
    Set<String> values = cache.get(key);
    if (values == null) values = new HashSet<String>();
    values.add(value);
    cache.put(key, values);
  }

  public synchronized Set<String> getGroupIds() {
    return doIndexTask(new IndexTask<Set<String>>() {
      public Set<String> doTask() throws Exception {
        final Set<String> result = new HashSet<String>();
        myData.groups.traverseAllRecords(new PersistentEnumerator.RecordsProcessor() {
          public boolean process(int record) throws IOException {
            result.add(myData.groups.valueOf(record));
            return true;
          }
        });
        return result;
      }
    }, Collections.<String>emptySet());
  }

  public synchronized Set<String> getArtifactIds(final String groupId) {
    return doIndexTask(new IndexTask<Set<String>>() {
      public Set<String> doTask() throws Exception {
        Set<String> result = myData.groupToArtifactMap.get(groupId);
        return result == null ? Collections.<String>emptySet() : result;
      }
    }, Collections.<String>emptySet());
  }

  public synchronized Set<String> getVersions(final String groupId, final String artifactId) {
    return doIndexTask(new IndexTask<Set<String>>() {
      public Set<String> doTask() throws Exception {
        Set<String> result = myData.groupWithArtifactToVersionMap.get(groupId + ":" + artifactId);
        return result == null ? Collections.<String>emptySet() : result;
      }
    }, Collections.<String>emptySet());
  }

  public synchronized boolean hasGroupId(String groupId) {
    return hasValue(myData.groups, myData.hasGroupCache, groupId);
  }

  public synchronized boolean hasArtifactId(String groupId, String artifactId) {
    return hasValue(myData.groupsWithArtifacts,
                    myData.hasArtifactCache,
                    groupId + ":" + artifactId);
  }

  public synchronized boolean hasVersion(String groupId, String artifactId, String version) {
    return hasValue(myData.groupsWithArtifactsWithVersions,
                    myData.hasVersionCache,
                    groupId + ":" + artifactId + ":" + version);
  }

  private boolean hasValue(final PersistentStringEnumerator set, Map<String, Boolean> cache, final String value) {
    Boolean cached = cache.get(value);
    if (cached != null) return cached;

    class FoundException extends RuntimeException {
    }

    boolean result = doIndexTask(new IndexTask<Boolean>() {
      public Boolean doTask() throws Exception {
        try {
          set.traverseAllRecords(new PersistentEnumerator.RecordsProcessor() {
            public boolean process(int record) throws IOException {
              if (value.equals(set.valueOf(record))) {
                throw new FoundException();
              }
              return true;
            }
          });
        }
        catch (FoundException ignore) {
          return true;
        }
        return false;
      }
    }, false).booleanValue();

    cache.put(value, result);
    return result;
  }

  private <T> T doIndexTask(IndexTask<T> task, T defaultValue) {
    assert Thread.holdsLock(this);

    if (!isBroken) {
      try {
        return task.doTask();
      }
      catch (Exception e1) {
        MavenLog.LOG.warn(e1);

        cleanupBrokenData();
        try {
          open();
        }
        catch (MavenIndexException e2) {
          MavenLog.LOG.warn(e2);
        }
      }
    }

    isBroken = true;
    myListener.indexIsBroken(this);
    return defaultValue;
  }

  private static interface IndexTask<T> {
    T doTask() throws Exception;
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

    public IndexData(File dir) throws IOException {
      try {
        groups = new PersistentStringEnumerator(new File(dir, GROUP_IDS_FILE));
        groupsWithArtifacts = new PersistentStringEnumerator(new File(dir, ARTIFACT_IDS_FILE));
        groupsWithArtifactsWithVersions = new PersistentStringEnumerator(new File(dir, VERSIONS_FILE));

        groupToArtifactMap = createPersistentMap(new File(dir, ARTIFACT_IDS_MAP_FILE));
        groupWithArtifactToVersionMap = createPersistentMap(new File(dir, VERSIONS_MAP_FILE));
      }
      catch (IOException e) {
        close();
        throw e;
      }
    }

    private PersistentHashMap<String, Set<String>> createPersistentMap(File f) throws IOException {
      return new PersistentHashMap<String, Set<String>>(f, new EnumeratorStringDescriptor(), new SetDescriptor());
    }

    public void close() throws IOException {
      IOException[] exceptions = new IOException[1];

      safeClose(groups, exceptions);
      safeClose(groupsWithArtifacts, exceptions);
      safeClose(groupsWithArtifactsWithVersions, exceptions);

      safeClose(groupToArtifactMap, exceptions);
      safeClose(groupWithArtifactToVersionMap, exceptions);

      if (exceptions[0] != null) throw exceptions[0];
    }

    private void safeClose(PersistentEnumerator enumerator, IOException[] exceptions) throws IOException {
      try {
        if (enumerator != null) enumerator.close();
      }
      catch (IOException e) {
        if (exceptions[0] == null) exceptions[0] = e;
      }
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

  private static class MyScanningListener implements ArtifactScanningListener {
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

  public static interface IndexListener {
    void indexIsBroken(MavenIndex index);
  }
}
