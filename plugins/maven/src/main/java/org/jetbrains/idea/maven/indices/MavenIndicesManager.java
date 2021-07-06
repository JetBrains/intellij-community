// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.JdomKt;
import com.intellij.util.io.PathKt;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenIndexerWrapper;
import org.jetbrains.idea.maven.server.MavenServerDownloadListener;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MavenIndicesManager implements Disposable {
  private static final String ELEMENT_ARCHETYPES = "archetypes";
  private static final String ELEMENT_ARCHETYPE = "archetype";
  private static final String ELEMENT_GROUP_ID = "groupId";
  private static final String ELEMENT_ARTIFACT_ID = "artifactId";
  private static final String ELEMENT_VERSION = "version";
  private static final String ELEMENT_REPOSITORY = "repository";
  private static final String ELEMENT_DESCRIPTION = "description";

  private static final String LOCAL_REPOSITORY_ID = "local";
  private final @NotNull Project myProject;


  private final AtomicBoolean myInitStarted = new AtomicBoolean(false);

  private class IndexKeeper implements Disposable {
    private final @NotNull MavenIndexerWrapper myIndexer;
    private final @NotNull MavenIndices myIndices;
    private final @NotNull List<MavenArchetype> myUserArchetypes;
    private final @NotNull MavenServerDownloadListener myDownloadListener;

    private IndexKeeper(@NotNull MavenIndexerWrapper indexer,
                        @NotNull MavenIndices indices,
                        @NotNull List<MavenArchetype> archetypes, MavenServerDownloadListener downloadListener) {
      myIndexer = indexer;
      myIndices = indices;
      myUserArchetypes = archetypes;
      myDownloadListener = downloadListener;
      MavenServerManager.getInstance().addDownloadListener(downloadListener);
    }

    @Override
    public void dispose() {
      try {
        myIndices.close();
      }
      catch (Exception e) {
        MavenLog.LOG.error("", e);
      }

      MavenServerManager mavenServerManager = MavenServerManager.getInstanceIfCreated();
      if (mavenServerManager != null) {
        mavenServerManager.removeDownloadListener(myDownloadListener);
      }
      clear();
    }
  }

  private final CompletableFuture<IndexKeeper> myKeeper = new CompletableFuture<>();

  public enum IndexUpdatingState {
    IDLE, WAITING, UPDATING
  }

  private volatile Path myTestIndicesDir;


  private final Object myUpdatingIndicesLock = new Object();
  private final List<MavenSearchIndex> myWaitingIndices = new ArrayList<>();
  private volatile MavenSearchIndex myUpdatingIndex;
  private final IndexFixer myIndexFixer = new IndexFixer();
  private final BackgroundTaskQueue myUpdatingQueue = new BackgroundTaskQueue(null, IndicesBundle.message("maven.indices.updating"));


  /**
   * @deprecated use {@link MavenIndicesManager#getInstance(Project)}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @Deprecated
  public static MavenIndicesManager getInstance() {
    // should not be used as it lead to plugin classloader leak on the plugin unload
    return ProjectManager.getInstance().getDefaultProject().getService(MavenIndicesManager.class);
  }

  public static MavenIndicesManager getInstance(@NotNull Project project) {
    return project.getService(MavenIndicesManager.class);
  }

  public MavenIndicesManager(@NotNull Project project) {
    myProject = project;
  }

  @TestOnly
  public void setTestIndexDir(Path indicesDir) {
    myTestIndicesDir = indicesDir;
  }

  public void clear() {
    myUpdatingQueue.clear();
  }

  @NotNull
  private MavenIndices getIndicesObject() {
    return ReadAction.nonBlocking(() -> {
      return ensureInitialized().myIndices;
    }).executeSynchronously();
  }

  @NotNull
  private IndexKeeper ensureInitialized() {
    if (myInitStarted.compareAndSet(false, true)) {
      startInitialization();
    }

    ApplicationManager.getApplication().assertIsNonDispatchThread();
    do {
      ProgressManager.checkCanceled();
      try {
        IndexKeeper indexKeeper = myKeeper.get(10, TimeUnit.MILLISECONDS);
        if (indexKeeper != null) return indexKeeper;
      } catch (TimeoutException ignore){
      }
      catch (Exception e){
        throw new RuntimeException(e);
      }
    }
    while (true);
  }

  private void startInitialization() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        MavenIndexerWrapper indexer = MavenServerManager.getInstance().createIndexer(myProject);
        MavenServerDownloadListener downloadListener = new MavenServerDownloadListener() {
          @Override
          public void artifactDownloaded(File file, String relativePath) {
            addArtifact(file, relativePath);
          }
        };
        MavenIndices indices = new MavenIndices(indexer, getIndicesDir().toFile(), new MavenSearchIndex.IndexListener() {
          @Override
          public void indexIsBroken(@NotNull MavenSearchIndex index) {
            if (index instanceof MavenIndex) {
              scheduleUpdate(null, Collections.singletonList((MavenIndex)index), false);
            }
          }
        });
        ArrayList<MavenArchetype> archetypes = loadUserArchetypes(getUserArchetypesFile());
        if (archetypes == null) {
          archetypes = new ArrayList<>();
        }
        IndexKeeper keeper = new IndexKeeper(indexer, indices, archetypes, downloadListener);
        Disposer.register(this, keeper);
        myKeeper.complete(keeper);
      }
      catch (Exception e) {
        MavenLog.LOG.error(e);
        myKeeper.completeExceptionally(e);
      }
    });
  }

  @NotNull
  private Path getIndicesDir() {
    return myTestIndicesDir == null
           ? MavenUtil.getPluginSystemDir("Indices")
           : myTestIndicesDir;
  }

  @Override
  public void dispose() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      PathKt.delete(getIndicesDir());
    }
  }

  public List<MavenIndex> getIndices() {
    return getIndicesObject().getIndices();
  }

  public MavenIndex ensureRemoteIndexExist(@NotNull Pair<String, String> remoteIndexIdAndUrl) {
    try {
      MavenIndices indicesObjectCache = ReadAction.nonBlocking(() -> {
        if (myProject.isDisposed()) {
          return null;
        }
        else {
          return getIndicesObject();
        }
      }).executeSynchronously();


      if (indicesObjectCache == null) return null;
      return indicesObjectCache.add(remoteIndexIdAndUrl.first, remoteIndexIdAndUrl.second, MavenSearchIndex.Kind.REMOTE);
    }
    catch (MavenIndexException e) {
      if (myProject.isDisposed()) {
        MavenLog.LOG.warn(e.getMessage());
      }
      else {
        MavenLog.LOG.warn(e);
      }
      return null;
    }
  }

  @Nullable
  public MavenIndex createIndexForLocalRepo(Project project, @Nullable File localRepository) {
    if (localRepository == null) {
      return null;
    }
    MavenIndices indicesObjectCache = getIndicesObject();

    try {
      MavenIndex localIndex = indicesObjectCache.add(LOCAL_REPOSITORY_ID, localRepository.getPath(), MavenIndex.Kind.LOCAL);
      if (localIndex.getUpdateTimestamp() == -1) {
        scheduleUpdate(project, Collections.singletonList(localIndex));
      }
      return localIndex;
    }
    catch (MavenIndexException e) {
      MavenLog.LOG.warn("Cannot create index:" + e.getMessage());
      return null;
    }
  }

  public List<MavenIndex> ensureIndicesExist(Collection<Pair<String, String>> remoteRepositoriesIdsAndUrls) {
    // MavenIndices.add method returns an existing index if it has already been added, thus we have to use set here.
    LinkedHashSet<MavenIndex> result = new LinkedHashSet<>();

    for (Pair<String, String> eachIdAndUrl : remoteRepositoriesIdsAndUrls) {
      MavenIndex index = ensureRemoteIndexExist(eachIdAndUrl);
      if (index != null) {
        result.add(index);
      }
    }
    return new ArrayList<>(result);
  }

  private void addArtifact(File artifactFile, String relativePath) {
    String repositoryPath = getRepositoryUrl(artifactFile, relativePath);

    MavenIndices indices = getIndicesObject();
    MavenIndex index = indices.find(repositoryPath, MavenSearchIndex.Kind.LOCAL);
    if (index != null) {
      index.addArtifact(artifactFile);
    }
  }

  public void fixArtifactIndex(File artifactFile, File localRepository) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      MavenIndex index = getIndicesObject().find(localRepository.getPath(), MavenSearchIndex.Kind.LOCAL);
      if (index != null) {
        myIndexFixer.fixIndex(artifactFile, index);
      }
    });
  }

  private static String getRepositoryUrl(File artifactFile, String name) {
    List<String> parts = getArtifactParts(name);

    File result = artifactFile;
    for (int i = 0; i < parts.size(); i++) {
      result = result.getParentFile();
    }
    return result.getPath();
  }

  private static List<String> getArtifactParts(String name) {
    return StringUtil.split(name, "/");
  }

  public Promise<Void> scheduleUpdate(@Nullable Project project, List<MavenIndex> indices) {
    return scheduleUpdate(project, indices, true);
  }

  private Promise<Void> scheduleUpdate(@Nullable Project project, List<MavenIndex> indices, final boolean fullUpdate) {
    final List<MavenSearchIndex> toSchedule = new ArrayList<>();

    synchronized (myUpdatingIndicesLock) {
      for (MavenSearchIndex each : indices) {
        if (myWaitingIndices.contains(each)) continue;
        toSchedule.add(each);
      }

      myWaitingIndices.addAll(toSchedule);
    }
    if (toSchedule.isEmpty()) {
      return Promises.resolvedPromise();
    }

    final AsyncPromise<Void> promise = new AsyncPromise<>();
    myUpdatingQueue.run(new Task.Backgroundable(project, IndicesBundle.message("maven.indices.updating"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          indicator.setIndeterminate(false);
          doUpdateIndices(project, toSchedule, fullUpdate, new MavenProgressIndicator(project, indicator, null));
        }
        catch (MavenProcessCanceledException ignore) {
        }
        finally {
          promise.setResult(null);
        }
      }
    });
    return promise;
  }

  private void doUpdateIndices(final Project projectOrNull,
                               List<MavenSearchIndex> indices,
                               boolean fullUpdate,
                               MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    MavenLog.LOG.assertTrue(!fullUpdate || projectOrNull != null);

    List<MavenSearchIndex> remainingWaiting = new ArrayList<>(indices);

    try {
      for (MavenSearchIndex each : indices) {
        if (indicator.isCanceled()) return;

        indicator.setText(IndicesBundle.message("maven.indices.updating.index",
                                                each.getRepositoryId(),
                                                each.getRepositoryPathOrUrl()));

        synchronized (myUpdatingIndicesLock) {
          remainingWaiting.remove(each);
          myWaitingIndices.remove(each);
          myUpdatingIndex = each;
        }

        try {
          MavenIndices.updateOrRepair(each, fullUpdate, fullUpdate ? getMavenSettings(projectOrNull, indicator) : null, indicator);
          if (projectOrNull != null) {
            MavenRehighlighter.rehighlight(projectOrNull);
          }
        }
        finally {
          synchronized (myUpdatingIndicesLock) {
            myUpdatingIndex = null;
          }
        }
      }
    }
    finally {
      synchronized (myUpdatingIndicesLock) {
        myWaitingIndices.removeAll(remainingWaiting);
      }
    }
  }

  private static MavenGeneralSettings getMavenSettings(@NotNull final Project project, @NotNull MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    MavenGeneralSettings settings;

    settings = ReadAction
      .compute(() -> project.isDisposed() ? null : MavenProjectsManager.getInstance(project).getGeneralSettings().clone());

    if (settings == null) {
      // project was closed
      indicator.cancel();
      indicator.checkCanceled();
    }

    return settings;
  }

  public IndexUpdatingState getUpdatingState(MavenSearchIndex index) {
    synchronized (myUpdatingIndicesLock) {
      if (myUpdatingIndex == index) return IndexUpdatingState.UPDATING;
      if (myWaitingIndices.contains(index)) return IndexUpdatingState.WAITING;
      return IndexUpdatingState.IDLE;
    }
  }

  public Set<MavenArchetype> getArchetypes() {
    IndexKeeper indexKeeper = ensureInitialized();
    Set<MavenArchetype> result = new HashSet<>(indexKeeper.myIndexer.getArchetypes());
    result.addAll(indexKeeper.myUserArchetypes);
    for (MavenIndex index : indexKeeper.myIndices.getIndices()) {
      result.addAll(index.getArchetypes());
    }

    for (MavenArchetypesProvider each : MavenArchetypesProvider.EP_NAME.getExtensionList()) {
      result.addAll(each.getArchetypes());
    }
    return result;
  }

  public void addArchetype(MavenArchetype archetype) {
    IndexKeeper indexKeeper = ensureInitialized();
    List<MavenArchetype> archetypes = indexKeeper.myUserArchetypes;
    int idx = archetypes.indexOf(archetype);
    if (idx >= 0) {
      archetypes.set(idx, archetype);
    }
    else {
      archetypes.add(archetype);
    }

    saveUserArchetypes(archetypes);
  }

  private static ArrayList<MavenArchetype> loadUserArchetypes(Path file) {
    try {
      if (!PathKt.exists(file)) {
        return null;
      }

      // Store artifact to set to remove duplicate created by old IDEA (https://youtrack.jetbrains.com/issue/IDEA-72105)
      Collection<MavenArchetype> result = new LinkedHashSet<>();

      List<Element> children = JDOMUtil.load(file).getChildren(ELEMENT_ARCHETYPE);
      for (int i = children.size() - 1; i >= 0; i--) {
        Element each = children.get(i);

        String groupId = each.getAttributeValue(ELEMENT_GROUP_ID);
        String artifactId = each.getAttributeValue(ELEMENT_ARTIFACT_ID);
        String version = each.getAttributeValue(ELEMENT_VERSION);
        String repository = each.getAttributeValue(ELEMENT_REPOSITORY);
        String description = each.getAttributeValue(ELEMENT_DESCRIPTION);

        if (StringUtil.isEmptyOrSpaces(groupId)
            || StringUtil.isEmptyOrSpaces(artifactId)
            || StringUtil.isEmptyOrSpaces(version)) {
          continue;
        }

        result.add(new MavenArchetype(groupId, artifactId, version, repository, description));
      }

      ArrayList<MavenArchetype> listResult = new ArrayList<>(result);
      Collections.reverse(listResult);

      return listResult;
    }
    catch (IOException | JDOMException e) {
      MavenLog.LOG.warn(e);
      return null;
    }
  }

  private void saveUserArchetypes(List<MavenArchetype> userArchetypes) {
    Element root = new Element(ELEMENT_ARCHETYPES);
    for (MavenArchetype each : userArchetypes) {
      Element childElement = new Element(ELEMENT_ARCHETYPE);
      childElement.setAttribute(ELEMENT_GROUP_ID, each.groupId);
      childElement.setAttribute(ELEMENT_ARTIFACT_ID, each.artifactId);
      childElement.setAttribute(ELEMENT_VERSION, each.version);
      if (each.repository != null) {
        childElement.setAttribute(ELEMENT_REPOSITORY, each.repository);
      }
      if (each.description != null) {
        childElement.setAttribute(ELEMENT_DESCRIPTION, each.description);
      }
      root.addContent(childElement);
    }
    try {
      JdomKt.write(root, getUserArchetypesFile());
    }
    catch (IOException e) {
      MavenLog.LOG.warn(e);
    }
  }

  @NotNull
  private Path getUserArchetypesFile() {
    return getIndicesDir().resolve("UserArchetypes.xml");
  }


  private final class IndexFixer {
    private final Set<String> indexedCache = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final ConcurrentLinkedQueue<Pair<File, MavenIndex>> queueToAdd = new ConcurrentLinkedQueue<>();
    private final MergingUpdateQueue myMergingUpdateQueue;

    private IndexFixer() {
      myMergingUpdateQueue =
        new MergingUpdateQueue(this.getClass().getName(), 1000, true, MergingUpdateQueue.ANY_COMPONENT, MavenIndicesManager.this, null,
                               false).usePassThroughInUnitTestMode();
    }

    public void fixIndex(File file, MavenIndex index) {
      if (indexedCache.contains(file.getName())) {
        return;
      }
      queueToAdd.add(new Pair.NonNull<>(file, index));

      myMergingUpdateQueue.queue(Update.create(this, new AddToIndexRunnable()));
    }

    private class AddToIndexRunnable implements Runnable {

      @Override
      public void run() {
        Pair<File, MavenIndex> elementToAdd;
        while ((elementToAdd = queueToAdd.poll()) != null) {
          elementToAdd.second.addArtifact(elementToAdd.first);
          indexedCache.add(elementToAdd.first.getName());
        }
      }
    }
  }
}
