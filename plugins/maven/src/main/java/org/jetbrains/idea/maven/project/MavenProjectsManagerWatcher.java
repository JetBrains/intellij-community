/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.psi.PsiDocumentManager;
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

  private static final Key<ConcurrentMap<Project, Integer>> CRC_WITHOUT_SPACES = Key.create("MavenProjectsManagerWatcher.CRC_WITHOUT_SPACES");

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

    myBusConnection.subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
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

    DocumentAdapter myDocumentListener = new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent event) {
        Document doc = event.getDocument();
        VirtualFile file = FileDocumentManager.getInstance().getFile(doc);

        if (file == null) return;
        boolean isMavenFile =
          file.getName().equals(MavenConstants.POM_XML) || file.getName().equals(MavenConstants.PROFILES_XML) || isSettingsFile(file);
        if (!isMavenFile) return;

        synchronized (myChangedDocuments) {
          myChangedDocuments.add(doc);
        }
        myChangedDocumentsQueue.queue(new Update(MavenProjectsManagerWatcher.this) {
          @Override
          public void run() {
            final Document[] copy;

            synchronized (myChangedDocuments) {
              copy = myChangedDocuments.toArray(new Document[myChangedDocuments.size()]);
              myChangedDocuments.clear();
            }

            MavenUtil.invokeLater(myProject, () -> new WriteAction() {
              @Override
              protected void run(@NotNull Result result) throws Throwable {
                for (Document each : copy) {
                  PsiDocumentManager.getInstance(myProject).commitDocument(each);
                  ((FileDocumentManagerImpl)FileDocumentManager.getInstance()).saveDocument(each, false);
                }
              }
            }.execute());
          }
        });
      }
    };
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(myDocumentListener, myBusConnection);

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
            @Override
            public void beforeValidityChanged(@NotNull VirtualFilePointer[] pointers) {
            }

            @Override
            public void validityChanged(@NotNull VirtualFilePointer[] pointers) {
            }
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
        myManager.scheduleImportAndResolve().done(modules -> promise.setResult(null));
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

  private class MyRootChangesListener extends ModuleRootAdapter {
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
    if (!path.endsWith("/" + MavenConstants.POM_XML)) return false;
    return myProjectsTree.isPotentialProject(path);
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

  private class MyFileChangeListener extends MyFileChangeListenerBase {
    private List<VirtualFile> filesToUpdate;
    private List<VirtualFile> filesToRemove;
    private boolean settingsHaveChanged;
    private boolean forceImportAndResolve;

    @Override
    protected boolean isRelevant(String path) {
      return isPomFile(path) || isProfilesFile(path) || isSettingsFile(path);
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
        if (remove || xmlFileWasChanged(pom, event)) {
          filesToUpdate.add(pom);
        }
        return;
      }

      if (remove) {
        filesToRemove.add(file);
      }
      else {
        if (xmlFileWasChanged(file, event)) {
          filesToUpdate.add(file);
        }
      }
    }

    private boolean xmlFileWasChanged(VirtualFile xmlFile, VFileEvent event) {
      if (!xmlFile.isValid() || !(event instanceof VFileContentChangeEvent)) return true;

      ConcurrentMap<Project, Integer> map = xmlFile.getUserData(CRC_WITHOUT_SPACES);
      if (map == null) {
        ConcurrentMap<Project, Integer> value = ContainerUtil.createConcurrentWeakMap();
        map = xmlFile.putUserDataIfAbsent(CRC_WITHOUT_SPACES, value);
      }

      Integer crc = map.get(myProject);
      Integer newCrc;

      try {
        newCrc = MavenUtil.crcWithoutSpaces(xmlFile);
      }
      catch (IOException ignored) {
        return true;
      }

      if (newCrc == -1 // XML is invalid
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

  private static abstract class MyFileChangeListenerBase implements BulkFileListener {
    protected abstract boolean isRelevant(String path);

    protected abstract void updateFile(VirtualFile file, VFileEvent event);

    protected abstract void deleteFile(VirtualFile file, VFileEvent event);

    protected abstract void apply();

    @Override
    public void before(@NotNull List<? extends VFileEvent> events) {
      for (VFileEvent each : events) {
        if (each instanceof VFileDeleteEvent) {
          deleteRecursively(each.getFile(), each);
        }
        else {
          if (!isRelevant(each.getPath())) continue;
          if (each instanceof VFilePropertyChangeEvent) {
            if (isRenamed(each)) {
              deleteRecursively(each.getFile(), each);
            }
          }
          else if (each instanceof VFileMoveEvent) {
            VFileMoveEvent moveEvent = (VFileMoveEvent)each;
            String newPath = moveEvent.getNewParent().getPath() + "/" + moveEvent.getFile().getName();
            if (!isRelevant(newPath)) {
              deleteRecursively(moveEvent.getFile(), each);
            }
          }
        }
      }
    }

    private void deleteRecursively(VirtualFile f, final VFileEvent event) {
      VfsUtilCore.visitChildrenRecursively(f, new VirtualFileVisitor() {
        @Override
        public boolean visitFile(@NotNull VirtualFile f) {
          if (isRelevant(f.getPath())) deleteFile(f, event);
          return true;
        }

        @Nullable
        @Override
        public Iterable<VirtualFile> getChildrenIterable(@NotNull VirtualFile f) {
          return f.isDirectory() && f instanceof NewVirtualFile ? ((NewVirtualFile)f).iterInDbChildren() : null;
        }
      });
    }

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
      for (VFileEvent each : events) {
        if (!isRelevant(each.getPath())) continue;

        if (each instanceof VFileCreateEvent) {
          VFileCreateEvent createEvent = (VFileCreateEvent)each;
          VirtualFile newChild = createEvent.getParent().findChild(createEvent.getChildName());
          if (newChild != null) {
            updateFile(newChild, each);
          }
        }
        else if (each instanceof VFileCopyEvent) {
          VFileCopyEvent copyEvent = (VFileCopyEvent)each;
          VirtualFile newChild = copyEvent.getNewParent().findChild(copyEvent.getNewChildName());
          if (newChild != null) {
            updateFile(newChild, each);
          }
        }
        else if (each instanceof VFileContentChangeEvent) {
          updateFile(each.getFile(), each);
        }
        else if (each instanceof VFilePropertyChangeEvent) {
          if (isRenamed(each)) {
            updateFile(each.getFile(), each);
          }
        }
        else if (each instanceof VFileMoveEvent) {
          updateFile(each.getFile(), each);
        }
      }
      apply();
    }

    private static boolean isRenamed(VFileEvent each) {
      return ((VFilePropertyChangeEvent)each).getPropertyName().equals(VirtualFile.PROP_NAME)
             && !Comparing.equal(((VFilePropertyChangeEvent)each).getOldValue(), ((VFilePropertyChangeEvent)each).getNewValue());
    }
  }
}
