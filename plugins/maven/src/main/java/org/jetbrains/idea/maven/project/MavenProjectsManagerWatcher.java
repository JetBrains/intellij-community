// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.externalSystem.service.project.autoimport.FileChangeListenerBase;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.Update;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.utils.MavenMergingUpdateQueue;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

public class MavenProjectsManagerWatcher {

  private static final Key<ConcurrentMap<Project, Long>> CRC_WITHOUT_SPACES = Key.create("MavenProjectsManagerWatcher.CRC_WITHOUT_SPACES");

  public static final Key<Boolean> FORCE_IMPORT_AND_RESOLVE_ON_REFRESH =
    Key.create(MavenProjectsManagerWatcher.class + "FORCE_IMPORT_AND_RESOLVE_ON_REFRESH");

  private static final int DOCUMENT_SAVE_DELAY = 1000;

  private final Project myProject;
  private final MavenProjectsManager myManager;
  private final MavenProjectsTree myProjectsTree;
  private final MavenGeneralSettings myGeneralSettings;
  private final MavenProjectsProcessor myReadingProcessor;
  private final MavenEmbeddersManager myEmbeddersManager;

  private final List<VirtualFilePointer> mySettingsFilesPointers = new ArrayList<>();
  private final List<LocalFileSystem.WatchRequest> myWatchedRoots = new ArrayList<>();

  private final Set<Document> myChangedDocuments = new THashSet<>();
  private final MavenMergingUpdateQueue myChangedDocumentsQueue;

  public MavenProjectsManagerWatcher(Project project,
                                     MavenProjectsManager manager,
                                     MavenProjectsTree projectsTree,
                                     MavenGeneralSettings generalSettings,
                                     MavenProjectsProcessor readingProcessor,
                                     MavenEmbeddersManager embeddersManager) {
    myProject = project;
    myManager = manager;
    myProjectsTree = projectsTree;
    myGeneralSettings = generalSettings;
    myReadingProcessor = readingProcessor;
    myEmbeddersManager = embeddersManager;


    myChangedDocumentsQueue = new MavenMergingUpdateQueue(getClass() + ": Document changes queue",
                                                          DOCUMENT_SAVE_DELAY,
                                                          false,
                                                          myProject);
  }

  public synchronized void start() {
    final MessageBusConnection myBusConnection = myProject.getMessageBus().connect(myChangedDocumentsQueue);
    myBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, new MyFileChangeListener());
    myBusConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyRootChangesListener());

    myChangedDocumentsQueue.makeUserAware(myProject);
    myChangedDocumentsQueue.activate();

    myBusConnection.subscribe(ProjectTopics.MODULES, new ModuleListener() {
      @Override
      public void moduleRemoved(@NotNull Project project, @NotNull Module module) {
        MavenProject mavenProject = myManager.findProject(module);
        if (mavenProject != null && !myManager.isIgnored(mavenProject)) {
          VirtualFile file = mavenProject.getFile();

          if (myManager.isManagedFile(file) && myManager.getModules(mavenProject).isEmpty()) {
            myManager.removeManagedFiles(Collections.singletonList(file));
          }
          else {
            myManager.setIgnoredState(Collections.singletonList(mavenProject), true);
          }
        }
      }

      @Override
      public void moduleAdded(@NotNull final Project project, @NotNull final Module module) {
        // this method is needed to return non-ignored status for modules that were deleted (and thus ignored) and then created again with a different module type
        if (myManager.isMavenizedModule(module)) {
          MavenProject mavenProject = myManager.findProject(module);
          if (mavenProject != null) myManager.setIgnoredState(Collections.singletonList(mavenProject), false);
        }

      }
    });

    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(DocumentEvent event) {
        Document doc = event.getDocument();
        VirtualFile file = FileDocumentManager.getInstance().getFile(doc);

        if (file == null) return;
        String fileName = file.getName();
        boolean isMavenFile = fileName.equals(MavenConstants.POM_XML) || fileName.equals(MavenConstants.PROFILES_XML) ||
                              isSettingsFile(file) || fileName.startsWith("pom.") || isPomFile(file.getPath()) ||
                              isMavenOrJvmConfigFile(file.getPath());
        if (!isMavenFile) return;

        synchronized (myChangedDocuments) {
          myChangedDocuments.add(doc);
        }
        myChangedDocumentsQueue.queue(new Update(MavenProjectsManagerWatcher.this) {
          @Override
          public void run() {
            final Document[] copy;

            synchronized (myChangedDocuments) {
              copy = myChangedDocuments.toArray(Document.EMPTY_ARRAY);
              myChangedDocuments.clear();
            }

            MavenUtil.invokeLater(myProject, () -> WriteAction.run(() -> {
              for (Document each : copy) {
                PsiDocumentManager.getInstance(myProject).commitDocument(each);
                ((FileDocumentManagerImpl)FileDocumentManager.getInstance()).saveDocument(each, false);
              }
            }));
          }
        });
      }
    }, myBusConnection);

    final MavenGeneralSettings.Listener mySettingsPathsChangesListener = new MavenGeneralSettings.Listener() {
      @Override
      public void changed() {
        updateSettingsFilePointers();
        onSettingsChange();
      }
    };
    myGeneralSettings.addListener(mySettingsPathsChangesListener);
    Disposer.register(myChangedDocumentsQueue, new Disposable() {
      @Override
      public void dispose() {
        myGeneralSettings.removeListener(mySettingsPathsChangesListener);
        mySettingsFilesPointers.clear();
      }
    });
    updateSettingsFilePointers();
  }

  private void updateSettingsFilePointers() {
    LocalFileSystem.getInstance().removeWatchedRoots(myWatchedRoots);
    mySettingsFilesPointers.clear();
    addFilePointer(myGeneralSettings.getEffectiveUserSettingsIoFile(),
                   myGeneralSettings.getEffectiveGlobalSettingsIoFile());
  }

  private void addFilePointer(File... settingsFiles) {
    Collection<String> pathsToWatch = new ArrayList<>(settingsFiles.length);

    for (File settingsFile : settingsFiles) {
      if (settingsFile == null) continue;

      File parentFile = settingsFile.getParentFile();
      if (parentFile != null) {
        String path = getNormalizedPath(parentFile);
        if (path != null) {
          pathsToWatch.add(path);
        }
      }

      String path = getNormalizedPath(settingsFile);
      if (path != null) {
        String url = VfsUtilCore.pathToUrl(path);
        mySettingsFilesPointers.add(
          VirtualFilePointerManager.getInstance().create(url, myChangedDocumentsQueue, new VirtualFilePointerListener() {
          }));
      }
    }

    myWatchedRoots.addAll(LocalFileSystem.getInstance().addRootsToWatch(pathsToWatch, false));
  }

  @Nullable
  private static String getNormalizedPath(@NotNull File settingsFile) {
    String canonized = PathUtil.getCanonicalPath(settingsFile.getAbsolutePath());
    return canonized == null ? null : FileUtil.toSystemIndependentName(canonized);
  }

  public synchronized void stop() {
    Disposer.dispose(myChangedDocumentsQueue);
  }

  public synchronized void addManagedFilesWithProfiles(List<VirtualFile> files, MavenExplicitProfiles explicitProfiles) {
    myProjectsTree.addManagedFilesWithProfiles(files, explicitProfiles);
    scheduleUpdateAll(false, true);
  }

  @TestOnly
  public synchronized void resetManagedFilesAndProfilesInTests(List<VirtualFile> files, MavenExplicitProfiles explicitProfiles) {
    myProjectsTree.resetManagedFilesAndProfiles(files, explicitProfiles);
    scheduleUpdateAll(false, true);
  }

  public synchronized void removeManagedFiles(List<VirtualFile> files) {
    myProjectsTree.removeManagedFiles(files);
    scheduleUpdateAll(false, true);
  }

  public synchronized void setExplicitProfiles(MavenExplicitProfiles profiles) {
    myProjectsTree.setExplicitProfiles(profiles);
    scheduleUpdateAll(false, false);
  }

  /**
   * Returned {@link Promise} instance isn't guarantied to be marked as rejected in all cases where importing wasn't performed (e.g.
   * if project is closed)
   */
  public Promise<Void> scheduleUpdateAll(boolean force, final boolean forceImportAndResolve) {
    final AsyncPromise<Void> promise = new AsyncPromise<>();
    Runnable onCompletion = createScheduleImportAction(forceImportAndResolve, promise);
    myReadingProcessor.scheduleTask(new MavenProjectsProcessorReadingTask(force, myProjectsTree, myGeneralSettings, onCompletion));
    return promise;
  }

  public Promise<Void> scheduleUpdate(List<VirtualFile> filesToUpdate,
                                      List<VirtualFile> filesToDelete,
                                      boolean force,
                                      final boolean forceImportAndResolve) {
    final AsyncPromise<Void> promise = new AsyncPromise<>();
    Runnable onCompletion = createScheduleImportAction(forceImportAndResolve, promise);
    myReadingProcessor.scheduleTask(new MavenProjectsProcessorReadingTask(filesToUpdate,
                                                                          filesToDelete,
                                                                          force,
                                                                          myProjectsTree,
                                                                          myGeneralSettings,
                                                                          onCompletion));
    return promise;
  }

  @NotNull
  private Runnable createScheduleImportAction(final boolean forceImportAndResolve, final AsyncPromise<Void> promise) {
    return () -> {
      if (myProject.isDisposed()) {
        promise.setError("Project disposed");
        return;
      }

      if (forceImportAndResolve || myManager.getImportingSettings().isImportAutomatically()) {
        myManager.scheduleImportAndResolve().onSuccess(modules -> promise.setResult(null));
      }
      else {
        promise.setResult(null);
      }
    };
  }

  private void onSettingsChange() {
    myEmbeddersManager.reset();
    scheduleUpdateAll(true, false);
  }

  private void onSettingsXmlChange() {
    myGeneralSettings.changed();
    // onSettingsChange() will be called indirectly by pathsChanged listener on GeneralSettings object
  }

  private class MyRootChangesListener implements ModuleRootListener {
    @Override
    public void rootsChanged(ModuleRootEvent event) {
      // todo is this logic necessary?
      List<VirtualFile> existingFiles = myProjectsTree.getProjectsFiles();
      List<VirtualFile> newFiles = new ArrayList<>();
      List<VirtualFile> deletedFiles = new ArrayList<>();

      for (VirtualFile f : myProjectsTree.getExistingManagedFiles()) {
        if (!existingFiles.contains(f)) {
          newFiles.add(f);
        }
      }

      for (VirtualFile f : existingFiles) {
        if (!f.isValid()) deletedFiles.add(f);
      }

      scheduleUpdate(newFiles, deletedFiles, false, false);
    }
  }

  private boolean isPomFile(String path) {
    return MavenUtil.isPotentialPomFile(path) && myProjectsTree.isPotentialProject(path);
  }

  private boolean isProfilesFile(String path) {
    if (!path.endsWith("/" + MavenConstants.PROFILES_XML)) return false;
    return myProjectsTree.isPotentialProject(path.substring(0, path.length() - MavenConstants.PROFILES_XML.length()) + MavenConstants.POM_XML);
  }

  private boolean isSettingsFile(String path) {
    for (VirtualFilePointer each : mySettingsFilesPointers) {
      VirtualFile f = each.getFile();
      if (f != null && FileUtil.pathsEqual(path, f.getPath())) return true;
    }
    return false;
  }

  private boolean isSettingsFile(VirtualFile f) {
    for (VirtualFilePointer each : mySettingsFilesPointers) {
      if (Comparing.equal(each.getFile(), f)) return true;
    }
    return false;
  }

  private static boolean isMavenOrJvmConfigFile(String path) {
    return path.endsWith(MavenConstants.JVM_CONFIG_RELATIVE_PATH) || path.endsWith(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH);
  }

  private class MyFileChangeListener extends FileChangeListenerBase {
    private List<VirtualFile> filesToUpdate;
    private List<VirtualFile> filesToRemove;
    private boolean settingsHaveChanged;
    private boolean forceImportAndResolve;

    @Override
    protected boolean isRelevant(String path) {
      return isPomFile(path) || isProfilesFile(path) || isSettingsFile(path) || isMavenOrJvmConfigFile(path);
    }

    @Override
    protected void updateFile(VirtualFile file, VFileEvent event) {
      doUpdateFile(file, event, false);
    }

    @Override
    protected void deleteFile(VirtualFile file, VFileEvent event) {
      doUpdateFile(file, event, true);
    }

    private void doUpdateFile(VirtualFile file, VFileEvent event, boolean remove) {
      initLists();

      if (isSettingsFile(file)) {
        settingsHaveChanged = true;
        return;
      }

      if (file.getUserData(FORCE_IMPORT_AND_RESOLVE_ON_REFRESH) == Boolean.TRUE) {
        forceImportAndResolve = true;
      }

      VirtualFile pom = getPomFileProfilesFile(file);
      if (pom != null) {
        if (remove || fileWasChanged(pom, event)) {
          filesToUpdate.add(pom);
        }
        return;
      }

      if (isMavenOrJvmConfigFile(file.getPath()) && (remove || fileWasChanged(file, event))) {
        VirtualFile baseDir = file.getParent().getParent();
        MavenUtil.streamPomFiles(myProject, baseDir).forEach(filesToUpdate::add);
        return;
      }

      if (remove) {
        filesToRemove.add(file);
      }
      else {
        if (fileWasChanged(file, event)) {
          filesToUpdate.add(file);
        }
      }
    }

    private boolean fileWasChanged(VirtualFile file, VFileEvent event) {
      if (!file.isValid() || !(event instanceof VFileContentChangeEvent)) return true;

      ConcurrentMap<Project, Long> map = file.getUserData(CRC_WITHOUT_SPACES);
      if (map == null) {
        ConcurrentMap<Project, Long> value = ContainerUtil.createConcurrentWeakMap();
        map = file.putUserDataIfAbsent(CRC_WITHOUT_SPACES, value);
      }

      Long crc = map.get(myProject);
      Long newCrc;

      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
      if(psiFile instanceof XmlFile) {
        try {
          newCrc = Long.valueOf(MavenUtil.crcWithoutSpaces(file));
        }
        catch (IOException ignored) {
          return true;
        }
      } else {
        newCrc = file.getModificationStamp();
      }

      if (newCrc == -1 // file is invalid
          || newCrc.equals(crc)) {
        return false;
      }
      else {
        map.put(myProject, newCrc);
        return true;
      }
    }

    @Nullable
    private VirtualFile getPomFileProfilesFile(VirtualFile f) {
      if (!f.getName().equals(MavenConstants.PROFILES_XML)) return null;
      return f.getParent().findChild(MavenConstants.POM_XML);
    }

    @Override
    protected void apply() {
      // the save may occur during project close. in this case the background task
      // can not be started since the window has already been closed.
      if (areFileSetsInitialised()) {
        if (settingsHaveChanged) {
          onSettingsXmlChange();
        }
        else {
          filesToUpdate.removeAll(filesToRemove);
          scheduleUpdate(filesToUpdate, filesToRemove, false, forceImportAndResolve);
        }
      }

      clearLists();
    }

    private boolean areFileSetsInitialised() {
      return filesToUpdate != null;
    }

    private void initLists() {
      // Do not use before() method to initialize the lists
      // since the listener can be attached during the update
      // and before method can be skipped.
      // The better way to fix if, of course, is to do something with
      // subscription - add listener not during postStartupActivity
      // but on project initialization to avoid this situation.
      if (areFileSetsInitialised()) return;

      filesToUpdate = new ArrayList<>();
      filesToRemove = new ArrayList<>();
      settingsHaveChanged = false;
      forceImportAndResolve = false;
    }

    private void clearLists() {
      filesToUpdate = null;
      filesToRemove = null;
    }
  }
}
