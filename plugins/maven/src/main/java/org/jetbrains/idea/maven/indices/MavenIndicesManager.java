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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.THashSet;
import org.apache.maven.archetype.catalog.Archetype;
import org.apache.maven.archetype.catalog.ArchetypeCatalog;
import org.apache.maven.archetype.source.ArchetypeDataSource;
import org.apache.maven.archetype.source.ArchetypeDataSourceException;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenRehighlighter;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MavenIndicesManager implements ApplicationComponent {
  private static final String ELEMENT_ARCHETYPES = "archetypes";
  private static final String ELEMENT_ARCHETYPE = "archetype";
  private static final String ELEMENT_GROUP_ID = "groupId";
  private static final String ELEMENT_ARTIFACT_ID = "artifactId";
  private static final String ELEMENT_VERSION = "version";
  private static final String ELEMENT_REPOSITORY = "repository";
  private static final String ELEMENT_DESCRIPTION = "description";

  private static final String LOCAL_REPOSITORY_ID = "local";

  public enum IndexUpdatingState {
    IDLE, WAITING, UPDATING
  }

  private volatile File myTestIndicesDir;

  private volatile MavenEmbedderWrapper myEmbedder;
  private volatile MavenIndices myIndices;

  private final Object myUpdatingIndicesLock = new Object();
  private final List<MavenIndex> myWaitingIndices = new ArrayList<MavenIndex>();
  private volatile MavenIndex myUpdatingIndex;
  private final BackgroundTaskQueue myUpdatingQueue = new BackgroundTaskQueue(IndicesBundle.message("maven.indices.updating"));

  private volatile List<ArchetypeInfo> myUserArchetypes = new ArrayList<ArchetypeInfo>();

  public static MavenIndicesManager getInstance() {
    return ApplicationManager.getApplication().getComponent(MavenIndicesManager.class);
  }

  @NotNull
  public String getComponentName() {
    return getClass().getSimpleName();
  }

  public void initComponent() {
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

    MavenGeneralSettings defaultSettings = new MavenGeneralSettings();
    myEmbedder = MavenEmbedderWrapper.create(defaultSettings);
    myIndices = new MavenIndices(myEmbedder, getIndicesDir(), new MavenIndex.IndexListener() {
      public void indexIsBroken(MavenIndex index) {
        scheduleUpdate(null, Collections.singletonList(index), false);
      }
    });

    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      public void run() {
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
    if (myIndices != null) {
      try {
        myIndices.close();
      }
      catch (Exception e) {
        MavenLog.LOG.error("", e);
      }
      myIndices = null;
    }

    if (myEmbedder != null) {
      try {
        myEmbedder.release();
      }
      catch (Exception e) {
        MavenLog.LOG.error("", e);
      }
      myEmbedder = null;
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

  public void addArtifact(File artifactFile, String name) {
    String repositoryPath = getRepositoryUrl(artifactFile, name);

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
        doUpdateIndices(projectOrNull, toSchedule, fullUpdate, indicator);
      }
    });
  }

  private void doUpdateIndices(final Project projectOrNull, List<MavenIndex> indices, boolean fullUpdate, ProgressIndicator indicator) {
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
          MavenEmbedderWrapper embedderToUse = null;
          if (fullUpdate) {
            embedderToUse = new ReadAction<MavenEmbedderWrapper>() {
              @Override
              protected void run(Result<MavenEmbedderWrapper> result) throws Throwable {
                if (!projectOrNull.isDisposed()) {
                  MavenGeneralSettings settings = MavenProjectsManager.getInstance(projectOrNull).getGeneralSettings();
                  result.setResult(MavenEmbedderWrapper.create(settings));
                }
              }
            }.execute().getResultObject();
            if (embedderToUse == null /* project disposed */) throw new ProcessCanceledException();
          }

          try {
            getIndicesObject().updateOrRepair(each, embedderToUse, fullUpdate, indicator);
          }
          finally {
            if (embedderToUse != null) embedderToUse.release();
          }

          scheduleRehighlightAllPoms(projectOrNull);
        }
        finally {
          synchronized (myUpdatingIndicesLock) {
            myUpdatingIndex = null;
          }
        }
      }
    }
    catch (ProcessCanceledException ignore) {
    }
    finally {
      synchronized (myUpdatingIndicesLock) {
        myWaitingIndices.removeAll(remainingWaiting);
      }
    }
  }

  public IndexUpdatingState getUpdatingState(MavenIndex index) {
    synchronized (myUpdatingIndicesLock) {
      if (myUpdatingIndex == index) return IndexUpdatingState.UPDATING;
      if (myWaitingIndices.contains(index)) return IndexUpdatingState.WAITING;
      return IndexUpdatingState.IDLE;
    }
  }

  private void scheduleRehighlightAllPoms(final Project projectOrNull) {
    if (projectOrNull == null) return;
    MavenRehighlighter.rehighlight(projectOrNull);
  }

  public synchronized Set<ArchetypeInfo> getArchetypes() {
    ensureInitialized();
    Set<ArchetypeInfo> result = new THashSet<ArchetypeInfo>();
    result.addAll(getArchetypesFrom("internal-catalog"));
    result.addAll(getArchetypesFrom("nexus"));
    result.addAll(myUserArchetypes);

    for (MavenArchetypesProvider each : Extensions.getExtensions(MavenArchetypesProvider.EP_NAME)) {
      result.addAll(each.getArchetypes());
    }
    return result;
  }

  public synchronized void addArchetype(ArchetypeInfo archetype) {
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
      List<ArchetypeInfo> result = new ArrayList<ArchetypeInfo>();
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

        result.add(new ArchetypeInfo(groupId, artifactId, version, repository, description));
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
    for (ArchetypeInfo each : myUserArchetypes) {
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

  private List<ArchetypeInfo> getArchetypesFrom(String roleHint) {
    try {
      ArchetypeDataSource source = myEmbedder.getComponent(ArchetypeDataSource.class, roleHint);
      ArchetypeCatalog catalog = source.getArchetypeCatalog(new Properties());

      List<ArchetypeInfo> result = new ArrayList<ArchetypeInfo>();
      for (Archetype each : (Iterable<? extends Archetype>)catalog.getArchetypes()) {
        result.add(new ArchetypeInfo(each));
      }

      return result;
    }
    catch (ArchetypeDataSourceException e) {
      MavenLog.LOG.warn(e);
    }
    return Collections.emptyList();
  }
}
