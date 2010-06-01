/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryTableAttachHandler;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.converters.repositories.MavenRepositoriesProvider;
import org.jetbrains.idea.maven.execution.SoutMavenConsole;
import org.jetbrains.idea.maven.facade.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.facade.MavenFacadeManager;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
import org.jetbrains.idea.maven.project.MavenEmbeddersManager;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;

/**
 * @author Gregory.Shrago
 */
public class RepositoryAttachHandler implements LibraryTableAttachHandler {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.idea.maven.utils.RepositoryAttachHandler");

  public String getLongName() {
    return "Attach Classes from Repository...";
  }

  public String getShortName() {
    return "Classes from Repository...";
  }

  public Icon getIcon() {
    return MavenIcons.MAVEN_ICON;
  }

  public ActionCallback performAttach(final Project project, final NullableComputable<Library.ModifiableModel> modelProvider) {

    final RepositoryAttachDialog dialog = new RepositoryAttachDialog(project, false);
    dialog.setTitle(getLongName());
    dialog.show();
    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      final ActionCallback callback = new ActionCallback();
      final String copyTo = dialog.getDirectoryPath();
      final String coord = dialog.getCoordinateText();
      resolveLibrary(project, coord, dialog.getRepositories(), false, new Processor<List<MavenArtifact>>() {
        public boolean process(final List<MavenArtifact> artifacts) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              final Library.ModifiableModel modifiableModel = modelProvider.compute();
              if (modifiableModel == null) {
                callback.setRejected();
              }
              else {
                replaceLibraryData(project, modifiableModel, artifacts, copyTo);
                callback.setDone();
              }
            }
          });
          final boolean nothingRetrieved = artifacts.isEmpty();
          final StringBuilder sb = new StringBuilder();
          if (nothingRetrieved) {
            sb.append("No files were downloaded for ").append(coord);
          }
          else {
            sb.append("The following files were downloaded:<br>");
            sb.append("<ol>");
            for (MavenArtifact each : artifacts) {
              sb.append("<li>");
              sb.append(each.getFile().getName());
              sb.append("</li>");
            }
            sb.append("</ol>");
          }
          final String title = "Attach Jars From Repository";
          if (nothingRetrieved && ModalityState.current().dominates(ModalityState.NON_MODAL)) {
            Messages.showErrorDialog(project, sb.toString(), title);
          }
          else {
            Notifications.Bus.notify(new Notification("Repository", sb.toString(), title,
                                                      nothingRetrieved ? NotificationType.WARNING : NotificationType.INFORMATION),
                                     NotificationDisplayType.STICKY_BALLOON, project);
          }
          return true;
        }
      });
      return callback;
    }
    return new ActionCallback.Rejected();
  }

  private static String getMavenCoordinate(String libraryName) {
    return libraryName.substring("Managed: ".length());
  }

  public ActionCallback refreshLibrary(final Project project, final Library.ModifiableModel library) {
    final ActionCallback result = new ActionCallback();
    final String coord = getMavenCoordinate(library.getName());
    resolveLibrary(project, coord, getDefaultRepositories(), true, new Processor<List<MavenArtifact>>() {
      public boolean process(final List<MavenArtifact> artifacts) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            replaceLibraryData(project, library, artifacts, null);
            result.setDone();
          }
        });
        return true;
      }
    });
    return result;
  }

  private static void replaceLibraryData(Project project,
                                         Library.ModifiableModel library,
                                         Collection<MavenArtifact> artifacts,
                                         String copyTo) {
    final String repoUrl = getLocalRepositoryUrl(project);
    for (OrderRootType type : OrderRootType.getAllTypes()) {
      for (String url : library.getUrls(type)) {
        if (url.startsWith(repoUrl)) {
          library.removeRoot(url, type);
        }
      }
    }
    for (MavenArtifact each : artifacts) {
      try {
        File repoFile = each.getFile();
        File toFile = repoFile;
        if (copyTo != null) {
          toFile = new File(copyTo, repoFile.getName());
          if (repoFile.exists()) {
            FileUtil.copy(repoFile, toFile);
          }
        }
        String url = VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(toFile.getPath()));
        library.addRoot(url, OrderRootType.CLASSES);
      }
      catch (MalformedURLException e) {
        LOG.warn(e);
      }
      catch (IOException e) {
        LOG.warn(e);
      }
    }
  }

  private static String getLocalRepositoryUrl(Project project) {
    File file = MavenProjectsManager.getInstance(project).getGeneralSettings().getEffectiveLocalRepository();
    return VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(file.getPath()));
  }

  public static void searchArtifacts(final Project project, String coord,
                                     final PairProcessor<Collection<Pair<MavenArtifactInfo, MavenRepositoryInfo>>, Boolean> resultProcessor,
                                     final Processor<Collection<MavenRepositoryInfo>> repoProcessor) {
    if (coord == null) return;
    final MavenArtifactInfo template = createTemplate(coord, null);
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Maven", false) {

      public void run(@NotNull ProgressIndicator indicator) {
        final Ref<List<Pair<MavenArtifactInfo, MavenRepositoryInfo>>> result
          = Ref.create(Collections.<Pair<MavenArtifactInfo, MavenRepositoryInfo>>emptyList());
        final Ref<List<MavenRepositoryInfo>> result2 = Ref.create(Collections.<MavenRepositoryInfo>emptyList());
        final Ref<Boolean> tooManyRef = Ref.create(Boolean.FALSE);
        try {
          MavenFacadeManager facade = MavenFacadeManager.getInstance();

          final String[] nexusUrls = getDefaultNexusUrls();
          final List<Pair<MavenArtifactInfo, MavenRepositoryInfo>> resultList
            = new ArrayList<Pair<MavenArtifactInfo, MavenRepositoryInfo>>();
          final List<MavenRepositoryInfo> result2List = new ArrayList<MavenRepositoryInfo>();
          for (String nexusUrl : nexusUrls) {
            final List<MavenArtifactInfo> artifacts;
            try {
              artifacts = facade.findArtifacts(template, nexusUrl);
            }
            catch (Exception ex) {
              LOG.warn("Accessing Nexus at: " + nexusUrl, ex);
              continue;
            }
            if (artifacts == null) {
              tooManyRef.set(Boolean.TRUE);
            }
            else if (!artifacts.isEmpty()) {
              final List<MavenRepositoryInfo> repositories = facade.getRepositories(nexusUrl);
              final HashMap<String, MavenRepositoryInfo> map = new HashMap<String, MavenRepositoryInfo>();
              for (MavenRepositoryInfo repository : repositories) {
                map.put(repository.getId(), repository);
              }
              result2List.addAll(repositories);
              for (MavenArtifactInfo artifact : artifacts) {
                resultList.add(Pair.create(artifact, map.get(artifact.getRepositoryId())));
              }
            }
          }
          result.set(resultList);
          result2.set(result2List);
        }
        catch (Exception e) {
          handleError(null, e);
        }
        finally {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              repoProcessor.process(result2.get());
              resultProcessor.process(result.get(), tooManyRef.get());
            }
          });
        }
      }
    });
  }

  public static void searchRepositories(final Project project, final Processor<Collection<MavenRepositoryInfo>> resultProcessor) {
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Maven", false) {

      public void run(@NotNull ProgressIndicator indicator) {
        final Ref<List<MavenRepositoryInfo>> result = Ref.create(Collections.<MavenRepositoryInfo>emptyList());
        try {
          final String[] nexusUrls = getDefaultNexusUrls();
          final MavenFacadeManager manager = MavenFacadeManager.getInstance();
          final ArrayList<MavenRepositoryInfo> repoList = new ArrayList<MavenRepositoryInfo>();
          for (String nexusUrl : nexusUrls) {
            final List<MavenRepositoryInfo> repositories;
            try {
              repositories = manager.getRepositories(nexusUrl);
            }
            catch (Exception ex) {
              LOG.warn("Accessing Nexus at: " + nexusUrl, ex);
              continue;
            }
            repoList.addAll(repositories);
          }
          result.set(repoList);
        }
        catch (Exception e) {
          handleError(null, e);
        }
        finally {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              resultProcessor.process(result.get());
            }
          });
        }
      }
    });
  }

  private static MavenArtifactInfo createTemplate(String coord, String packaging) {
    final String[] parts = coord.split(":");
    if (parts.length == 1 && parts[0].length() > 0 && Character.isUpperCase(parts[0].charAt(0))) {
      return new MavenArtifactInfo(null, null, null, packaging, null, parts[0], null);
    }
    else {
      return new MavenArtifactInfo(parts.length > 0 ? parts[0] : null,
                                   parts.length > 1 ? parts[1] : null,
                                   parts.length > 2 ? parts[2] : null,
                                   packaging,
                                   null);
    }
  }

  private static void handleError(String message, Exception e) {
    LOG.error(message, e);
  }

  public static void resolveLibrary(final Project project,
                                    final String libName,
                                    final Collection<MavenRepositoryInfo> repositories,
                                    boolean modal,
                                    final Processor<List<MavenArtifact>> resultProcessor) {
    Task task;
    if (modal) {
      task = new Task.Modal(project, "Maven", false) {
        public void run(@NotNull ProgressIndicator indicator) {
          doResolveInner(project, createTemplate(libName, "jar"), repositories, resultProcessor, indicator);
        }
      };
    }
    else {
      task = new Task.Backgroundable(project, "Maven", false, PerformInBackgroundOption.DEAF) {
        public void run(@NotNull ProgressIndicator indicator) {
          doResolveInner(project, createTemplate(libName, "jar"), repositories, resultProcessor, indicator);
        }

        @Override
        public boolean shouldStartInBackground() {
          return false;
        }
      };
    }

    ProgressManager.getInstance().run(task);
  }

  private static void doResolveInner(Project project,
                                     MavenArtifactInfo artifact,
                                     Collection<MavenRepositoryInfo> repositories,
                                     final Processor<List<MavenArtifact>> resultProcessor,
                                     ProgressIndicator indicator) {
    final Ref<List<MavenArtifact>> result = new Ref<List<MavenArtifact>>();

    MavenEmbeddersManager manager = MavenProjectsManager.getInstance(project).getEmbeddersManager();
    MavenEmbedderWrapper embedder = manager.getEmbedder(MavenEmbeddersManager.FOR_DOWNLOAD);
    try {
      embedder.customizeForResolve(new SoutMavenConsole(), new MavenProgressIndicator(indicator));
      List<MavenArtifact> resolved = embedder.resolveTransitively(Collections.singletonList(artifact),
                                                                  convertRepositories(repositories));
      result.set(ContainerUtil.findAll(resolved, new Condition<MavenArtifact>() {
        public boolean value(MavenArtifact mavenArtifact) {
          return mavenArtifact.isResolved();
        }
      }));
    }
    catch (MavenProcessCanceledException e) {
      return;
    }
    finally {
      manager.release(embedder);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          resultProcessor.process(result.get());
        }
      });
    }
  }

  private static List<MavenRemoteRepository> convertRepositories(Collection<MavenRepositoryInfo> infos) {
    List<MavenRemoteRepository> result = new ArrayList<MavenRemoteRepository>(infos.size());
    for (MavenRepositoryInfo each : infos) {
      result.add(new MavenRemoteRepository(each.getId(), each.getName(), each.getUrl(), null, null, null));
    }
    return result;
  }

  private static String[] getDefaultNexusUrls() {
    return new String[]{
      "http://oss.sonatype.org/service/local/",
      "http://repository.sonatype.org/service/local/",
      //"http://maven.labs.intellij.net:8081/nexus/service/local/"
    };
  }

  public static List<MavenRepositoryInfo> getDefaultRepositories() {
    List<MavenRepositoryInfo> result = new SmartList<MavenRepositoryInfo>();
    final MavenRepositoriesProvider provider = MavenRepositoriesProvider.getInstance();
    for (String id : provider.getRepositoryIds()) {
      final String url = provider.getRepositoryUrl(id);
      if (url != null) {
       result.add(new MavenRepositoryInfo(id, provider.getRepositoryName(id), url));
      }
    }
    return result;
  }

  //public static void registerLibraryWatcher(final Project project) {
  //  final Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);
  //  final Runnable syncRunnable = new Runnable() {
  //    boolean myInRefresh;
  //
  //    public void run() {
  //      if (myInRefresh) return;
  //      alarm.cancelAllRequests();
  //      alarm.addRequest(new Runnable() {
  //        public void run() {
  //          final LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
  //          final Set<String> coordSet = new HashSet<String>();
  //          for (final Library library : libraryTable.getLibraries()) {
  //            if (!INSTANCE.isMyLibrary(project, library)) continue;
  //            coordSet.add(getMavenCoordinate(library));
  //          }
  //          resolveLibrary(project, coordSet, false, new Processor<Map<String, List<ArtifactType>>>() {
  //            public boolean process(final Map<String, List<ArtifactType>> resolveResult) {
  //              try {
  //                myInRefresh = true;
  //                final Map<String, Library> libMap = new HashMap<String, Library>();
  //                for (final Library library : libraryTable.getLibraries()) {
  //                  if (!INSTANCE.isMyLibrary(project, library)) continue;
  //                  libMap.put(getMavenCoordinate(library), library);
  //                }
  //
  //                final Map<Library, Collection<ArtifactType>> map = new HashMap<Library, Collection<ArtifactType>>();
  //                for (String coord : resolveResult.keySet()) {
  //                  final Library library = libMap.get(coord);
  //                  if (library == null) continue;
  //                  map.put(library, resolveResult.get(coord));
  //                }
  //                INSTANCE.refreshLibraries(project, map);
  //              }
  //              finally {
  //                myInRefresh = false;
  //              }
  //              return true;
  //            }
  //          });
  //        }
  //      }, 2000);
  //    }
  //  };
  //  final LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
  //  libraryTable.addListener(new LibraryTable.Listener() {
  //    public void afterLibraryAdded(Library newLibrary) {
  //    }
  //
  //    public void afterLibraryRenamed(Library library) {
  //      syncRunnable.run();
  //    }
  //
  //    public void beforeLibraryRemoved(Library library) {
  //    }
  //
  //    public void afterLibraryRemoved(Library library) {
  //      syncRunnable.run();
  //    }
  //  }, project);
  //  for (Library library : libraryTable.getLibraries()) {
  //    library.getRootProvider().addRootSetChangedListener(new RootProvider.RootSetChangedListener() {
  //      public void rootSetChanged(RootProvider wrapper) {
  //        syncRunnable.run();
  //      }
  //    }, library);
  //  }
  //  syncRunnable.run();
  //}

  private void refreshLibraries(final Project project, final Map<Library, Collection<MavenArtifact>> map) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (Library library : map.keySet()) {
          final Library.ModifiableModel modifiableModel = library.getModifiableModel();
          replaceLibraryData(project, modifiableModel, map.get(library), null);
          modifiableModel.commit();
        }
      }
    });
  }

  //public static RemoteTransferListener fromProgressIndicator(final ProgressIndicator indicator) {
  //  return new RemoteTransferListenerImpl() {
  //
  //    final Map<Resource, Long> myDownloads = new ConcurrentHashMap<Resource, Long>();
  //    @Override
  //    public void transferInitiated(TransferEvent transferEvent) {
  //      final String message = transferEvent.getRequestType() == TransferEvent.RequestType.PUT ? "Uploading" : "Downloading";
  //      final String url = transferEvent.getRepositoryUrl();
  //      indicator.setText(message + ": " + url + "/" + transferEvent.getResource().getResourceName());
  //    }
  //
  //    @Override
  //    public void transferStarted(TransferEvent transferEvent) {
  //      // nothing
  //    }
  //
  //    @Override
  //    public void transferProgress(TransferEvent transferEvent, int length) {
  //      final Resource resource = transferEvent.getResource();
  //      final long curComplete;
  //      if (!myDownloads.containsKey(resource)) {
  //        curComplete = length;
  //      }
  //      else {
  //        final long complete = myDownloads.get(resource).longValue();
  //        curComplete = complete + length;
  //      }
  //      myDownloads.put(resource, new Long(curComplete));
  //
  //      long curTotal = 0;
  //      long curProgress = 0;
  //      boolean unknown = false;
  //      for (Resource res : myDownloads.keySet()) {
  //        final long total = res.getResourceContentLength();
  //        final long complete = myDownloads.get(res).longValue();
  //        if (total == -1) unknown = true;
  //        else curTotal += total;
  //        curProgress += complete;
  //      }
  //
  //      final StringBuilder sb = new StringBuilder();
  //      if (curTotal >= 1024) {
  //        sb.append((curProgress / 1024) + "/" + (curTotal == -1 ? "?" : (curTotal / 1024) + "K"));
  //      }
  //      else {
  //        sb.append(curProgress + "/" + (curTotal == -1 ? "?" : curTotal + "b"));
  //      }
  //      if (unknown) {
  //        indicator.setIndeterminate(true);
  //      }
  //      else {
  //        indicator.setIndeterminate(false);
  //        indicator.setFraction((double)curProgress / curTotal);
  //      }
  //      indicator.setText(sb.toString());
  //      indicator.setText2(transferEvent.getResource().getResourceName());
  //    }
  //
  //    @Override
  //    public void transferCompleted(TransferEvent transferEvent) {
  //      transferProgress(transferEvent, 0);
  //      if (true) return;
  //      final StringBuilder sb = new StringBuilder();
  //      final long contentLength = transferEvent.getResource().getResourceContentLength();
  //      if (contentLength != -1) {
  //        String type =
  //          (transferEvent.getRequestType() == TransferEvent.RequestType.PUT ? "uploaded" : "downloaded");
  //        sb.append(contentLength >= 1024 ? (contentLength / 1024) + "K" : contentLength + "b");
  //        String name = transferEvent.getResource().getResourceName();
  //        name = name.substring(name.lastIndexOf('/') + 1, name.length());
  //        sb.append(" ");
  //        sb.append(type);
  //        sb.append("  (");
  //        sb.append(name);
  //        sb.append(")");
  //      }
  //      indicator.setText(sb.toString());
  //      //myDownloads.remove(transferEvent.getResource());
  //    }
  //
  //    @Override
  //    public void transferError(TransferEvent transferEvent) {
  //      indicator.setText2(transferEvent.getException().getMessage());
  //    }
  //
  //    @Override
  //    public void debug(String s) {
  //      indicator.setText2(s);
  //    }
  //  };
  //}
}
