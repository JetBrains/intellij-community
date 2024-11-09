// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices;

import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentHashMap;
import com.intellij.util.io.VersionUpdatedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.server.*;
import org.jetbrains.idea.maven.statistics.MavenIndexUsageCollector;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.openapi.util.text.StringUtil.split;
import static com.intellij.util.containers.ContainerUtil.notNullize;

public final class MavenIndexImpl implements MavenIndex, MavenSearchIndex {

  private static final String DATA_DIR_PREFIX = "data";

  private static final String ARTIFACT_IDS_MAP_FILE = "artifactIds-map.dat";
  private static final String VERSIONS_MAP_FILE = "versions-map.dat";
  private static final String ARCHETYPES_MAP_FILE = "archetypes-map.dat";

  private final MavenIndexerWrapper myNexusIndexer;
  private final Path myDir;
  /**
   * @deprecated not used
   */
  @Deprecated(forRemoval = true)
  private final Set<String> myRegisteredRepositoryIds;

  private final String myRepositoryPathOrUrl;
  private final RepositoryKind myKind;
  private final AtomicBoolean myDataClosed = new AtomicBoolean(false);
  private final Lock indexUpdateLock = new ReentrantLock();
  private volatile Long myUpdateTimestamp;
  private volatile IndexData myData;
  private volatile String myFailureMessage;
  private volatile boolean isBroken;
  private volatile boolean isClose;
  private String myDataDirName;

  public MavenIndexImpl(MavenIndexerWrapper indexer,
                        MavenIndexUtils.IndexPropertyHolder propertyHolder) throws MavenIndexException {
    myNexusIndexer = indexer;
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
          throw new MavenIndexException("Cannot open index " + myDir, e2);
        }
        markAsBroken();
      }
    }
    finally {
      boolean isCentral = isForCentral();
      MavenIndexUsageCollector.INDEX_OPENED.log(
        myKind == RepositoryKind.LOCAL,
        isCentral,
        myKind == RepositoryKind.REMOTE && !isCentral);
      save();
    }
  }

  private void doOpen() throws Exception {
    MavenLog.LOG.debug("open index " + this);
    ProgressManager.getInstance().computeInNonCancelableSection(() -> {
      Path dataDir;
      synchronized (this) {
        if (myDataDirName == null) {
          dataDir = createNewDataDir();
          myDataDirName = dataDir.toString();
        }
        else {
          dataDir = myDir.resolve(myDataDirName);
          Files.createDirectories(dataDir);
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

  private void cleanupBrokenData() throws IOException {
    close(true);

    //noinspection TestOnlyProblems
    final Path currentDataDir = getCurrentDataDir();
    final Path currentDataContextDir = getCurrentDataContextDir();
    final Path[] files = Files.list(currentDataDir).toArray(Path[]::new);
    for (Path file : files) {
      if (!file.equals(currentDataContextDir)) {
        FileUtil.delete(file);
      }
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
  }

  @Override
  public String getRepositoryId() {
    return join(myRegisteredRepositoryIds, ",");
  }

  @Override
  public Path getRepositoryFile() {
    return myKind == RepositoryKind.LOCAL ? Path.of(myRepositoryPathOrUrl) : null;
  }

  @Override
  public String getRepositoryUrl() {
    return myKind == RepositoryKind.REMOTE ? myRepositoryPathOrUrl : null;
  }

  @Override
  public String getRepositoryPathOrUrl() {
    return myRepositoryPathOrUrl;
  }

  @Override
  public @NotNull MavenRepositoryInfo getRepository() {
    return new MavenRepositoryInfo(getRepositoryId(), getRepositoryId(), myRepositoryPathOrUrl, myKind);
  }

  public long getUpdateTimestamp() {
    return myUpdateTimestamp == null ? -1 : myUpdateTimestamp;
  }

  @Override
  public String getFailureMessage() {
    return myFailureMessage;
  }

  @Override
  public void updateOrRepair(boolean fullUpdate, MavenProgressIndicator progress, boolean explicit)
    throws MavenProcessCanceledException {
    StructuredIdeActivity activity = MavenIndexUsageCollector.INDEX_UPDATE.started(null);
    boolean isSuccess = false;
    try {
      indexUpdateLock.lock();

      MavenLog.LOG.debug("start update index " + this);
      final Path newDataDir = createNewDataDir();
      final Path newDataContextDir = getDataContextDir(newDataDir);
      final Path currentDataContextDir = getCurrentDataContextDir();

      boolean reuseExistingContext = fullUpdate ?
                                     myKind != RepositoryKind.LOCAL && hasValidContext(currentDataContextDir) :
                                     hasValidContext(currentDataContextDir);

      fullUpdate = fullUpdate || !reuseExistingContext && myKind == RepositoryKind.LOCAL;

      if (reuseExistingContext) {
        try {
          Files.copy(currentDataContextDir, newDataContextDir);
        }
        catch (IOException e) {
          throw new MavenIndexException(e);
        }
      }

      if (fullUpdate) {
        MavenIndexId mavenIndexId = getMavenIndexId(newDataContextDir, "update");
        try {
          updateNexusContext(mavenIndexId, progress, explicit);
        }
        finally {
          myNexusIndexer.releaseIndex(mavenIndexId);
        }
      }

      updateIndexData(progress, newDataDir, fullUpdate);

      isBroken = false;
      myFailureMessage = null;

      MavenLog.LOG.debug("finish update index " + this);
      isSuccess = true;
    }
    catch (MavenProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      handleUpdateException(e);
    }
    finally {
      boolean isCentral = isForCentral();
      boolean finalIsSuccess = isSuccess;
      activity.finished(() ->
                          Arrays.asList(
                            MavenIndexUsageCollector.IS_LOCAL.with(myKind == RepositoryKind.LOCAL),
                            MavenIndexUsageCollector.IS_CENTRAL.with(myKind == RepositoryKind.REMOTE && isCentral),
                            MavenIndexUsageCollector.IS_PRIVATE_REMOTE.with(myKind == RepositoryKind.REMOTE && !isCentral),
                            MavenIndexUsageCollector.IS_SUCCESS.with(finalIsSuccess),
                            MavenIndexUsageCollector.MANUAL.with(explicit)
                          )
      );
      indexUpdateLock.unlock();
    }

    save();
  }

  private boolean isForCentral() {
    return myRepositoryPathOrUrl != null && myRepositoryPathOrUrl.contains("repo.maven.apache.org");
  }

  private boolean hasValidContext(@NotNull Path contextDir) {
    return Files.isDirectory(contextDir) && myNexusIndexer.indexExists(contextDir);
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

  private MavenIndexId getMavenIndexId(Path contextDir, String suffix) throws MavenServerIndexerException {
    String indexId = myDir.toString() + "-" + suffix;
    Path repositoryFile = getRepositoryFile();
    return new MavenIndexId(
      indexId, getRepositoryId(), repositoryFile == null ? null : repositoryFile.toAbsolutePath().toString(),
      getRepositoryUrl(), contextDir.toAbsolutePath().toString()
    );
  }

  private void updateNexusContext(@NotNull MavenIndexId indexId,
                                  @NotNull MavenProgressIndicator progress,
                                  boolean multithreaded)
    throws MavenServerIndexerException, MavenProcessCanceledException {
    myNexusIndexer.updateIndex(indexId, progress, multithreaded);
  }

  private void updateIndexData(MavenProgressIndicator progress, Path newDataDir, boolean fullUpdate) throws MavenIndexException,
                                                                                                            IOException {
    IndexData newData = new IndexData(newDataDir);
    try {
      doUpdateIndexData(newData, progress);
      newData.flush();
    }
    catch (Throwable e) {
      newData.close(true);
      Files.delete(newDataDir);

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
      myDataDirName = newDataDir.toString();

      if (fullUpdate) {
        myUpdateTimestamp = System.currentTimeMillis();
      }

      for (Path each : Files.list(myDir).toList()) {
        if (each.toString().startsWith(DATA_DIR_PREFIX) && !each.toString().equals(myDataDirName)) {
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
      myNexusIndexer.processArtifacts(data.mavenIndexId, mavenIndicesProcessor, progress);

      persist(groupToArtifactMap, data.groupToArtifactMap);
      persist(groupWithArtifactToVersionMap, data.groupWithArtifactToVersionMap);
      persist(archetypeIdToDescriptionMap, data.archetypeIdToDescriptionMap);
    }
    finally {
      progress.popState();
    }
  }

  private static void closeAndClean(PersistentHashMap<String, Set<String>> map) {
    try {
      map.closeAndClean();
    }
    catch (IOException e) {
      MavenLog.LOG.error(e);
    }
  }

  public void closeAndClean() {
    if (myDataClosed.compareAndSet(false, true)) {
      closeAndClean(myData.groupToArtifactMap);
      closeAndClean(myData.groupWithArtifactToVersionMap);
      closeAndClean(myData.archetypeIdToDescriptionMap);
      close(false);
    }
  }

  public Path getDir() {
    return myDir;
  }

  @TestOnly
  private synchronized Path getCurrentDataDir() {
    return myDir.resolve(myDataDirName);
  }

  private Path getCurrentDataContextDir() {
    //noinspection TestOnlyProblems
    return getCurrentDataDir().resolve("context");
  }

  @NotNull
  private Path createNewDataDir() {
    return MavenIndices.createNewDir(myDir, DATA_DIR_PREFIX, 100);
  }

  /**
   * Trying to add artifacts to index.
   *
   * @return list of artifact responses; indexed id is not null if artifact added; indexed id is null if retry is needed
   */
  @Override
  @NotNull
  public List<AddArtifactResponse> tryAddArtifacts(@NotNull Collection<? extends Path> artifactFiles) {
    var failedResponses = ContainerUtil.map(artifactFiles, file -> new AddArtifactResponse(file.toFile(), null));
    return doIndexAndRecoveryTask(() -> {
      boolean locked = indexUpdateLock.tryLock();
      if (!locked) return failedResponses;
      try {
        IndexData indexData = myData;
        if (indexData != null) {
          var addArtifactResponses = indexData.addArtifacts(artifactFiles);
          for (var addArtifactResponse : addArtifactResponses) {
            var id = addArtifactResponse.indexedMavenId();
            if (id != null) {
              String groupWithArtifact = id.groupId + ":" + id.artifactId;
              addToCache(indexData.groupToArtifactMap, id.groupId, id.artifactId);
              addToCache(indexData.groupWithArtifactToVersionMap, groupWithArtifact, id.version);
              if ("maven-archetype".equals(id.packaging)) {
                addToCache(indexData.archetypeIdToDescriptionMap, groupWithArtifact,
                           id.version + ":" + StringUtil.notNullize(id.description));
              }
            }
          }
          indexData.flush();
          return addArtifactResponses;
        }
        return failedResponses;
      }
      finally {
        indexUpdateLock.unlock();
      }
    }, failedResponses);
  }

  @Override
  public Collection<String> getGroupIds() {
    return doIndexTask(() -> getGroupIdsRaw(), Collections.emptySet());
  }

  @Override
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

  @Override
  public Set<String> getVersions(final String groupId, final String artifactId) {
    String ga = groupId + ":" + artifactId;
    return doIndexTask(() -> notNullize(myData.groupWithArtifactToVersionMap.get(ga)), Collections.emptySet());
  }

  @Override
  public boolean hasGroupId(String groupId) {
    if (isBroken) return false;

    IndexData indexData = myData;
    if (indexData == null) return false;
    return doIndexTask(() -> indexData.groupToArtifactMap.containsMapping(groupId), false);
  }

  @Override
  public boolean hasArtifactId(String groupId, String artifactId) {
    if (isBroken) return false;

    IndexData indexData = myData;
    if (indexData == null) return false;
    String key = groupId + ":" + artifactId;
    return doIndexTask(() -> indexData.groupWithArtifactToVersionMap.containsMapping(key), false);
  }

  @Override
  public boolean hasVersion(String groupId, String artifactId, final String version) {
    if (isBroken) return false;
    IndexData indexData = myData;
    if (indexData == null) return false;

    final String groupWithArtifactWithVersion = groupId + ":" + artifactId + ':' + version;
    String groupWithArtifact = groupWithArtifactWithVersion.substring(0, groupWithArtifactWithVersion.length() - version.length() - 1);

    return doIndexTask(() -> notNullize(indexData.groupWithArtifactToVersionMap.get(groupWithArtifact)).contains(version), false);
  }

  @Override
  public Set<MavenArtifactInfo> search(final String pattern, final int maxResult) {
    IndexData indexData = myData;
    if (indexData == null) return Collections.emptySet();
    return doIndexAndRecoveryTask(() -> indexData.search(pattern, maxResult), Collections.emptySet());
  }

  @Override
  public Set<MavenArchetype> getArchetypes() {
    return doIndexAndRecoveryTask(() -> {
      Set<MavenArchetype> archetypes = new HashSet<>();
      IndexData indexData = myData;
      if (indexData == null) return Collections.emptySet();
      indexData.archetypeIdToDescriptionMap.consumeKeysWithExistingMapping(ga -> {
        List<String> gaParts = split(ga, ":");

        String groupId = gaParts.get(0);
        String artifactId = gaParts.get(1);

        try {
          for (String vd : indexData.archetypeIdToDescriptionMap.get(ga)) {
            int index = vd.indexOf(':');
            if (index == -1) continue;

            String version = vd.substring(0, index);
            String description = vd.substring(index + 1);

            archetypes.add(new MavenArchetype(groupId, artifactId, version, myRepositoryPathOrUrl, description));
          }
        }
        catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
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

        try {
          cleanupBrokenData();
        }
        catch (IOException e) {
          MavenLog.LOG.warn(e);
        }
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
      MavenIndexUsageCollector.INDEX_BROKEN.log();
      ApplicationManager.getApplication().getMessageBus().syncPublisher(INDEX_IS_BROKEN).indexIsBroken(this);
    }
    isBroken = true;
  }

  @NotNull
  private Collection<String> getGroupIdsRaw() throws IOException {
    CommonProcessors.CollectProcessor<String> processor = new CommonProcessors.CollectProcessor<>();
    myData.groupToArtifactMap.processKeysWithExistingMapping(processor);
    return processor.getResults();
  }

  @Override
  public String toString() {
    return "MavenIndex{" +
           "myKind=" + myKind + '\'' +
           ", myRepositoryPathOrUrl='" + myRepositoryPathOrUrl +
           ", ids=" + myRegisteredRepositoryIds +
           '}';
  }

  private static <T> Set<T> getOrCreate(Map<String, Set<T>> map, String key) {
    return map.computeIfAbsent(key, k -> new TreeSet<>());
  }

  private static <T> void persist(Map<String, T> map, PersistentHashMap<String, T> persistentMap) throws IOException {
    for (Map.Entry<String, T> each : map.entrySet()) {
      persistentMap.put(each.getKey(), each.getValue());
    }
  }

  private static Path getDataContextDir(Path dataDir) {
    return dataDir.resolve("context");
  }

  private static void addToCache(PersistentHashMap<String, Set<String>> cache, String key, String value) throws IOException {
    if (key == null || value == null || cache == null) return;
    synchronized (cache) {
      Set<String> values = cache.get(key);
      if (values == null) values = new TreeSet<>();
      if (values.add(value)) {
        cache.put(key, values);
      }
    }
  }

  @FunctionalInterface
  private interface IndexTask<T> {
    T doTask() throws Exception;
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
      }
      catch (EOFException ignore) {
      }
      return result;
    }
  }

  private class IndexData {
    final PersistentHashMap<String, Set<String>> groupToArtifactMap;
    final PersistentHashMap<String, Set<String>> groupWithArtifactToVersionMap;
    final PersistentHashMap<String, Set<String>> archetypeIdToDescriptionMap;

    final MavenIndexId mavenIndexId;

    IndexData(Path dir) throws MavenIndexException {
      try {
        groupToArtifactMap = createPersistentMap(dir.resolve(ARTIFACT_IDS_MAP_FILE));
        groupWithArtifactToVersionMap = createPersistentMap(dir.resolve(VERSIONS_MAP_FILE));
        archetypeIdToDescriptionMap = createPersistentMap(dir.resolve(ARCHETYPES_MAP_FILE));

        mavenIndexId = getMavenIndexId(getDataContextDir(dir), dir.toString());
      }
      catch (IOException | MavenServerIndexerException e) {
        close(true);
        throw new MavenIndexException(e);
      }
    }

    private static PersistentHashMap<String, Set<String>> createPersistentMap(final Path f) throws IOException {
      return new PersistentHashMap<>(f, EnumeratorStringDescriptor.INSTANCE, new SetDescriptor());
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

    private static void safeClose(@Nullable Closeable enumerator, MavenIndexException[] exceptions) {
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

    @NotNull
    List<AddArtifactResponse> addArtifacts(Collection<? extends Path> artifactFiles) {
      return myNexusIndexer.addArtifacts(mavenIndexId, artifactFiles);
    }

    Set<MavenArtifactInfo> search(String pattern, int maxResult) throws MavenServerIndexerException {
      return myNexusIndexer.search(mavenIndexId, pattern, maxResult);
    }
  }
}
