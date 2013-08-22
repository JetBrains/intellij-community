/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils.library;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.importing.MavenExtraArtifactType;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.services.MavenRepositoryServicesManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author gregsh
 */
public class RepositoryLibrarySynchronizer implements StartupActivity, DumbAware{
  @Override
  public void runActivity(@NotNull final Project project) {
    StartupManager.getInstance(project).registerPostStartupActivity(new DumbAwareRunnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().invokeLater(new DumbAwareRunnable() {
          @Override
          public void run() {
            final MultiMap<Library, Module> libraries = collectLibraries(project);
            if (libraries.isEmpty()) return;
            Task task = new Task.Backgroundable(project, "Maven", false) {
              public void run(@NotNull ProgressIndicator indicator) {
                ensureLibrariesAreDownloaded(project, libraries, indicator);
              }
            };
            ProgressManager.getInstance().run(task);
          }
        }, project.getDisposed());
      }
    });
  }

  private static MultiMap<Library, Module> collectLibraries(final Project project) {
    final PersistentLibraryKind<RepositoryLibraryProperties> libraryKind = RepositoryLibraryType.getInstance().getKind();
    final MultiMap<Library, Module> result = new MultiMap<Library, Module>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        for (final Module module : ModuleManager.getInstance(project).getModules()) {
          OrderEnumerator.orderEntries(module).withoutSdk().forEachLibrary(new Processor<Library>() {
            @Override
            public boolean process(Library library) {
              if (((LibraryEx)library).getKind() == libraryKind) {
                result.putValue(library, module);
              }
              return true;
            }
          });
        }
        for (Library library : ProjectLibraryTable.getInstance(project).getLibraries()) {
          if (!result.containsKey(library) && ((LibraryEx)library).getKind() == libraryKind) {
            result.put(library, Collections.<Module>emptyList());
          }
        }
      }
    });
    return result;
  }

  private static void ensureLibrariesAreDownloaded(final Project project, final MultiMap<Library, Module> libraries, ProgressIndicator indicator) {
    THashMap<Library, Pair<Integer, String>> libsToSync = new THashMap<Library, Pair<Integer, String>>();

    String localRepositoryPath = FileUtil.toSystemIndependentName(MavenProjectsManager.getInstance(project).getLocalRepository().getPath());

    // todo group libraries together later
    AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      for (final Library library : libraries.keySet()) {
        String[] urls = library.getUrls(OrderRootType.CLASSES);
        VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
        // check library state via URLs vs files number test
        if (urls.length <= files.length) continue;
        int flag = 0;
        if (library.getUrls(OrderRootType.SOURCES).length > 0) flag |= 0x1;
        if (library.getUrls(JavadocOrderRootType.getInstance()).length > 0) flag |= 0x2;

        String firstUrl = StringUtil.trimStart(PathUtil.getLocalPath(urls[0]), JarFileSystem.PROTOCOL_PREFIX);
        String storagePath = firstUrl.startsWith(localRepositoryPath) ? null : FileUtil.toSystemDependentName(
          PathUtil.getParentPath(firstUrl));

        libsToSync.put(library, Pair.create(flag, storagePath));
      }
    }
    finally {
      token.finish();
    }
    if (libsToSync.isEmpty()) return;

    Map<String, MavenRepositoryInfo> repoMap = new THashMap<String, MavenRepositoryInfo>();
    for (String url : MavenRepositoryServicesManager.getServiceUrls()) {
      for (MavenRepositoryInfo info : MavenRepositoryServicesManager.getRepositories(url)) {
        repoMap.put(info.getId(), info);
      }
    }
    final List<MavenArtifact> dowloaded = new ArrayList<MavenArtifact>();
    for (final Library library : libsToSync.keySet()) {
      int flag = libsToSync.get(library).first;
      final String storagePath = libsToSync.get(library).second;
      final SmartList<MavenExtraArtifactType> extraTypes = new SmartList<MavenExtraArtifactType>();
      if ((flag & 0x1) != 0) extraTypes.add(MavenExtraArtifactType.SOURCES);
      if ((flag & 0x2) != 0) extraTypes.add(MavenExtraArtifactType.DOCS);

      RepositoryLibraryProperties properties = (RepositoryLibraryProperties)((LibraryEx)library).getProperties();
      MavenId mavenId = RepositoryAttachHandler.getMavenId(properties.getMavenId());
      List<MavenId> idsToResolve = Collections.singletonList(mavenId);

      indicator.setText("Synchronizing " + mavenId.getDisplayString() + "...");

      // detect repositories: we need to re-search artifacts, it's faster than to query hundreds of them.
      ArrayList<MavenRepositoryInfo> repositories = new ArrayList<MavenRepositoryInfo>();
      for (String url : MavenRepositoryServicesManager.getServiceUrls()) {
        List<MavenArtifactInfo> artifacts = MavenRepositoryServicesManager.findArtifacts(new MavenArtifactInfo(mavenId, "jar", null), url);
        for (MavenArtifactInfo artifact : artifacts) {
          ContainerUtil.addIfNotNull(repositories, repoMap.get(artifact.getRepositoryId()));
        }
      }

      RepositoryAttachHandler.doResolveInner(
        project, idsToResolve, extraTypes, repositories,
        new Processor<List<MavenArtifact>>() {
          @Override
          public boolean process(final List<MavenArtifact> artifacts) {
            dowloaded.addAll(artifacts);
            ApplicationManager.getApplication().invokeLater(new DumbAwareRunnable() {
              @Override
              public void run() {
                if (((LibraryEx)library).isDisposed()) return;
                AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(RepositoryLibrarySynchronizer.class);
                try {
                  List<OrderRoot> roots = RepositoryAttachHandler.createRoots(artifacts, storagePath);
                  Library.ModifiableModel model = library.getModifiableModel();
                  for (OrderRoot root : roots) {
                    String fileName = PathUtil.getFileName(PathUtil.getLocalPath(root.getFile().getUrl()));
                    for (String existingUrl : model.getUrls(root.getType())) {
                      if (Comparing.equal(PathUtil.getFileName(PathUtil.getLocalPath(existingUrl)), fileName)) {
                        model.removeRoot(existingUrl, root.getType());
                      }
                    }
                    model.addRoot(root.getFile(), root.getType());
                  }
                  model.commit();
                }
                finally {
                  token.finish();
                }
              }
            }, project.getDisposed());
            return true;
          }
        }, indicator);
    }
    RepositoryAttachHandler.notifyArtifactsDownloaded(project, dowloaded);
  }
}
