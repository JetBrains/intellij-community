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
import com.intellij.openapi.components.ServiceManager;
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
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.converters.repositories.MavenRepositoriesProvider;
import org.jetbrains.idea.maven.facade.nexus.ArtifactType;
import org.jetbrains.idea.maven.facade.nexus.RepositoryType;
import org.jetbrains.idea.maven.facade.remote.MavenFacade;
import org.jetbrains.idea.maven.facade.remote.MavenFacadeManager;
import org.jetbrains.idea.maven.facade.remote.RemoteTransferListener;
import org.jetbrains.idea.maven.facade.remote.impl.RemoteTransferListenerImpl;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
      resolveLibrary(project, Collections.singleton(coord), dialog.getRepositories(), false, new Processor<Map<String, List<ArtifactType>>>() {
        public boolean process(final Map<String, List<ArtifactType>> stringListMap) {
          final List<ArtifactType> result = stringListMap.get(coord);
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              final Library.ModifiableModel modifiableModel = modelProvider.compute();
              if (modifiableModel == null || result == null) {
                callback.setRejected();
              }
              else {
                replaceLibraryData(project, modifiableModel, result, copyTo);
                callback.setDone();
              }
            }
          });
          final boolean nothingRetrieved = result == null || result.isEmpty();
          final StringBuilder sb = new StringBuilder();
          if (nothingRetrieved) sb.append("No files were downloaded for ").append(coord);
          else {
            sb.append("The following files were downloaded:<br>");
            sb.append("<ol>");
            for (ArtifactType artifactType : result) {
              sb.append("<li>");
              final String file = artifactType.getResourceUri();
              sb.append(file.substring(file.lastIndexOf('/')));
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
                                                      nothingRetrieved? NotificationType.WARNING : NotificationType.INFORMATION), NotificationDisplayType.STICKY_BALLOON, project);
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
    resolveLibrary(project, Collections.singleton(coord), getDefaultRepositories(), true, new Processor<Map<String, List<ArtifactType>>>() {
      public boolean process(final Map<String, List<ArtifactType>> artifactTypes) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            replaceLibraryData(project, library, artifactTypes.get(coord), null);
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
                                         Collection<ArtifactType> artifactTypes,
                                         String copyTo) {
    final String repoUrl = VfsUtil.pathToUrl(MavenProjectsManager.getInstance(project).getGeneralSettings().getEffectiveLocalRepository().getPath());
    for (OrderRootType type : OrderRootType.getAllTypes()) {
      for (String url : library.getUrls(type)) {
        if (url.startsWith(repoUrl)) {
          library.removeRoot(url, type);
        }
      }
    }
    for (ArtifactType type : artifactTypes) {
      try {
        final String url = VfsUtil.convertFromUrl(new URL(type.getResourceUri()));
        final String targetUrl;
        if (copyTo != null) {
          final File repoFile = new File(FileUtil.toSystemDependentName(VfsUtil.urlToPath(url)));
          final File toFile = new File(copyTo, repoFile.getName());
          if (repoFile.exists()) {
            FileUtil.copy(repoFile, toFile);
          }
          targetUrl = VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(toFile.getPath()));
        }
        else targetUrl = url;
        library.addRoot(targetUrl, OrderRootType.CLASSES);
      }
      catch (MalformedURLException e) {
        LOG.warn(e);
      }
      catch (IOException e) {
        LOG.warn(e);
      }
    }
  }

  public static void searchArtifacts(final Project project, String coord, final PairProcessor<Collection<ArtifactType>, Boolean> resultProcessor, final Processor<Collection<RepositoryType>> repoProcessor) {
    if (coord == null) return;
    final ArtifactType template = createTemplate(coord);
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Maven", false) {

      public void run(@NotNull ProgressIndicator indicator) {
        final MavenFacadeManager mavenManager = ServiceManager.getService(project, MavenFacadeManager.class);
        final Ref<List<ArtifactType>> result = Ref.create(Collections.<ArtifactType>emptyList());
        final Ref<List<RepositoryType>> result2 = Ref.create(Collections.<RepositoryType>emptyList());
        final Ref<Boolean> tooManyRef = Ref.create(Boolean.FALSE);
        try {
          final MavenFacade mavenFacade = mavenManager.getMavenFacade(project);
          final String[] nexusUrls = getDefaultNexusUrls();
          final List<ArtifactType> resultList = new ArrayList<ArtifactType>();
          final List<RepositoryType> result2List = new ArrayList<RepositoryType>();
          for (String nexusUrl : nexusUrls) {
            final List<ArtifactType> artifacts;
            try {
              artifacts = mavenFacade.findArtifacts(template, nexusUrl);
            }
            catch (Exception ex) {
              LOG.warn("Accessing Nexus at: "+nexusUrl, ex);
              continue;
            }
            if (artifacts == null) {
              tooManyRef.set(Boolean.TRUE);
            }
            else if (!artifacts.isEmpty()) {
              final List<RepositoryType> repositories = mavenFacade.getRepositories(nexusUrl);
              final HashMap<String, RepositoryType> map = new HashMap<String, RepositoryType>();
              for (RepositoryType repository : repositories) {
                map.put(repository.getId(), repository);
              }
              result2List.addAll(repositories);
              for (ArtifactType artifact : artifacts) {
                final RepositoryType repoType = map.get(artifact.getRepoId());
                artifact.setResourceUri(repoType == null? null: repoType.getContentResourceURI());
              }
              resultList.addAll(artifacts);
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

  public static void searchRepositories(final Project project, final Processor<Collection<RepositoryType>> resultProcessor) {
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Maven", false) {

      public void run(@NotNull ProgressIndicator indicator) {
        final MavenFacadeManager mavenManager = ServiceManager.getService(project, MavenFacadeManager.class);
        final Ref<List<RepositoryType>> result = Ref.create(Collections.<RepositoryType>emptyList());
        try {
          final String[] nexusUrls = getDefaultNexusUrls();
          final MavenFacade mavenFacade = mavenManager.getMavenFacade(project);
          final ArrayList<RepositoryType> repoList = new ArrayList<RepositoryType>();
          for (String nexusUrl : nexusUrls) {
            final List<RepositoryType> repositories;
            try {
              repositories = mavenFacade.getRepositories(nexusUrl);
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

  private static String[] getDefaultNexusUrls() {
    return new String[] {
              "http://oss.sonatype.org/service/local/",
              "http://repository.sonatype.org/service/local/",
              //"http://maven.labs.intellij.net:8081/nexus/service/local/"
            };
  }

  public static List<MavenFacade.Repository> getDefaultRepositories() {
    final List<MavenFacade.Repository> list = new ArrayList<MavenFacade.Repository>();
    final Set<String> visited = new HashSet<String>();
    final MavenRepositoriesProvider provider = MavenRepositoriesProvider.getInstance();
    for (String id : provider.getRepositoryIds()) {
      final String url = provider.getRepositoryUrl(id);
      if (!visited.add(url)) continue;
      list.add(new MavenFacade.Repository(id, url, StringUtil.notNullize(provider.getRepositoryLayout(id), "default")));
    }
    return list;
  }

  private static ArtifactType createTemplate(String coord) {
    final ArtifactType template = new ArtifactType();
    final String[] parts = coord.split(":");
    if (parts.length == 1 && parts[0].length() > 0 && Character.isUpperCase(parts[0].charAt(0))) {
      template.setContextId(parts[0]);
    }
    else {
      template.setGroupId(parts.length > 0 ? parts[0] : null);
      template.setArtifactId(parts.length > 1 ? parts[1] : null);
      template.setVersion(parts.length > 2 ? parts[2] : null);
    }
    return template;
  }

  private static void handleError(String message, Exception e) {
    LOG.error(message, e);
  }

  public static void resolveLibrary(final Project project,
                                    Collection<String> libNames,
                                    final Collection<MavenFacade.Repository> repositories, boolean modal,
                                    final Processor<Map<String, List<ArtifactType>>> resultProcessor) {
    if (libNames.isEmpty()) return;
    final ArrayList<ArtifactType> parameters = new ArrayList<ArtifactType>();
    for (String s : libNames) {
      final ArtifactType template = createTemplate(s);
      template.setPackaging("jar");
      parameters.add(template);
    }
    ProgressManager.getInstance().run(modal ? new Task.Modal(project, "Maven", false) {

      public void run(@NotNull ProgressIndicator indicator) {
        doResolveInner(project, parameters, repositories, resultProcessor, indicator);
      }
    } : new Task.Backgroundable(project, "Maven", false, PerformInBackgroundOption.DEAF) {
      
      public void run(@NotNull ProgressIndicator indicator) {
        doResolveInner(project, parameters, repositories, resultProcessor, indicator);
      }

      @Override
      public boolean shouldStartInBackground() {
        return false;
      }
    });
  }

  private static void doResolveInner(Project project,
                                     final ArrayList<ArtifactType> artifacts,
                                     Collection<MavenFacade.Repository> repositories,
                                     final Processor<Map<String, List<ArtifactType>>> resultProcessor,
                                     ProgressIndicator indicator) {
    final Ref<Map<String, List<ArtifactType>>> result = Ref.create(Collections.<String, List<ArtifactType>>emptyMap());
    final MavenFacadeManager mavenManager = ServiceManager.getService(project, MavenFacadeManager.class);
    try {
      final MavenFacade mavenFacade = mavenManager.getMavenFacade(project);
      final MavenFacade.MavenFacadeSettings settings = createMavenFacadeSettings(project);
      settings.getRemoteRepositories().addAll(repositories);
      mavenFacade.setMavenSettings(settings);
      final RemoteTransferListener transferListener = fromProgressIndicator(indicator);
      UnicastRemoteObject.exportObject(transferListener, 0);
      mavenFacade.setTransferListener(transferListener);
      try {
        result.set(mavenFacade.resolveDependencies(artifacts));
      }
      finally {
        UnicastRemoteObject.unexportObject(transferListener, true);
        mavenFacade.setTransferListener(null);
      }
    }
    catch (Exception e) {
      handleError("Error resolving: " + artifacts, e);
    }
    finally {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          resultProcessor.process(result.get());
        }
      });
    }
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

  private void refreshLibraries(final Project project, final Map<Library, Collection<ArtifactType>> map) {
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

  private static MavenFacade.MavenFacadeSettings createMavenFacadeSettings(Project project) {
    final MavenFacade.MavenFacadeSettings settings = new MavenFacade.MavenFacadeSettings();
    final MavenGeneralSettings generalSettings = MavenProjectsManager.getInstance(project).getGeneralSettings();
    settings.setLocalRepository(new MavenFacade.Repository("local", VfsUtil.pathToUrl(generalSettings.getEffectiveLocalRepository().getPath()), "default"));
    return settings;
  }

  public static RemoteTransferListener fromProgressIndicator(final ProgressIndicator indicator) {
    return new RemoteTransferListenerImpl() {

      final Map<Resource, Long> myDownloads = new ConcurrentHashMap<Resource, Long>();
      @Override
      public void transferInitiated(TransferEvent transferEvent) {
        final String message = transferEvent.getRequestType() == TransferEvent.RequestType.PUT ? "Uploading" : "Downloading";
        final String url = transferEvent.getRepositoryUrl();
        indicator.setText(message + ": " + url + "/" + transferEvent.getResource().getResourceName());
      }

      @Override
      public void transferStarted(TransferEvent transferEvent) {
        // nothing
      }

      @Override
      public void transferProgress(TransferEvent transferEvent, int length) {
        final Resource resource = transferEvent.getResource();
        final long curComplete;
        if (!myDownloads.containsKey(resource)) {
          curComplete = length;
        }
        else {
          final long complete = myDownloads.get(resource).longValue();
          curComplete = complete + length;
        }
        myDownloads.put(resource, new Long(curComplete));

        long curTotal = 0;
        long curProgress = 0;
        boolean unknown = false;
        for (Resource res : myDownloads.keySet()) {
          final long total = res.getResourceContentLength();
          final long complete = myDownloads.get(res).longValue();
          if (total == -1) unknown = true;
          else curTotal += total;
          curProgress += complete;
        }

        final StringBuilder sb = new StringBuilder();
        if (curTotal >= 1024) {
          sb.append((curProgress / 1024) + "/" + (curTotal == -1 ? "?" : (curTotal / 1024) + "K"));
        }
        else {
          sb.append(curProgress + "/" + (curTotal == -1 ? "?" : curTotal + "b"));
        }
        if (unknown) {
          indicator.setIndeterminate(true);
        }
        else {
          indicator.setIndeterminate(false);
          indicator.setFraction((double)curProgress / curTotal);
        }
        indicator.setText(sb.toString());
        indicator.setText2(transferEvent.getResource().getResourceName());
      }

      @Override
      public void transferCompleted(TransferEvent transferEvent) {
        transferProgress(transferEvent, 0);
        if (true) return;
        final StringBuilder sb = new StringBuilder();
        final long contentLength = transferEvent.getResource().getResourceContentLength();
        if (contentLength != -1) {
          String type =
            (transferEvent.getRequestType() == TransferEvent.RequestType.PUT ? "uploaded" : "downloaded");
          sb.append(contentLength >= 1024 ? (contentLength / 1024) + "K" : contentLength + "b");
          String name = transferEvent.getResource().getResourceName();
          name = name.substring(name.lastIndexOf('/') + 1, name.length());
          sb.append(" ");
          sb.append(type);
          sb.append("  (");
          sb.append(name);
          sb.append(")");
        }
        indicator.setText(sb.toString());
        //myDownloads.remove(transferEvent.getResource());
      }

      @Override
      public void transferError(TransferEvent transferEvent) {
        indicator.setText2(transferEvent.getException().getMessage());
      }

      @Override
      public void debug(String s) {
        indicator.setText2(s);
      }
    };
  }
}
