package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.*;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.*;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.project.TransferListenerAdapter;
import org.sonatype.nexus.index.*;
import org.sonatype.nexus.index.context.IndexContextInInconsistentStateException;
import org.sonatype.nexus.index.context.IndexingContext;
import org.sonatype.nexus.index.context.UnsupportedExistingLuceneIndexException;
import org.sonatype.nexus.index.scan.ScanningResult;
import org.sonatype.nexus.index.updater.IndexUpdater;

import java.io.*;
import java.util.*;

public class MavenIndex {
  private static final String CURRENT_VERSION = "2";

  protected static final String INDEX_INFO_FILE = "index.properties";

  private static final String INDEX_VERSION_KEY = "version";
  private static final String KIND_KEY = "kind";
  private static final String PATH_OR_URL_KEY = "pathOrUrl";
  private static final String TIMESTAMP_KEY = "lastUpdate";
  private static final String DATA_DIR_NAME_KEY = "dataDirName";
  private static final String FAILURE_MESSAGE_KEY = "failureMessage";

  private static final String UPDATE_DIR = "update";

  private static final String DATA_DIR_PREFIX = "data";
  private static final String GROUP_IDS_FILE = "groupIds.dat";
  private static final String ARTIFACT_IDS_FILE = "artifactIds.dat";

  private static final String VERSIONS_FILE = "versions.dat";
  private static final String ARTIFACT_IDS_MAP_FILE = "artifactIds-map.dat";
  private static final String VERSIONS_MAP_FILE = "versions-map.dat";

  public enum Kind {
    LOCAL, REMOTE
  }
  
  private final NexusIndexer myIndexer;
  private ArtifactContextProducer myArtifactContextProducer;
  private final File myDir;

  private final String myRepositoryPathOrUrl;
  private final Kind myKind;
  private volatile Long myUpdateTimestamp;

  private volatile String myDataDirName;
  private volatile IndexData myData;

  private volatile String myFailureMessage;

  private volatile boolean isBroken;
  private final IndexListener myListener;

  public MavenIndex(NexusIndexer indexer,
                    ArtifactContextProducer artifactContextProducer,
                    File dir,
                    String repositoryPathOrUrl,
                    Kind kind,
                    IndexListener listener) throws MavenIndexException {
    myIndexer = indexer;
    myArtifactContextProducer = artifactContextProducer;
    myDir = dir;
    myRepositoryPathOrUrl = normalizePathOrUrl(repositoryPathOrUrl);
    myKind = kind;
    myListener = listener;

    open();
  }

  public MavenIndex(NexusIndexer indexer,
                    ArtifactContextProducer artifactContextProducer,
                    File dir,
                    IndexListener listener) throws MavenIndexException {
    myIndexer = indexer;
    myArtifactContextProducer = artifactContextProducer;
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

    if (!getUpdateDir().exists()) {
      myUpdateTimestamp = null;
    }

    if (!CURRENT_VERSION.equals(props.getProperty(INDEX_VERSION_KEY))) {
      isBroken = true;
      cleanupBrokenData();
    }

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
        myDataDirName = findAvailableDataDirName();
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
    props.setProperty(INDEX_VERSION_KEY, CURRENT_VERSION);
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

  public void updateOrRepair(IndexUpdater updater,
                             ProxyInfo proxyInfo,
                             boolean fullUpdate,
                             ProgressIndicator progress) {
    try {
      if (fullUpdate) {
        if (myKind == Kind.LOCAL) FileUtil.delete(getUpdateDir());
        IndexingContext context = createContext(getUpdateDir());
        try {
          updateContext(context, updater, proxyInfo, progress);
        }
        finally {
          myIndexer.removeIndexingContext(context, false);
        }
      }
      updateData(progress);

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

  private IndexingContext createContext(File contextDir) throws IOException, UnsupportedExistingLuceneIndexException {
    // Nexus cannot update index if the id does not equal to the stored one.
    String id = contextDir.exists() ? null : myDir.getName();
    return myIndexer.addIndexingContext(id,
                                        id,
                                        getRepositoryFile(),
                                        contextDir,
                                        getRepositoryUrl(),
                                        null, // repo update url
                                        NexusIndexer.FULL_INDEX);
  }

  private File getUpdateDir() {
    return new File(myDir, UPDATE_DIR);
  }

  private void updateContext(IndexingContext context,
                             IndexUpdater updater,
                             ProxyInfo proxyInfo,
                             ProgressIndicator progress) throws IOException, UnsupportedExistingLuceneIndexException {
    if (Kind.LOCAL == myKind) {
      progress.setIndeterminate(true);
      try {
        myIndexer.scan(context, new MyScanningListener(), false);
      }
      finally {
        progress.setIndeterminate(false);
      }
    }
    else {
      updater.fetchAndUpdateIndex(context, new TransferListenerAdapter(), proxyInfo);
    }
  }

  private void updateData(ProgressIndicator progress) throws IOException, UnsupportedExistingLuceneIndexException {
    progress.setText2(IndicesBundle.message("maven.indices.updating.saving"));

    String newDataDirName;
    IndexData newData;

    newDataDirName = findAvailableDataDirName();
    FileUtil.copyDir(getUpdateDir(), getDataContextDir(getDataDir(newDataDirName)));
    newData = openData(newDataDirName);

    try {
      doUpdateIndexData(newData, progress);
      newData.flush();
    }
    catch (Throwable e) {
      newData.close();
      FileUtil.delete(getDataDir(newDataDirName));

      if (e instanceof IOException) throw (IOException)e;
      throw new RuntimeException(e);
    }

    synchronized (this) {
      IndexData oldData = myData;
      String oldDataDir = myDataDirName;

      myData = newData;
      myDataDirName = newDataDirName;

      myUpdateTimestamp = System.currentTimeMillis();

      oldData.close();
      FileUtil.delete(getDataDir(oldDataDir));
    }
  }

  private void doUpdateIndexData(IndexData data,
                                 ProgressIndicator progress) throws IOException, IndexContextInInconsistentStateException {
    Set<String> groups = new HashSet<String>();
    Set<String> groupsWithArtifacts = new HashSet<String>();
    Set<String> groupsWithArtifactsWithVersions = new HashSet<String>();

    Map<String, Set<String>> groupToArtifactMap = new HashMap<String, Set<String>>();
    Map<String, Set<String>> groupWithArtifactToVersionMap = new HashMap<String, Set<String>>();

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

      if (groupId == null || artifactId == null || version == null) continue;

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

  private IndexData openData(String dataDir) throws IOException, UnsupportedExistingLuceneIndexException {
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

  private File getDataDir(String dataDirName) {
    return new File(myDir, dataDirName);
  }

  private File getDataContextDir(File dataDir) {
    return new File(dataDir, "context");
  }

  private String findAvailableDataDirName() {
    return MavenIndices.findAvailableDir(myDir, DATA_DIR_PREFIX, 100).getName();
  }

  public synchronized void addArtifact(final File artifactFile) {
    doIndexTask(new IndexTask<Object>() {
      public Object doTask() throws Exception {
        ArtifactContext artifactContext = myArtifactContextProducer.getArtifactContext(myData.context, artifactFile);
        if (artifactContext == null) return null;

        myIndexer.addArtifactToIndex(artifactContext, myData.context);

        ArtifactInfo artifactInfo = artifactContext.getArtifactInfo();

        String groupId = artifactInfo.groupId;
        String artifactId = artifactInfo.artifactId;
        String version = artifactInfo.version;

        if (groupId == null  || artifactId == null || version == null) return null;

        myData.groups.enumerate(groupId);
        myData.hasGroupCache.put(groupId, true);

        String groupWithArtifact = groupId + ":" + artifactId;

        myData.groupsWithArtifacts.enumerate(groupWithArtifact);
        myData.hasArtifactCache.put(groupWithArtifact, true);
        addToCache(myData.groupToArtifactMap, groupId, artifactId);

        String groupWithArtifactWithVersion = groupWithArtifact + ":" + version;

        myData.groupsWithArtifactsWithVersions.enumerate(groupWithArtifactWithVersion);
        myData.hasVersionCache.put(groupWithArtifactWithVersion, true);
        addToCache(myData.groupWithArtifactToVersionMap, groupWithArtifact, version);
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

  public synchronized Set<ArtifactInfo> search(final Query query, final int maxResult) {
    return doIndexTask(new IndexTask<Set<ArtifactInfo>>() {
      public Set<ArtifactInfo> doTask() throws Exception {
        TopDocs docs = null;

        try {
          BooleanQuery.setMaxClauseCount(Integer.MAX_VALUE);
          docs = myData.context.getIndexSearcher().search(query, (Filter)null, maxResult);
        }
        catch (BooleanQuery.TooManyClauses ignore) {
          // this exception occurs when too wide wildcard is used on too big data.
        }

        if (docs.scoreDocs.length == 0) return Collections.emptySet();

        Set<ArtifactInfo> result = new HashSet<ArtifactInfo>();

        for (int i = 0; i < docs.scoreDocs.length; i++) {
          int docIndex = docs.scoreDocs[i].doc;
          Document doc = myData.context.getIndexReader().document(docIndex);
          ArtifactInfo artifactInfo = myData.context.constructArtifactInfo(doc);

          if (artifactInfo == null) continue;

          artifactInfo.repository = myRepositoryPathOrUrl;
          result.add(artifactInfo);
        }
        return result;
      }
    }, Collections.<ArtifactInfo>emptySet());
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

  private class IndexData {
    final PersistentStringEnumerator groups;
    final PersistentStringEnumerator groupsWithArtifacts;
    final PersistentStringEnumerator groupsWithArtifactsWithVersions;

    final PersistentHashMap<String, Set<String>> groupToArtifactMap;
    final PersistentHashMap<String, Set<String>> groupWithArtifactToVersionMap;

    final Map<String, Boolean> hasGroupCache = new HashMap<String, Boolean>();
    final Map<String, Boolean> hasArtifactCache = new HashMap<String, Boolean>();
    final Map<String, Boolean> hasVersionCache = new HashMap<String, Boolean>();

    final IndexingContext context;

    public IndexData(File dir) throws IOException, UnsupportedExistingLuceneIndexException {
      try {
        groups = new PersistentStringEnumerator(new File(dir, GROUP_IDS_FILE));
        groupsWithArtifacts = new PersistentStringEnumerator(new File(dir, ARTIFACT_IDS_FILE));
        groupsWithArtifactsWithVersions = new PersistentStringEnumerator(new File(dir, VERSIONS_FILE));

        groupToArtifactMap = createPersistentMap(new File(dir, ARTIFACT_IDS_MAP_FILE));
        groupWithArtifactToVersionMap = createPersistentMap(new File(dir, VERSIONS_MAP_FILE));

        context = createContext(getDataContextDir(dir));
      }
      catch (IOException e) {
        close();
        throw e;
      }
      catch (UnsupportedExistingLuceneIndexException e) {
        close();
        throw e;
      }
    }

    private PersistentHashMap<String, Set<String>> createPersistentMap(File f) throws IOException {
      return new PersistentHashMap<String, Set<String>>(f, new EnumeratorStringDescriptor(), new SetDescriptor());
    }

    public void close() throws IOException {
      IOException[] exceptions = new IOException[1];

      try {
        myIndexer.removeIndexingContext(context, false);
      }
      catch (IOException e) {
        MavenLog.LOG.info(e);
        if (exceptions[0] == null) exceptions[0] = e;
      }

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
        MavenLog.LOG.info(e);
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
