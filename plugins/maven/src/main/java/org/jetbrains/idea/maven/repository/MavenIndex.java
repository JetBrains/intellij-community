package org.jetbrains.idea.maven.repository;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentEnumerator;
import com.intellij.util.io.PersistentHashMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.maven.embedder.MavenEmbedder;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.sonatype.nexus.index.ArtifactInfo;
import org.sonatype.nexus.index.NexusIndexer;
import org.sonatype.nexus.index.context.IndexingContext;
import org.sonatype.nexus.index.context.UnsupportedExistingLuceneIndexException;
import org.sonatype.nexus.index.updater.IndexUpdater;

import java.io.*;
import java.util.*;

public abstract class MavenIndex {
  protected static final String CONTEXT_DIR = "context";
  protected static final String DATA_DIR_PREFIX = "data";

  protected static final String GROUP_IDS_FILE = "groupIds.dat";
  protected static final String ARTIFACT_IDS_FILE = "artifactIds.dat";
  protected static final String VERSIONS_FILE = "versions.dat";

  protected static final String ARTIFACT_IDS_MAP_FILE = "artifactIds-map.dat";
  protected static final String VERSIONS_MAP_FILE = "versions-map.dat";

  public enum Kind {
    LOCAL(0), PROJECT(1), REMOTE(2);
    private int code;

    Kind(int code) {
      this.code = code;
    }

    public int getCode() {
      return code;
    }

    public static Kind forCode(int code) {
      for (Kind each : values()) {
        if (each.code == code) return each;
      }
      throw new RuntimeException("Unknown index kind: " + code);
    }
  }

  private File myIndicesDir;

  private String myId;
  private String myRepositoryPathOrUrl;
  private Kind myKind;

  private String myDataDir;

  private IndexData myData;

  public MavenIndex(String id, String repositoryPathOrUrl, Kind kind) {
    myId = id;
    myRepositoryPathOrUrl = repositoryPathOrUrl;
    myKind = kind;
  }

  public static MavenIndex read(DataInputStream s) throws IOException {
    Kind kind = Kind.forCode(s.readInt());
    String id = s.readUTF();
    String repo = readStringOrNull(s);
    String dataDir = readStringOrNull(s);

    MavenIndex result = create(kind, id, repo);
    result.myDataDir = dataDir;

    return result;
  }

  private static MavenIndex create(Kind kind, String id, String repo) {
    switch (kind) {
      case LOCAL:
        return new LocalMavenIndex(id, repo);
      case REMOTE:
        return new RemoteMavenIndex(id, repo);
      case PROJECT:
        return new ProjectMavenIndex(id, repo);
    }
    throw new RuntimeException("unexpected kind: " + kind);
  }

  private static String readStringOrNull(DataInputStream s) throws IOException {
    String result = null;
    boolean hasRepo = s.readBoolean();
    if (hasRepo) result = s.readUTF();
    return result;
  }

  public synchronized void write(DataOutputStream s) throws IOException {
    s.writeInt(myKind.getCode());
    s.writeUTF(myId);
    writeStringOrNull(s, myRepositoryPathOrUrl);
    writeStringOrNull(s, myDataDir);
  }

  private void writeStringOrNull(DataOutputStream s, String string) throws IOException {
    boolean has = string != null;
    s.writeBoolean(has);
    if (has) s.writeUTF(string);
  }

  public synchronized String getId() {
    return myId;
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

  public synchronized void open(File indicesDir) throws MavenIndexException {
    myIndicesDir = indicesDir;
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

  private synchronized void doOpen() throws Exception {
    try {
      if (myDataDir == null) {
        myDataDir = findAvailableDataDir();
      }
      myData = openData(myDataDir);
    }
    catch (Exception e) {
      remove();
      throw e;
    }
  }

  public synchronized void change(String id, String repositoryPathOrUrl) throws MavenIndexException {
    remove();
    myId = id;
    myRepositoryPathOrUrl = repositoryPathOrUrl;
    open(myIndicesDir);
  }

  public synchronized void remove() throws MavenIndexException {
    try {
      close();
    }
    finally {
      clear();
    }
  }

  private synchronized IndexingContext createContext(NexusIndexer indexer) throws IOException, UnsupportedExistingLuceneIndexException {
    return indexer.addIndexingContext(myId,
                                      myId,
                                      getRepositoryFile(),
                                      getContextDir(),
                                      getRepositoryUrl(),
                                      null, // repo update url
                                      NexusIndexer.FULL_INDEX);
  }


  private synchronized File getIndexDir() {
    return new File(myIndicesDir, myId);
  }

  private synchronized File getContextDir() {
    return new File(getIndexDir(), CONTEXT_DIR);
  }

  public synchronized void close() throws MavenIndexException {
    try {
      if (myData != null) myData.close();
    }
    catch (IOException e) {
      throw new MavenIndexException(e);
    }
  }

  public synchronized void clear() {
    FileUtil.delete(getIndexDir());
  }

  public void update(Project project,
                     MavenEmbedder embedder,
                     NexusIndexer indexer,
                     IndexUpdater updater,
                     ProgressIndicator progress) throws MavenIndexException,
                                                        ProcessCanceledException {
    try {
      if (shouldClearBeforeUpdate()) {
        FileUtil.delete(getContextDir());
      }
      IndexingContext context = createContext(indexer);
      try {
        updateContext(context, embedder, indexer, updater, progress);
        updateData(context, project, progress);
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

  protected boolean shouldClearBeforeUpdate() {
    return false;
  }

  protected abstract void updateContext(IndexingContext context, MavenEmbedder embedder,
                                        NexusIndexer indexer,
                                        IndexUpdater updater,
                                        ProgressIndicator progress)
      throws IOException, UnsupportedExistingLuceneIndexException, MavenIndexException;

  private void updateData(IndexingContext context, Project project, ProgressIndicator progress) throws IOException {
    progress.setText2("Updating caches...");

    Set<String> groupIds = new HashSet<String>();
    Set<String> artifactIds = new HashSet<String>();
    Set<String> versions = new HashSet<String>();
    Map<String, Set<String>> artifactIdsMap = new HashMap<String, Set<String>>();
    Map<String, Set<String>> versionsMap = new HashMap<String, Set<String>>();

    doUpdateIndexData(context, project, groupIds, artifactIds, versions, artifactIdsMap, versionsMap, progress);

    IndexData oldData;
    String oldDataDir;
    synchronized (this) {
      oldData = myData;
      oldDataDir = myDataDir;
    }

    String newDataDir = findAvailableDataDir();
    IndexData newData = openData(newDataDir);

    progress.setText2("Saving caches...");

    for (String each : groupIds) newData.groupIds.add(each);
    for (String each : artifactIds) newData.artifactIds.add(each);
    for (String each : versions) newData.versions.add(each);

    for (Map.Entry<String, Set<String>> each : artifactIdsMap.entrySet()) {
      newData.artifactIdsMap.put(each.getKey(), each.getValue());
    }

    for (Map.Entry<String, Set<String>> each : versionsMap.entrySet()) {
      newData.versionsMap.put(each.getKey(), each.getValue());
    }

    newData.flush();

    synchronized (this) {
      myData = newData;
      myDataDir = newDataDir;

      oldData.close();
      FileUtil.delete(getDataDir(oldDataDir));
    }
  }

  protected void doUpdateIndexData(IndexingContext context,
                                   Project project,
                                   Set<String> groupIds,
                                   Set<String> artifactIds,
                                   Set<String> versions,
                                   Map<String, Set<String>> artifactIdsMap,
                                   Map<String, Set<String>> versionsMap,
                                   ProgressIndicator progress) throws IOException {
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

      groupIds.add(groupId);
      artifactIds.add(groupId + ":" + artifactId);
      versions.add(groupId + ":" + artifactId + ":" + version);

      getOrCreate(artifactIdsMap, groupId).add(artifactId);
      getOrCreate(versionsMap, groupId + ":" + artifactId).add(version);
    }
  }

  private IndexData openData(String dataDir) throws IOException {
    File dir = getDataDir(dataDir);
    dir.mkdirs();
    return new IndexData(dir);
  }

  private File getDataDir(String dataDir) {
    return new File(getIndexDir(), dataDir);
  }

  public synchronized File getCurrentDataDir() {
    return getDataDir(myDataDir);
  }

  private String findAvailableDataDir() {
    for (int i = 0; i < 100; i++) {
      String name = DATA_DIR_PREFIX + i;
      File f = new File(getIndexDir(), name);
      if (!f.exists()) return name;
    }
    throw new RuntimeException("No available data dir found");
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

  public synchronized void removeArtifact(MavenId id) {
  }

  public synchronized <T> T process(DataProcessor<T> processor) throws Exception {
    return processor.process(myData);
  }

  public static interface DataProcessor<T> {
    T process(IndexData data) throws Exception;
  }

  public static class IndexData {
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
}
