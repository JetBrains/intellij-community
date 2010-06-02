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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.THashSet;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.facade.MavenFacadeDownloadListener;
import org.jetbrains.idea.maven.facade.MavenFacadeManager;
import org.jetbrains.idea.maven.facade.MavenIndexerWrapper;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.*;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.*;

public class MavenIndicesManager {
  private static final String ELEMENT_ARCHETYPES = "archetypes";
  private static final String ELEMENT_ARCHETYPE = "archetype";
  private static final String ELEMENT_GROUP_ID = "groupId";
  private static final String ELEMENT_ARTIFACT_ID = "artifactId";
  private static final String ELEMENT_VERSION = "version";
  private static final String ELEMENT_REPOSITORY = "repository";
  private static final String ELEMENT_DESCRIPTION = "description";

  private static final String LOCAL_REPOSITORY_ID = "local";
  private MavenFacadeDownloadListener myDownloadListener;

  public enum IndexUpdatingState {
    IDLE, WAITING, UPDATING
  }

  private volatile File myTestIndicesDir;

  private volatile MavenIndexerWrapper myIndexer = MavenFacadeManager.getInstance().createIndexer();
  private volatile MavenIndices myIndices;

  private final Object myUpdatingIndicesLock = new Object();
  private final List<MavenIndex> myWaitingIndices = new ArrayList<MavenIndex>();
  private volatile MavenIndex myUpdatingIndex;
  private final BackgroundTaskQueue myUpdatingQueue = new BackgroundTaskQueue(IndicesBundle.message("maven.indices.updating"));

  private volatile List<MavenArchetype> myUserArchetypes = new ArrayList<MavenArchetype>();

  public static MavenIndicesManager getInstance() {
    return ServiceManager.getService(MavenIndicesManager.class);
  }

  @TestOnly
  public void setTestIndexDir(File indicesDir) {
    myTestIndicesDir = indicesDir;
  }

  private synchronized MavenIndices getIndicesObject() {
    ensureInitialized();
    return myIndices;
  }

  private synchronized void ensureInitialized() {
    if (myIndices != null) return;

    myIndexer = MavenFacadeManager.getInstance().createIndexer();

    myDownloadListener = new MavenFacadeDownloadListener() {
      public void artifactDownloaded(File file, String relativePath) throws RemoteException {
        addArtifact(file, relativePath);
      }
    };
    MavenFacadeManager.getInstance().addDownloadListener(myDownloadListener);

    myIndices = new MavenIndices(myIndexer, getIndicesDir(), new MavenIndex.IndexListener() {
      public void indexIsBroken(MavenIndex index) {
        scheduleUpdate(null, Collections.singletonList(index), false);
      }
    });

    Disposer.register(ApplicationManager.getApplication(), new Disposable() {
      public void dispose() {
        doShutdown();
      }
    });

    loadUserArchetypes();
  }

  private File getIndicesDir() {
    return myTestIndicesDir == null
           ? MavenUtil.getPluginSystemDir("Indices")
           : myTestIndicesDir;
  }

  public void disposeComponent() {
    doShutdown();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      FileUtil.delete(getIndicesDir());
    }
  }

  private synchronized void doShutdown() {
    if (myDownloadListener != null) {
      MavenFacadeManager.getInstance().removeDownloadListener(myDownloadListener);
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

    if (myIndexer != null) {
      try {
        myIndexer.release();
      }
      catch (Exception e) {
        MavenLog.LOG.error("", e);
      }
      myIndexer = null;
    }
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
    LinkedHashSet<MavenIndex> result = new LinkedHashSet<MavenIndex>();

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

    return new ArrayList<MavenIndex>(result);
  }

  private void addArtifact(File artifactFile, String relativePath) {
    String repositoryPath = getRepositoryUrl(artifactFile, relativePath);

    MavenIndex index = getIndicesObject().find(LOCAL_REPOSITORY_ID, repositoryPath, MavenIndex.Kind.LOCAL);
    if (index != null) {
      index.addArtifact(artifactFile);
    }
  }

  private String getRepositoryUrl(File artifactFile, String name) {
    List<String> parts = getArtifactParts(name);

    File result = artifactFile;
    for (int i = 0; i < parts.size(); i++) {
      result = result.getParentFile();
    }
    return result.getPath();
  }

  private List<String> getArtifactParts(String name) {
    return StringUtil.split(name, "/");
  }

  public void scheduleUpdate(Project project, List<MavenIndex> indices) {
    scheduleUpdate(project, indices, true);
  }

  private void scheduleUpdate(final Project projectOrNull, List<MavenIndex> indices, final boolean fullUpdate) {
    final List<MavenIndex> toSchedule = new ArrayList<MavenIndex>();

    synchronized (myUpdatingIndicesLock) {
      for (MavenIndex each : indices) {
        if (myWaitingIndices.contains(each)) continue;
        toSchedule.add(each);
      }

      myWaitingIndices.addAll(toSchedule);
    }

    myUpdatingQueue.run(new Task.Backgroundable(projectOrNull, IndicesBundle.message("maven.indices.updating"), true) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          doUpdateIndices(projectOrNull, toSchedule, fullUpdate, new MavenProgressIndicator(indicator));
        }
        catch (MavenProcessCanceledException ignore) {
        }
      }
    });
  }

  private void doUpdateIndices(final Project projectOrNull, List<MavenIndex> indices, boolean fullUpdate, MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    MavenLog.LOG.assertTrue(!fullUpdate || projectOrNull != null);

    List<MavenIndex> remainingWaiting = new ArrayList<MavenIndex>(indices);

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
          getIndicesObject().updateOrRepair(each, fullUpdate, fullUpdate ? getMavenSettings(projectOrNull) : null, indicator);
          if (projectOrNull != null) MavenRehighlighter.rehighlight(projectOrNull);
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

  private MavenGeneralSettings getMavenSettings(Project project) {
    return MavenProjectsManager.getInstance(project).getGeneralSettings();
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
    Set<MavenArchetype> result = new THashSet<MavenArchetype>(myIndexer.getArchetypes());
    result.addAll(myUserArchetypes);

    for (MavenArchetypesProvider each : Extensions.getExtensions(MavenArchetypesProvider.EP_NAME)) {
      result.addAll(each.getArchetypes());
    }
    return result;
  }

  public synchronized void addArchetype(MavenArchetype archetype) {
    ensureInitialized();
    myUserArchetypes.add(archetype);
    saveUserArchetypes();
  }

  private void loadUserArchetypes() {
    try {
      File file = getUserArchetypesFile();
      if (!file.exists()) return;

      Document doc = JDOMUtil.loadDocument(file);
      Element root = doc.getRootElement();
      if (root == null) return;
      List<MavenArchetype> result = new ArrayList<MavenArchetype>();
      for (Element each : (Iterable<? extends Element>)root.getChildren(ELEMENT_ARCHETYPE)) {
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

      myUserArchetypes = result;
    }
    catch (IOException e) {
      MavenLog.LOG.warn(e);
    }
    catch (JDOMException e) {
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
      File file = getUserArchetypesFile();
      file.getParentFile().mkdirs();
      JDOMUtil.writeDocument(new Document(root), file, "\n");
    }
    catch (IOException e) {
      MavenLog.LOG.warn(e);
    }
  }

  private File getUserArchetypesFile() {
    return new File(getIndicesDir(), "UserArchetypes.xml");
  }
}
