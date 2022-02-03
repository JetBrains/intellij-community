// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentHashMap;
import com.intellij.util.io.VersionUpdatedException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenIndexId;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.server.IndexedMavenId;
import org.jetbrains.idea.maven.server.MavenIndexerWrapper;
import org.jetbrains.idea.maven.server.MavenIndicesProcessor;
import org.jetbrains.idea.maven.server.MavenServerIndexerException;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.openapi.util.text.StringUtil.split;
import static com.intellij.util.containers.ContainerUtil.notNullize;

public final class MavenIndex implements MavenSearchIndex {
  private static final String DATA_DIR_PREFIX = "data";

  private static final String ARTIFACT_IDS_MAP_FILE = "artifactIds-map.dat";
  private static final String VERSIONS_MAP_FILE = "versions-map.dat";
  private static final String ARCHETYPES_MAP_FILE = "archetypes-map.dat";

  private final MavenIndexerWrapper myNexusIndexer;
  private final File myDir;
  /**
   *  @deprecated not used
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  private final Set<String> myRegisteredRepositoryIds;

  private final String myRepositoryPathOrUrl;
  private final Kind myKind;

  private volatile Long myUpdateTimestamp;
  private volatile IndexData myData;
  private volatile String myFailureMessage;
  private volatile boolean isBroken;
  private volatile boolean isClose;

  private String myDataDirName;
  private final IndexListener myListener;
  private final Lock indexUpdateLock = new ReentrantLock();

  public MavenIndex(MavenIndexerWrapper indexer,
                    MavenIndexUtils.IndexPropertyHolder propertyHolder,
                    IndexListener listener) throws MavenIndexException {
    myNexusIndexer = indexer;
    myListener = listener;

    myDir = propertyHolder.dir;
    myKind = propertyHolder.kind;
    myRegisteredRepositoryIds = propertyHolder.repositoryIds;
    myRepositoryPathOrUrl = propertyHolder.repositoryPathOrUrl;
    myUpdateTimestamp = propertyHolder.updateTimestamp;
    myDataDirName = propertyHolder.dataDirName;
    myFailureMessage = propertyHolder.failureMessage;

    open();
  }

  public String getDataDirName() {
    return myDataDirName;
  }

  private void open() throws MavenIndexException {
    try {
      try {
        doOpen();
      }
      catch (Exception e) {
        if (e instanceof ProcessCanceledException) {
          MavenLog.LOG.error("PCE should not be thrown", new Attachment("pce", e));
        }
        final boolean versionUpdated = e.getCause() instanceof VersionUpdatedException;
        if (!versionUpdated) MavenLog.LOG.warn(e);

        try {
          doOpen();
        }
        catch (Exception e2) {
          throw new MavenIndexException("Cannot open index " + myDir.getPath(), e2);
        }
        markAsBroken();
      }
    }
    finally {
      save();
    }
  }

  private void doOpen() throws Exception {
    MavenLog.LOG.debug("open index " + this);
    ProgressManager.getInstance().computeInNonCancelableSection(() -> {
      File dataDir;
      synchronized (this) {
        if (myDataDirName == null) {
          dataDir = createNewDataDir();
          myDataDirName = dataDir.getName();
        }
        else {
          dataDir = new File(myDir, myDataDirName);
          dataDir.mkdirs();
        }

        if (myData != null) {
          myData.close(true);
        }
        if (isClose) return null;
        myData = new IndexData(dataDir);
        return null;
      }
    });
  }

  private void cleanupBrokenData() {
    close(true);

    //noinspection TestOnlyProblems
    final File currentDataDir = getCurrentDataDir();
    final File currentDataContextDir = getCurrentDataContextDir();
    final File[] files = currentDataDir.listFiles();
    if (files != null) {
      for (File file : files) {
        if (!FileUtil.filesEqual(file, currentDataContextDir)) {
          FileUtil.delete(file);
        }
      }
    }
    else {
      FileUtil.delete(currentDataDir);
    }
  }

  public synchronized void finalClose(boolean releaseIndexContext) {
    isClose = true;
    close(releaseIndexContext);
  }

  @Override
  public void close(boolean releaseIndexContext) {
    IndexData data = myData;
    try {
      if (data != null) data.close(releaseIndexContext);
    }
    catch (MavenIndexException e) {
      MavenLog.LOG.warn(e);
    }
    myData = null;
  }

  private synchronized void save() {
    myDir.mkdirs();
    MavenIndexUtils.saveIndexProperty(this);
  }

  @Override
  public String getRepositoryId() {
    return join(myRegisteredRepositoryIds, ",");
  }

  @Override
  public File getRepositoryFile() {
    return myKind == Kind.LOCAL ? new File(myRepositoryPathOrUrl) : null;
  }

  @Override
  public String getRepositoryUrl() {
    return myKind == Kind.REMOTE ? myRepositoryPathOrUrl : null;
  }

  @Override
  public String getRepositoryPathOrUrl() {
    return myRepositoryPathOrUrl;
  }

  @Override
  public Kind getKind() {
    return myKind;
  }

  @Override
  public long getUpdateTimestamp() {
    return myUpdateTimestamp == null ? -1 : myUpdateTimestamp;
  }

  @Override
  public String getFailureMessage() {
    return myFailureMessage;
  }

  @Override
  public void updateOrRepair(boolean fullUpdate, @Nullable MavenGeneralSettings settings, MavenProgressIndicator progress)
    throws MavenProcessCanceledException {
    try {
      indexUpdateLock.lock();

      MavenLog.LOG.debug("start update index " + this);
      final File newDataDir = createNewDataDir();
      final File newDataContextDir = getDataContextDir(newDataDir);
      final File currentDataContextDir = getCurrentDataContextDir();

      boolean reuseExistingContext = fullUpdate ?
                                     myKind != Kind.LOCAL && hasValidContext(currentDataContextDir) :
                                     hasValidContext(currentDataContextDir);

      fullUpdate = fullUpdate || !reuseExistingContext && myKind == Kind.LOCAL;

      if (reuseExistingContext) {
        try {
          FileUtil.copyDir(currentDataContextDir, newDataContextDir);
        }
        catch (IOException e) {
          throw new MavenIndexException(e);
        }
      }

      if (fullUpdate) {
        MavenIndexId mavenIndexId = getMavenIndexId(newDataContextDir, "update");
        try {
          updateNexusContext(mavenIndexId, settings, progress);
        }
        finally {
          myNexusIndexer.releaseIndex(mavenIndexId);
        }
      }

      updateIndexData(progress, newDataDir, fullUpdate);

      isBroken = false;
      myFailureMessage = null;

      MavenLog.LOG.debug("finish update index " + this);
    }
    catch (MavenProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      handleUpdateException(e);
    } finally {
      indexUpdateLock.unlock();
    }

    save();
  }

  private boolean hasValidContext(@NotNull File contextDir) {
    return contextDir.isDirectory() && myNexusIndexer.indexExists(contextDir);
  }

  private void handleUpdateException(Exception e) {
    String failureMessage = e.getMessage();
    if (failureMessage != null &&
        failureMessage.contains("nexus-maven-repository-index.properties") &&
        failureMessage.contains("FileNotFoundException")) {
      failureMessage = "Repository is non-nexus repo, or is not indexed";
      MavenLog.LOG.debug("Failed to update Maven indices for: " + myRegisteredRepositoryIds + " " + myRepositoryPathOrUrl, e);
    }
    else {
      MavenLog.LOG.warn("Failed to update Maven indices for: " + myRegisteredRepositoryIds + " " + myRepositoryPathOrUrl, e);
    }
    myFailureMessage = failureMessage;
  }

  private MavenIndexId getMavenIndexId(File contextDir, String suffix) throws MavenServerIndexerException {
    String indexId = myDir.getName() + "-" + suffix;
    File repositoryFile = getRepositoryFile();
    return new MavenIndexId(
      indexId, getRepositoryId(), repositoryFile == null ? null : repositoryFile.getAbsolutePath(),
      getRepositoryUrl(), contextDir.getAbsolutePath()
    );
  }

  private void updateNexusContext(@NotNull MavenIndexId indexId,
                                  @Nullable MavenGeneralSettings settings,
                                  @NotNull MavenProgressIndicator progress)
    throws MavenServerIndexerException, MavenProcessCanceledException {
    myNexusIndexer.updateIndex(indexId, settings, progress);
  }

  private void updateIndexData(MavenProgressIndicator progress, File newDataDir, boolean fullUpdate) throws MavenIndexException {
    IndexData newData = new IndexData(newDataDir);
    try {
      doUpdateIndexData(newData, progress);
      newData.flush();
    }
    catch (Throwable e) {
      newData.close(true);
      FileUtil.delete(newDataDir);

      if (e instanceof MavenServerIndexerException) throw new MavenIndexException(e);
      if (e instanceof IOException) throw new MavenIndexException(e);
      throw new RuntimeException(e);
    }

    synchronized (this) {
      if (isClose) {
        newData.close(false);
        return;
      }
      if (myData != null) {
        myData.close(true);
      }
      myData = newData;
      myDataDirName = newDataDir.getName();

      if (fullUpdate) {
        myUpdateTimestamp = System.currentTimeMillis();
      }

      for (File each : FileUtil.notNullize(myDir.listFiles())) {
        if (each.getName().startsWith(DATA_DIR_PREFIX) && !each.getName().equals(myDataDirName)) {
          FileUtil.delete(each);
        }
      }
    }
  }

  private void doUpdateIndexData(IndexData data, MavenProgressIndicator progress)
    throws IOException, MavenServerIndexerException {

    final Map<String, Set<String>> groupToArtifactMap = new HashMap<>();
    final Map<String, Set<String>> groupWithArtifactToVersionMap = new HashMap<>();
    final Map<String, Set<String>> archetypeIdToDescriptionMap = new HashMap<>();

    progress.pushState();
    progress.setIndeterminate(true);

    try {
      final StringBuilder builder = new StringBuilder();
      MavenIndicesProcessor mavenIndicesProcessor = artifacts -> {
        for (IndexedMavenId id : artifacts) {
          if ("pom.lastUpdated".equals(id.packaging)) {
            continue;
          }
          builder.setLength(0);

          builder.append(id.groupId).append(":").append(id.artifactId);
          String ga = builder.toString();

          getOrCreate(groupToArtifactMap, id.groupId).add(id.artifactId);
          getOrCreate(groupWithArtifactToVersionMap, ga).add(id.version);

          if ("maven-archetype".equals(id.packaging)) {
            builder.setLength(0);
            builder.append(id.version).append(":").append(StringUtil.notNullize(id.description));
            getOrCreate(archetypeIdToDescriptionMap, ga).add(builder.toString());
          }
        }
      };
      myNexusIndexer.processArtifacts(data.mavenIndexId, mavenIndicesProcessor);

      persist(groupToArtifactMap, data.groupToArtifactMap);
      persist(groupWithArtifactToVersionMap, data.groupWithArtifactToVersionMap);
      persist(archetypeIdToDescriptionMap, data.archetypeIdToDescriptionMap);
    }
    finally {
      progress.popState();
    }
  }

  private static <T> Set<T> getOrCreate(Map<String, Set<T>> map, String key) {
    return map.computeIfAbsent(key, k -> new TreeSet<>());
  }

  private static <T> void persist(Map<String, T> map, PersistentHashMap<String, T> persistentMap) throws IOException {
    for (Map.Entry<String, T> each : map.entrySet()) {
      persistentMap.put(each.getKey(), each.getValue());
    }
  }

  public File getDir() {
    return myDir;
  }

  @TestOnly
  private synchronized File getCurrentDataDir() {
    return new File(myDir, myDataDirName);
  }

  private File getCurrentDataContextDir() {
    //noinspection TestOnlyProblems
    return new File(getCurrentDataDir(), "context");
  }

  private static File getDataContextDir(File dataDir) {
    return new File(dataDir, "context");
  }

  @NotNull
  private File createNewDataDir() {
    return MavenIndices.createNewDir(myDir, DATA_DIR_PREFIX, 100);
  }

  /**
   * Trying to add artifact to index.
   *
   * @return true if artifact added to index else need retry
   */
  public boolean tryAddArtifact(final File artifactFile) {
    return doIndexAndRecoveryTask(() -> {
      boolean locked = indexUpdateLock.tryLock();
      if (!locked) return false;
      try {
        IndexData indexData = myData;
        IndexedMavenId id = indexData.addArtifact(artifactFile);
        if (id == null) return true;

        String groupWithArtifact = id.groupId + ":" + id.artifactId;

        addToCache(indexData.groupToArtifactMap, id.groupId, id.artifactId);
        addToCache(indexData.groupWithArtifactToVersionMap, groupWithArtifact, id.version);
        if ("maven-archetype".equals(id.packaging)) {
          addToCache(indexData.archetypeIdToDescriptionMap, groupWithArtifact, id.version + ":" + StringUtil.notNullize(id.description));
        }
        indexData.flush();
        return true;
      } finally {
        indexUpdateLock.unlock();
      }
    }, true);
  }

  private static void addToCache(PersistentHashMap<String, Set<String>> cache, String key, String value) throws IOException {
    synchronized (cache) {
      Set<String> values = cache.get(key);
      if (values == null) values = new TreeSet<>();
      if (values.add(value)) {
        cache.put(key, values);
      }
    }
  }

  public Collection<String> getGroupIds() {
    return doIndexTask(() -> getGroupIdsRaw(), Collections.emptySet());
  }

  public Set<String> getArtifactIds(final String groupId) {
    return doIndexTask(() -> notNullize(myData.groupToArtifactMap.get(groupId)), Collections.emptySet());
  }

  @TestOnly
  public synchronized void printInfo() {
    doIndexTask(() -> {
      MavenLog.LOG.debug("BaseFile: " + myData.groupToArtifactMap);
      MavenLog.LOG.debug("All data objects: " + getGroupIdsRaw());
      return null;
    }, null);
  }

  public Set<String> getVersions(final String groupId, final String artifactId) {
    String ga = groupId + ":" + artifactId;
    return doIndexTask(() -> notNullize(myData.groupWithArtifactToVersionMap.get(ga)), Collections.emptySet());
  }

  public boolean hasGroupId(String groupId) {
    if (isBroken) return false;

    IndexData indexData = myData;
    return doIndexTask(() -> indexData.groupToArtifactMap.containsMapping(groupId), false);
  }

  public boolean hasArtifactId(String groupId, String artifactId) {
    if (isBroken) return false;

    IndexData indexData = myData;
    String key = groupId + ":" + artifactId;
    return doIndexTask(() -> indexData.groupWithArtifactToVersionMap.containsMapping(key), false);
  }

  public boolean hasVersion(String groupId, String artifactId, final String version) {
    if (isBroken) return false;

    final String groupWithArtifactWithVersion = groupId + ":" + artifactId + ':' + version;
    String groupWithArtifact = groupWithArtifactWithVersion.substring(0, groupWithArtifactWithVersion.length() - version.length() - 1);
    IndexData indexData = myData;
    return doIndexTask(() -> notNullize(indexData.groupWithArtifactToVersionMap.get(groupWithArtifact)).contains(version), false);
  }

  public Set<MavenArtifactInfo> search(final String pattern, final int maxResult) {
    return doIndexAndRecoveryTask(() -> myData.search(pattern, maxResult), Collections.emptySet());
  }

  public Set<MavenArchetype> getArchetypes() {
    return doIndexAndRecoveryTask(() -> {
      Set<MavenArchetype> archetypes = new HashSet<>();
      IndexData indexData = myData;
      for (String ga : indexData.archetypeIdToDescriptionMap.getAllKeysWithExistingMapping()) {
        List<String> gaParts = split(ga, ":");

        String groupId = gaParts.get(0);
        String artifactId = gaParts.get(1);

        for (String vd : indexData.archetypeIdToDescriptionMap.get(ga)) {
          int index = vd.indexOf(':');
          if (index == -1) continue;

          String version = vd.substring(0, index);
          String description = vd.substring(index + 1);

          archetypes.add(new MavenArchetype(groupId, artifactId, version, myRepositoryPathOrUrl, description));
        }
      }
      return archetypes;
    }, Collections.emptySet());
  }

  private <T> T doIndexTask(IndexTask<T> task, T defaultValue) {
    if (!isBroken) {
      try {
        return task.doTask();
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        MavenLog.LOG.warn(e);
        markAsBroken();
      }
    }
    return defaultValue;
  }

  private <T> T doIndexAndRecoveryTask(IndexTask<T> task, T defaultValue) {
    if (!isBroken) {
      try {
        return task.doTask();
      }
      catch (ProcessCanceledException e) {
        throw e;
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
    markAsBroken();
    return defaultValue;
  }

  private void markAsBroken() {
    if (isClose) return;
    if (!isBroken) {
      MavenLog.LOG.info("index is broken " + this);
      myListener.indexIsBroken(this);
    }
    isBroken = true;
  }

  @FunctionalInterface
  private interface IndexTask<T> {
    T doTask() throws Exception;
  }

  private class IndexData {
    final PersistentHashMap<String, Set<String>> groupToArtifactMap;
    final PersistentHashMap<String, Set<String>> groupWithArtifactToVersionMap;
    final PersistentHashMap<String, Set<String>> archetypeIdToDescriptionMap;

    final MavenIndexId mavenIndexId;

    IndexData(File dir) throws MavenIndexException {
      try {
        groupToArtifactMap = createPersistentMap(new File(dir, ARTIFACT_IDS_MAP_FILE));
        groupWithArtifactToVersionMap = createPersistentMap(new File(dir, VERSIONS_MAP_FILE));
        archetypeIdToDescriptionMap = createPersistentMap(new File(dir, ARCHETYPES_MAP_FILE));

        mavenIndexId = getMavenIndexId(getDataContextDir(dir), dir.getName());
      }
      catch (IOException | MavenServerIndexerException e) {
        close(true);
        throw new MavenIndexException(e);
      }
    }

    private PersistentHashMap<String, Set<String>> createPersistentMap(final File f) throws IOException {
      return new PersistentHashMap<>(f.toPath(), EnumeratorStringDescriptor.INSTANCE, new SetDescriptor());
    }

    void close(boolean releaseIndexContext) throws MavenIndexException {
      MavenIndexException[] exceptions = new MavenIndexException[1];

      try {
        if (releaseIndexContext) myNexusIndexer.releaseIndex(mavenIndexId);
      }
      catch (MavenServerIndexerException e) {
        MavenLog.LOG.warn(e);
        if (exceptions[0] == null) exceptions[0] = new MavenIndexException(e);
      }

      safeClose(groupToArtifactMap, exceptions);
      safeClose(groupWithArtifactToVersionMap, exceptions);
      safeClose(archetypeIdToDescriptionMap, exceptions);

      if (exceptions[0] != null) throw exceptions[0];
    }

    private void safeClose(@Nullable Closeable enumerator, MavenIndexException[] exceptions) {
      try {
        if (enumerator != null) enumerator.close();
      }
      catch (IOException e) {
        MavenLog.LOG.warn(e);
        if (exceptions[0] == null) exceptions[0] = new MavenIndexException(e);
      }
    }

    void flush() {
      groupToArtifactMap.force();
      groupWithArtifactToVersionMap.force();
      archetypeIdToDescriptionMap.force();
    }

    IndexedMavenId addArtifact(File artifactFile) throws MavenServerIndexerException {
      return myNexusIndexer.addArtifact(mavenIndexId, artifactFile);
    }

    Set<MavenArtifactInfo> search(String pattern, int maxResult) throws MavenServerIndexerException {
      return myNexusIndexer.search(mavenIndexId, pattern, maxResult);
    }
  }

  @NotNull
  private Collection<String> getGroupIdsRaw() throws IOException {
    CommonProcessors.CollectProcessor<String> processor = new CommonProcessors.CollectProcessor<>();
    myData.groupToArtifactMap.processKeysWithExistingMapping(processor);
    return processor.getResults();
  }

  /**
   * @deprecated use {@link MavenIndexUtils#normalizePathOrUrl(java.lang.String)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  @NotNull
  public static String normalizePathOrUrl(@NotNull String pathOrUrl) {
    return MavenIndexUtils.normalizePathOrUrl(pathOrUrl);
  }

  private static class SetDescriptor implements DataExternalizer<Set<String>> {
    @Override
    public void save(@NotNull DataOutput s, Set<String> set) throws IOException {
      s.writeInt(set.size());
      for (String each : set) {
        s.writeUTF(each);
      }
    }

    @Override
    public Set<String> read(@NotNull DataInput s) throws IOException {
      int count = s.readInt();
      Set<String> result = new TreeSet<>();
      try {
        while (count-- > 0) {
          result.add(s.readUTF());
        }
      } catch (EOFException ignore){}
      return result;
    }
  }

  @Override
  public String toString() {
    return "MavenIndex{" +
           "myKind=" + myKind + '\'' +
           ", myRepositoryPathOrUrl='" + myRepositoryPathOrUrl +
           ", ids=" + myRegisteredRepositoryIds +
           '}';
  }
}
