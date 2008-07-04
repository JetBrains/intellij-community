package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentEnumerator;
import com.intellij.util.io.PersistentHashMap;
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

  private static final String CONTEXT_DIR = "context";

  private static final String DATA_DIR_PREFIX = "data";
  private static final String GROUP_IDS_FILE = "groupIds.dat";
  private static final String ARTIFACT_IDS_FILE = "artifactIds.dat";

  private static final String VERSIONS_FILE = "versions.dat";
  private static final String ARTIFACT_IDS_MAP_FILE = "artifactIds-map.dat";
  private static final String VERSIONS_MAP_FILE = "versions-map.dat";

  public enum Kind { LOCAL, REMOTE }

  private final File myDir;

  private String myRepositoryPathOrUrl;
  private final Kind myKind;
  private Long myUpdateTimestamp;

  private String myDataDirName;
  private IndexData myData;

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
      throw new MavenIndexException(e);
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

    open();
  }

  private synchronized void save() throws MavenIndexException {
    myDir.mkdirs();

    Properties props = new Properties();

    props.setProperty(KIND_KEY, myKind.toString());
    props.setProperty(PATH_OR_URL_KEY, myRepositoryPathOrUrl);
    if (myUpdateTimestamp != null) props.setProperty(TIMESTAMP_KEY, String.valueOf(myUpdateTimestamp));
    props.setProperty(DATA_DIR_NAME_KEY, myDataDirName);

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
      throw new MavenIndexException(e);
    }
  }

  public synchronized File getRepositoryFile() {
    return myKind == Kind.LOCAL ? new File(myRepositoryPathOrUrl) : null;
  }

  public synchronized String getRepositoryUrl() {
    return myKind == Kind.REMOTE ? myRepositoryPathOrUrl : null;
  }

  public synchronized String getRepositoryPathOrUrl() {
    return myRepositoryPathOrUrl;
  }

  public synchronized Kind getKind() {
    return myKind;
  }

  public long getUpdateTimestamp() {
    return myUpdateTimestamp == null ? -1 : myUpdateTimestamp;
  }

  private void open() throws MavenIndexException {
    try {
      doOpen();
    }
    catch (Exception e) {
      MavenLog.info(e);
      try {
        doOpen();
      }
      catch (Exception e1) {
        throw new MavenIndexException(e1);
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
      delete();
      throw e;
    }
  }

  public synchronized void change(String repositoryPathOrUrl) throws MavenIndexException {
    delete();
    myRepositoryPathOrUrl = repositoryPathOrUrl;
    open();

    save();
  }

  public synchronized void delete() throws MavenIndexException {
    try {
      close();
    }
    finally {
      FileUtil.delete(myDir);
    }
  }

  public synchronized void close() throws MavenIndexException {
    try {
      if (myData != null) myData.close();
    }
    catch (IOException e) {
      throw new MavenIndexException(e);
    }
  }

  public void update(MavenEmbedder embedder,
                     NexusIndexer indexer,
                     IndexUpdater updater,
                     ProgressIndicator progress) throws MavenIndexException,
                                                        ProcessCanceledException {
    try {
      if (myKind == Kind.LOCAL) {
        FileUtil.delete(getContextDir());
      }
      IndexingContext context = createContext(indexer);
      try {
        updateContext(context, embedder, indexer, updater, progress);
        updateData(context, progress);
        myUpdateTimestamp = System.currentTimeMillis();
        save();
      }
      finally {
        indexer.removeIndexingContext(context, false);
      }
    }
    catch (IOException e) {
      throw new MavenIndexException(e);
    }
    catch (UnsupportedExistingLuceneIndexException e) {
      throw new MavenIndexException(e);
    }
  }

  private synchronized IndexingContext createContext(NexusIndexer indexer) throws IOException, UnsupportedExistingLuceneIndexException {
    File targetDir = getContextDir();

    // Nexus cannot update index if the id does not equal to the stored one.
    String id = targetDir.exists() ? null : myDir.getName();

    return indexer.addIndexingContext(id,
                                      id,
                                      getRepositoryFile(),
                                      getContextDir(),
                                      getRepositoryUrl(),
                                      null, // repo update url
                                      NexusIndexer.FULL_INDEX);
  }

  private synchronized File getContextDir() {
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

  protected void updateLocalContext(IndexingContext context,
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
    progress.setText2("Updating caches...");

    String newDataDir = findAvailableDataDir();
    IndexData newData = openData(newDataDir);
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

      oldData.close();
      FileUtil.delete(getDataDir(oldDataDir));
    }
  }

  protected void doUpdateIndexData(IndexingContext context,
                                   IndexData data,
                                   ProgressIndicator progress) throws IOException {
    Map<String, Set<String>> artifactIdsMap = new HashMap<String, Set<String>>();
    Map<String, Set<String>> versionsMap = new HashMap<String, Set<String>>();

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

      data.groupIds.add(groupId);
      data.artifactIds.add(groupId + ":" + artifactId);
      data.versions.add(groupId + ":" + artifactId + ":" + version);

      getOrCreate(artifactIdsMap, groupId).add(artifactId);
      getOrCreate(versionsMap, groupId + ":" + artifactId).add(version);
    }

    persist(artifactIdsMap, data.artifactIdsMap);
    persist(versionsMap, data.versionsMap);
  }

  protected void persist(Map<String, Set<String>> map, PersistentHashMap<String, Set<String>> persistentMap) throws IOException {
    for (Map.Entry<String, Set<String>> each : map.entrySet()) {
      persistentMap.put(each.getKey(), each.getValue());
    }
  }

  private IndexData openData(String dataDir) throws IOException {
    File dir = getDataDir(dataDir);
    dir.mkdirs();
    return new IndexData(dir);
  }

  @TestOnly
  protected synchronized File getDir() {
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

  protected Set<String> getOrCreate(Map<String, Set<String>> map, String key) {
    Set<String> result = map.get(key);
    if (result == null) {
      result = new HashSet<String>();
      map.put(key, result);
    }
    return result;
  }

  public synchronized void addArtifact(MavenId id) throws MavenIndexException {
    if (id.groupId == null) return;

    try {
      myData.groupIds.add(id.groupId);

      if (id.artifactId != null) {
        myData.artifactIds.add(id.groupId + ":" + id.artifactId);
        addToCache(myData.artifactIdsMap, id.groupId, id.artifactId);

        if (id.version != null) {
          myData.versions.add(id.groupId + ":" + id.artifactId + ":" + id.version);
          addToCache(myData.versionsMap, id.groupId + ":" + id.artifactId, id.version);
        }
      }
      myData.flush();
    }
    catch (IOException e) {
      throw new MavenIndexException(e);
    }
  }

  private void addToCache(PersistentHashMap<String, Set<String>> cache, String key, String value) throws IOException {
    Set<String> values = cache.get(key);
    if (values == null) values = new HashSet<String>();
    values.add(value);
    cache.put(key, values);
  }

  public synchronized Set<String> getGroupIds() throws MavenIndexException {
    Set<String> result = myData.groupIds;
    return result == null ? Collections.<String>emptySet() : result;
  }

  public synchronized Set<String> getArtifactIds(String groupId) throws MavenIndexException {
    try {
      Set<String> result = myData.artifactIdsMap.get(groupId);
      return result == null ? Collections.<String>emptySet() : result;
    }
    catch (IOException e) {
      throw new MavenIndexException(e);
    }
  }

  public synchronized Set<String> getVersions(String groupId, String artifactId) throws MavenIndexException {
    try {
      Set<String> result = myData.versionsMap.get(groupId + ":" + artifactId);
      return result == null ? Collections.<String>emptySet() : result;
    }
    catch (IOException e) {
      throw new MavenIndexException(e);
    }
  }

  public synchronized boolean hasGroupId(String groupId) throws MavenIndexException {
    return myData.groupIds.contains(groupId);
  }

  public synchronized boolean hasArtifactId(String groupId, String artifactId) throws MavenIndexException {
    return myData.artifactIds.contains(groupId + ":" + artifactId);
  }

  public synchronized boolean hasVersion(String groupId, String artifactId, String version) throws MavenIndexException {
    return myData.versions.contains(groupId + ":" + artifactId + ":" + version);
  }

  private static class IndexData {
    File myDir;
    Set<String> groupIds;
    Set<String> artifactIds;
    Set<String> versions;
    PersistentHashMap<String, Set<String>> artifactIdsMap;
    PersistentHashMap<String, Set<String>> versionsMap;

    public IndexData(File dir) throws IOException {
      myDir = dir;
      groupIds = read(GROUP_IDS_FILE);
      artifactIds = read(ARTIFACT_IDS_FILE);
      versions = read(VERSIONS_FILE);

      artifactIdsMap = createPersistentMap(new File(dir, ARTIFACT_IDS_MAP_FILE));
      versionsMap = createPersistentMap(new File(dir, VERSIONS_MAP_FILE));
    }

    private Set<String> read(String fileName) throws IOException {
      File f = new File(myDir, fileName);
      if (!f.exists()) return new HashSet<String>();

      DataInputStream s = new DataInputStream(new FileInputStream(f));
      try {
        return new SetDescriptor().read(s);
      }
      finally {
        s.close();
      }
    }

    private PersistentHashMap<String, Set<String>> createPersistentMap(File f) throws IOException {
      return new PersistentHashMap<String, Set<String>>(f, new EnumeratorStringDescriptor(), new SetDescriptor());
    }

    public void close() throws IOException {
      safeClose(artifactIdsMap);
      safeClose(versionsMap);
    }

    private void safeClose(PersistentEnumerator enumerator) throws IOException {
      if (enumerator != null) enumerator.close();
    }

    public void flush() throws IOException {
      save(GROUP_IDS_FILE, groupIds);
      save(ARTIFACT_IDS_FILE, artifactIds);
      save(VERSIONS_FILE, versions);

      artifactIdsMap.flush();
      versionsMap.flush();
    }

    private void save(String fileName, Set<String> values) throws IOException {
      DataOutputStream s = new DataOutputStream(new FileOutputStream(new File(myDir, fileName)));
      try {
        new SetDescriptor().save(s, values);
      }
      finally {
        s.close();
      }
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
      Set<String> result = new HashSet<String>();
      int count = s.readInt();
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
