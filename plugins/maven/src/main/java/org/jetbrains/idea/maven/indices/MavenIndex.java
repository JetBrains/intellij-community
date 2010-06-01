/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.*;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.apache.lucene.search.Query;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.facade.MavenFacadeIndexerException;
import org.jetbrains.idea.maven.facade.MavenIndexerWrapper;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.*;
import java.util.*;

public class MavenIndex {
  private static final String CURRENT_VERSION = "4";

  protected static final String INDEX_INFO_FILE = "index.properties";

  private static final String INDEX_VERSION_KEY = "version";
  private static final String KIND_KEY = "kind";
  private static final String ID_KEY = "id";
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

  private final MavenIndexerWrapper myIndexer;
  private final File myDir;

  private final String myRepositoryId;
  private final String myRepositoryPathOrUrl;
  private final Kind myKind;
  private volatile Long myUpdateTimestamp;

  private volatile String myDataDirName;
  private volatile IndexData myData;

  private volatile String myFailureMessage;

  private volatile boolean isBroken;
  private final IndexListener myListener;

  public MavenIndex(MavenIndexerWrapper indexer,
                    File dir,
                    String repositoryId,
                    String repositoryPathOrUrl,
                    Kind kind,
                    IndexListener listener) throws MavenIndexException {
    myIndexer = indexer;
    myDir = dir;
    myRepositoryId = repositoryId;
    myRepositoryPathOrUrl = normalizePathOrUrl(repositoryPathOrUrl);
    myKind = kind;
    myListener = listener;

    open();
  }

  public MavenIndex(MavenIndexerWrapper indexer,
                    File dir,
                    IndexListener listener) throws MavenIndexException {
    myIndexer = indexer;
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

    if (!CURRENT_VERSION.equals(props.getProperty(INDEX_VERSION_KEY))) {
      throw new MavenIndexException("Incompatible index version, needs to be updated: " + dir);
    }

    myKind = Kind.valueOf(props.getProperty(KIND_KEY));
    myRepositoryId = props.getProperty(ID_KEY);
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
    catch (MavenIndexException e) {
      MavenLog.LOG.warn(e);
    }
    myData = null;
  }

  private synchronized void save() {
    myDir.mkdirs();

    Properties props = new Properties();

    props.setProperty(KIND_KEY, myKind.toString());
    props.setProperty(ID_KEY, myRepositoryId);
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

  public String getRepositoryId() {
    return myRepositoryId;
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

  public boolean isFor(Kind kind, String repositoryId, String pathOrUrl) {
    if (myKind != kind || !myRepositoryId.equals(repositoryId)) return false;
    if (kind == Kind.LOCAL) return FileUtil.pathsEqual(myRepositoryPathOrUrl, normalizePathOrUrl(pathOrUrl));
    return myRepositoryPathOrUrl.equalsIgnoreCase(normalizePathOrUrl(pathOrUrl));
  }

  public synchronized long getUpdateTimestamp() {
    return myUpdateTimestamp == null ? -1 : myUpdateTimestamp;
  }

  public synchronized String getFailureMessage() {
    return myFailureMessage;
  }

  public void updateOrRepair(boolean fullUpdate, MavenGeneralSettings settings, MavenProgressIndicator progress)
    throws MavenProcessCanceledException {
    try {
      if (fullUpdate) {
        if (myKind == Kind.LOCAL) FileUtil.delete(getUpdateDir());
        int context = createContext(getUpdateDir(), "update");
        try {
          updateContext(context, settings, progress);
        }
        finally {
          myIndexer.releaseIndex(context);
        }
      }
      updateData(progress);

      isBroken = false;
      myFailureMessage = null;
    }
    catch (MavenProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      handleUpdateException(e);
    }

    save();
  }

  private void handleUpdateException(Exception e) {
    myFailureMessage = e.getMessage();
    MavenLog.LOG.info("Failed to update Maven indices for: [" + myRepositoryId + "] " + myRepositoryPathOrUrl, e);
  }

  private int createContext(File contextDir, String suffix) throws MavenFacadeIndexerException {
    String indexId = myDir.getName() + "-" + suffix;
    // Nexus cannot update index if the id does not equal to the stored one.
    String repoId = contextDir.exists() ? null : myDir.getName();
    return myIndexer.createIndex(indexId,
                                 repoId,
                                 getRepositoryFile(),
                                 getRepositoryUrl(),
                                 contextDir);
  }

  private File getUpdateDir() {
    return new File(myDir, UPDATE_DIR);
  }

  private void updateContext(int indexId, MavenGeneralSettings settings, MavenProgressIndicator progress)
    throws MavenFacadeIndexerException, MavenProcessCanceledException {
    myIndexer.updateIndex(indexId, settings, progress);
  }

  private void updateData(MavenProgressIndicator progress) throws MavenIndexException {
    String newDataDirName;
    IndexData newData;

    newDataDirName = findAvailableDataDirName();
    try {
      FileUtil.copyDir(getUpdateDir(), getDataContextDir(getDataDir(newDataDirName)));
    }
    catch (IOException e) {
      throw new MavenIndexException(e);
    }
    newData = openData(newDataDirName);

    try {
      doUpdateIndexData(newData, progress);
      newData.flush();
    }
    catch (Throwable e) {
      newData.close();
      FileUtil.delete(getDataDir(newDataDirName));

      if (e instanceof MavenFacadeIndexerException) throw new MavenIndexException(e);
      if (e instanceof IOException) throw new MavenIndexException(e);
      throw new RuntimeException(e);
    }

    synchronized (this) {
      IndexData oldData = myData;

      myData = newData;
      myDataDirName = newDataDirName;

      myUpdateTimestamp = System.currentTimeMillis();

      oldData.close();
      for (File each : getAllDataDirs()) {
        if (each.getName().equals(newDataDirName)) continue;
        FileUtil.delete(each);
      }
    }
  }

  private void doUpdateIndexData(IndexData data,
                                 MavenProgressIndicator progress) throws IOException, MavenFacadeIndexerException {
    Set<String> groups = new THashSet<String>();
    Set<String> groupsWithArtifacts = new THashSet<String>();
    Set<String> groupsWithArtifactsWithVersions = new THashSet<String>();

    Map<String, Set<String>> groupToArtifactMap = new THashMap<String, Set<String>>();
    Map<String, Set<String>> groupWithArtifactToVersionMap = new THashMap<String, Set<String>>();

    List<MavenId> artifacts = myIndexer.getAllArtifacts(data.indexId);
    int total = artifacts.size();
    for (int i = 0; i < total; i++) {
      progress.setFraction(i / total);
      MavenId each = artifacts.get(i);

      String groupId = each.getGroupId();
      String artifactId = each.getArtifactId();
      String version = each.getVersion();

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
      result = new THashSet<T>();
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

  private IndexData openData(String dataDir) throws MavenIndexException {
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

  private Iterable<File> getAllDataDirs() {
    File[] children = myDir.listFiles();
    if (children == null) return ContainerUtil.emptyIterable();
    return ContainerUtil.iterate(children, new Condition<File>() {
      public boolean value(File file) {
        return file.getName().startsWith(DATA_DIR_PREFIX);
      }
    });
  }

  public synchronized void addArtifact(final File artifactFile) {
    doIndexTask(new IndexTask<Object>() {
      public Object doTask() throws Exception {
        MavenId id = myIndexer.addArtifact(myData.indexId, artifactFile);

        String groupId = id.getGroupId();
        String artifactId = id.getArtifactId();
        String version = id.getVersion();

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
    if (values == null) values = new THashSet<String>();
    values.add(value);
    cache.put(key, values);
  }

  public synchronized Set<String> getGroupIds() {
    return doIndexTask(new IndexTask<Set<String>>() {
      public Set<String> doTask() throws Exception {
        final Set<String> result = new THashSet<String>();
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

    boolean result = doIndexTask(new IndexTask<Boolean>() {
      public Boolean doTask() throws Exception {
        return !set.traverseAllRecords(new PersistentEnumerator.RecordsProcessor() {
          public boolean process(int record) throws IOException {
            if (value.equals(set.valueOf(record))) return false;
            return true;
          }
        });
      }
    }, false).booleanValue();

    cache.put(value, result);
    return result;
  }

  public synchronized Set<MavenArtifactInfo> search(final Query query, final int maxResult) {
    return doIndexTask(new IndexTask<Set<MavenArtifactInfo>>() {
      public Set<MavenArtifactInfo> doTask() throws Exception {
        return myIndexer.search(myData.indexId, query, maxResult);
      }
    }, Collections.<MavenArtifactInfo>emptySet());
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

  private interface IndexTask<T> {
    T doTask() throws Exception;
  }

  private class IndexData {
    final PersistentStringEnumerator groups;
    final PersistentStringEnumerator groupsWithArtifacts;
    final PersistentStringEnumerator groupsWithArtifactsWithVersions;

    final PersistentHashMap<String, Set<String>> groupToArtifactMap;
    final PersistentHashMap<String, Set<String>> groupWithArtifactToVersionMap;

    final Map<String, Boolean> hasGroupCache = new THashMap<String, Boolean>();
    final Map<String, Boolean> hasArtifactCache = new THashMap<String, Boolean>();
    final Map<String, Boolean> hasVersionCache = new THashMap<String, Boolean>();

    final int indexId;

    public IndexData(File dir) throws MavenIndexException {
      try {
        groups = new PersistentStringEnumerator(new File(dir, GROUP_IDS_FILE));
        groupsWithArtifacts = new PersistentStringEnumerator(new File(dir, ARTIFACT_IDS_FILE));
        groupsWithArtifactsWithVersions = new PersistentStringEnumerator(new File(dir, VERSIONS_FILE));

        groupToArtifactMap = createPersistentMap(new File(dir, ARTIFACT_IDS_MAP_FILE));
        groupWithArtifactToVersionMap = createPersistentMap(new File(dir, VERSIONS_MAP_FILE));

        indexId = createContext(getDataContextDir(dir), dir.getName());
      }
      catch (IOException e) {
        close();
        throw new MavenIndexException(e);
      }
      catch (MavenFacadeIndexerException e) {
        close();
        throw new MavenIndexException(e);
      }
    }

    private PersistentHashMap<String, Set<String>> createPersistentMap(File f) throws IOException {
      return new PersistentHashMap<String, Set<String>>(f, new EnumeratorStringDescriptor(), new SetDescriptor());
    }

    public void close() throws MavenIndexException {
      MavenIndexException[] exceptions = new MavenIndexException[1];

      try {
        if (indexId != 0) myIndexer.releaseIndex(indexId);
      }
      catch (MavenFacadeIndexerException e) {
        MavenLog.LOG.info(e);
        if (exceptions[0] == null) exceptions[0] = new MavenIndexException(e);
      }

      safeClose(groups, exceptions);
      safeClose(groupsWithArtifacts, exceptions);
      safeClose(groupsWithArtifactsWithVersions, exceptions);

      safeClose(groupToArtifactMap, exceptions);
      safeClose(groupWithArtifactToVersionMap, exceptions);

      if (exceptions[0] != null) throw exceptions[0];
    }

    private void safeClose(PersistentEnumerator enumerator, MavenIndexException[] exceptions) {
      try {
        if (enumerator != null) enumerator.close();
      }
      catch (IOException e) {
        MavenLog.LOG.info(e);
        if (exceptions[0] == null) exceptions[0] = new MavenIndexException(e);
      }
    }

    public void flush() throws IOException {
      groups.force();
      groupsWithArtifacts.force();
      groupsWithArtifactsWithVersions.force();

      groupToArtifactMap.force();
      groupWithArtifactToVersionMap.force();
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
      Set<String> result = new THashSet<String>(count);
      while (count-- > 0) {
        result.add(s.readUTF());
      }
      return result;
    }
  }

  public interface IndexListener {
    void indexIsBroken(MavenIndex index);
  }
}
