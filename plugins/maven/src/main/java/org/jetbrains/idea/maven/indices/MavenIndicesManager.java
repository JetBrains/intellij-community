/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.JdomKt;
import com.intellij.util.io.PathKt;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
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

public class MavenIndicesManager implements Disposable {
  private static final String ELEMENT_ARCHETYPES = "archetypes";
  private static final String ELEMENT_ARCHETYPE = "archetype";
  private static final String ELEMENT_GROUP_ID = "groupId";
  private static final String ELEMENT_ARTIFACT_ID = "artifactId";
  private static final String ELEMENT_VERSION = "version";
  private static final String ELEMENT_REPOSITORY = "repository";
  private static final String ELEMENT_DESCRIPTION = "description";

  private static final String LOCAL_REPOSITORY_ID = "local";
  private MavenServerDownloadListener myDownloadListener;

  public enum IndexUpdatingState {
    IDLE, WAITING, UPDATING
  }

  private volatile Path myTestIndicesDir;

  private volatile MavenIndexerWrapper myIndexer;
  private volatile MavenIndices myIndices;

  private final Object myUpdatingIndicesLock = new Object();
  private final List<MavenIndex> myWaitingIndices = new ArrayList<>();
  private volatile MavenIndex myUpdatingIndex;
  private final BackgroundTaskQueue myUpdatingQueue = new BackgroundTaskQueue(null, IndicesBundle.message("maven.indices.updating"));

  private volatile List<MavenArchetype> myUserArchetypes = new ArrayList<>();

  public static MavenIndicesManager getInstance() {
    return ServiceManager.getService(MavenIndicesManager.class);
  }

  @TestOnly
  public void setTestIndexDir(Path indicesDir) {
    myTestIndicesDir = indicesDir;
  }

  public void clear() {
    myUpdatingQueue.clear();
  }

  private synchronized MavenIndices getIndicesObject() {
    ensureInitialized();
    return myIndices;
  }

  private synchronized void ensureInitialized() {
    if (myIndices != null) return;

    myIndexer = MavenServerManager.getInstance().createIndexer();

    myDownloadListener = new MavenServerDownloadListener() {
      @Override
      public void artifactDownloaded(File file, String relativePath) {
        addArtifact(file, relativePath);
      }
    };
    MavenServerManager.getInstance().addDownloadListener(myDownloadListener);

    myIndices = new MavenIndices(myIndexer, getIndicesDir().toFile(), new MavenIndex.IndexListener() {
      @Override
      public void indexIsBroken(MavenIndex index) {
        scheduleUpdate(null, Collections.singletonList(index), false);
      }
    });

    loadUserArchetypes();
  }

  @NotNull
  private Path getIndicesDir() {
    return myTestIndicesDir == null
           ? MavenUtil.getPluginSystemDir("Indices")
           : myTestIndicesDir;
  }

  @Override
  public void dispose() {
    doShutdown();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      PathKt.delete(getIndicesDir());
    }
  }

  private synchronized void doShutdown() {
    if (myDownloadListener != null) {
      MavenServerManager.getInstance().removeDownloadListener(myDownloadListener);
      myDownloadListener = null;
    }

    if (myIndices != null) {
      try {
        myIndices.close();
      }
      catch (Exception e) {
        MavenLog.LOG.error("", e);
      }
      myIndices = null;
    }

    clear();
    myIndexer = null;
  }

  @TestOnly
  public void doShutdownInTests() {
    doShutdown();
  }

  public List<MavenIndex> getIndices() {
    return getIndicesObject().getIndices();
  }

  public synchronized List<MavenIndex> ensureIndicesExist(Project project,
                                                          File localRepository,
                                                          Collection<Pair<String, String>> remoteRepositoriesIdsAndUrls) {
    // MavenIndices.add method returns an existing index if it has already been added, thus we have to use set here.
    LinkedHashSet<MavenIndex> result = new LinkedHashSet<>();

    MavenIndices indicesObjectCache = getIndicesObject();

    try {
      MavenIndex localIndex = indicesObjectCache.add(LOCAL_REPOSITORY_ID, localRepository.getPath(), MavenIndex.Kind.LOCAL);
      result.add(localIndex);
      if (localIndex.getUpdateTimestamp() == -1) {
        scheduleUpdate(project, Collections.singletonList(localIndex));
      }
    }
    catch (MavenIndexException e) {
      MavenLog.LOG.warn(e);
    }

    for (Pair<String, String> eachIdAndUrl : remoteRepositoriesIdsAndUrls) {
      try {
        result.add(indicesObjectCache.add(eachIdAndUrl.first, eachIdAndUrl.second, MavenIndex.Kind.REMOTE));
      }
      catch (MavenIndexException e) {
        MavenLog.LOG.warn(e);
      }
    }

    return new ArrayList<>(result);
  }

  private void addArtifact(File artifactFile, String relativePath) {
    String repositoryPath = getRepositoryUrl(artifactFile, relativePath);

    MavenIndex index = getIndicesObject().find(repositoryPath, MavenIndex.Kind.LOCAL);
    if (index != null) {
      index.addArtifact(artifactFile);
    }
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

  public Promise<Void> scheduleUpdate(Project project, List<MavenIndex> indices) {
    return scheduleUpdate(project, indices, true);
  }

  private Promise<Void> scheduleUpdate(final Project projectOrNull, List<MavenIndex> indices, final boolean fullUpdate) {
    final List<MavenIndex> toSchedule = new ArrayList<>();

    synchronized (myUpdatingIndicesLock) {
      for (MavenIndex each : indices) {
        if (myWaitingIndices.contains(each)) continue;
        toSchedule.add(each);
      }

      myWaitingIndices.addAll(toSchedule);
    }
    if (toSchedule.isEmpty()) {
      return Promises.resolvedPromise();
    }

    final AsyncPromise<Void> promise = new AsyncPromise<>();
    myUpdatingQueue.run(new Task.Backgroundable(projectOrNull, IndicesBundle.message("maven.indices.updating"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          indicator.setIndeterminate(false);
          doUpdateIndices(projectOrNull, toSchedule, fullUpdate, new MavenProgressIndicator(indicator));
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

  private void doUpdateIndices(final Project projectOrNull, List<MavenIndex> indices, boolean fullUpdate, MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    MavenLog.LOG.assertTrue(!fullUpdate || projectOrNull != null);

    List<MavenIndex> remainingWaiting = new ArrayList<>(indices);

    try {
      for (MavenIndex each : indices) {
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

    AccessToken accessToken = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      settings = project.isDisposed() ? null : MavenProjectsManager.getInstance(project).getGeneralSettings().clone();
    }
    finally {
      accessToken.finish();
    }

    if (settings == null) {
      // project was closed
      indicator.cancel();
      indicator.checkCanceled();
    }

    return settings;
  }

  public IndexUpdatingState getUpdatingState(MavenIndex index) {
    synchronized (myUpdatingIndicesLock) {
      if (myUpdatingIndex == index) return IndexUpdatingState.UPDATING;
      if (myWaitingIndices.contains(index)) return IndexUpdatingState.WAITING;
      return IndexUpdatingState.IDLE;
    }
  }

  public synchronized Set<MavenArchetype> getArchetypes() {
    ensureInitialized();
    Set<MavenArchetype> result = new THashSet<>(myIndexer.getArchetypes());
    result.addAll(myUserArchetypes);
    for (MavenIndex index : myIndices.getIndices()) {
      result.addAll(index.getArchetypes());
    }

    for (MavenArchetypesProvider each : Extensions.getExtensions(MavenArchetypesProvider.EP_NAME)) {
      result.addAll(each.getArchetypes());
    }
    return result;
  }

  public synchronized void addArchetype(MavenArchetype archetype) {
    ensureInitialized();

    int idx = myUserArchetypes.indexOf(archetype);
    if (idx >= 0) {
      myUserArchetypes.set(idx, archetype);
    }
    else {
      myUserArchetypes.add(archetype);
    }

    saveUserArchetypes();
  }

  private void loadUserArchetypes() {
    try {
      Path file = getUserArchetypesFile();
      if (!PathKt.exists(file)) {
        return;
      }

      // Store artifact to set to remove duplicate created by old IDEA (https://youtrack.jetbrains.com/issue/IDEA-72105)
      Collection<MavenArchetype> result = new LinkedHashSet<>();

      List<Element> children = JdomKt.loadElement(file).getChildren(ELEMENT_ARCHETYPE);
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

      myUserArchetypes = listResult;
    }
    catch (IOException | JDOMException e) {
      MavenLog.LOG.warn(e);
    }
  }

  private void saveUserArchetypes() {
    Element root = new Element(ELEMENT_ARCHETYPES);
    for (MavenArchetype each : myUserArchetypes) {
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
}
