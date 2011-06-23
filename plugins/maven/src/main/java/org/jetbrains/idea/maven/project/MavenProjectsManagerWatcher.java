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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.PathUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.Update;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.utils.MavenMergingUpdateQueue;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.util.*;

public class MavenProjectsManagerWatcher {
  public static final Key<Boolean> FORCE_IMPORT_AND_RESOLVE_ON_REFRESH =
    Key.create(MavenProjectsManagerWatcher.class + "FORCE_IMPORT_AND_RESOLVE_ON_REFRESH");

  private static final int DOCUMENT_SAVE_DELAY = 1000;

  private final Project myProject;
  private final MavenProjectsManager myManager;
  private final MavenProjectsTree myProjectsTree;
  private final MavenGeneralSettings myGeneralSettings;
  private final MavenProjectsProcessor myReadingProcessor;
  private final MavenEmbeddersManager myEmbeddersManager;

  private final List<VirtualFilePointer> mySettingsFilesPointers = new ArrayList<VirtualFilePointer>();
  private final List<LocalFileSystem.WatchRequest> myWatchedRoots = new ArrayList<LocalFileSystem.WatchRequest>();

  private final Set<Document> myChangedDocuments = new THashSet<Document>();
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

    DocumentAdapter myDocumentListener = new DocumentAdapter() {
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
          public void run() {
            final Set<Document> copy;

            synchronized (myChangedDocuments) {
              copy = new THashSet<Document>(myChangedDocuments);
              myChangedDocuments.clear();
            }

            MavenUtil.invokeLater(myProject, new Runnable() {
              public void run() {
                new WriteAction() {
                  protected void run(Result result) throws Throwable {
                    for (Document each : copy) {
                      PsiDocumentManager.getInstance(myProject).commitDocument(each);
                      FileDocumentManager.getInstance().saveDocument(each);
                    }
                  }
                }.execute();
              }
            });
          }
        });
      }
    };
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(myDocumentListener, myBusConnection);

    final MavenGeneralSettings.Listener mySettingsPathsChangesListener = new MavenGeneralSettings.Listener() {
      public void changed() {
        updateSettingsFilePointers();
        onSettingsChange();
      }
    };
    myGeneralSettings.addListener(mySettingsPathsChangesListener);
    Disposer.register(myChangedDocumentsQueue, new Disposable() {
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
    Collection<String> pathsToWatch = new ArrayList<String>(settingsFiles.length);

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
        String url = VfsUtil.pathToUrl(path);
        mySettingsFilesPointers.add(
          VirtualFilePointerManager.getInstance().create(url, myChangedDocumentsQueue, new VirtualFilePointerListener() {
            public void beforeValidityChanged(VirtualFilePointer[] pointers) {
            }

            public void validityChanged(VirtualFilePointer[] pointers) {
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

  public synchronized void addManagedFilesWithProfiles(List<VirtualFile> files, List<String> explicitProfiles) {
    myProjectsTree.addManagedFilesWithProfiles(files, explicitProfiles);
    scheduleUpdateAll();
  }

  @TestOnly
  public synchronized void resetManagedFilesAndProfilesInTests(List<VirtualFile> files, List<String> explicitProfiles) {
    myProjectsTree.resetManagedFilesAndProfiles(files, explicitProfiles);
    scheduleUpdateAll();
  }

  public synchronized void removeManagedFiles(List<VirtualFile> files) {
    myProjectsTree.removeManagedFiles(files);
    scheduleUpdateAll();
  }

  public synchronized void setExplicitProfiles(Collection<String> profiles) {
    myProjectsTree.setExplicitProfiles(profiles);
    scheduleUpdateAll();
  }

  private void scheduleUpdateAll() {
    scheduleUpdateAll(false, true);
  }

  public void scheduleUpdateAll(boolean force, final boolean forceImportAndResolve) {
    Runnable onCompletion = new Runnable() {
      @Override
      public void run() {
        if (forceImportAndResolve || myManager.getImportingSettings().isImportAutomatically()) {
          myManager.scheduleImportAndResolve();
        }
      }
    };
    myReadingProcessor.scheduleTask(new MavenProjectsProcessorReadingTask(force, myProjectsTree, myGeneralSettings, onCompletion));
  }

  public void scheduleUpdate(List<VirtualFile> filesToUpdate,
                             List<VirtualFile> filesToDelete,
                             boolean force,
                             final boolean forceImportAndResolve) {
    Runnable onCompletion = new Runnable() {
      @Override
      public void run() {
        if (forceImportAndResolve || myManager.getImportingSettings().isImportAutomatically()) {
          myManager.scheduleImportAndResolve();
        }
      }
    };
    myReadingProcessor.scheduleTask(new MavenProjectsProcessorReadingTask(filesToUpdate,
                                                                          filesToDelete,
                                                                          force,
                                                                          myProjectsTree,
                                                                          myGeneralSettings,
                                                                          onCompletion));
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
    public void beforeRootsChange(ModuleRootEvent event) {
    }

    public void rootsChanged(ModuleRootEvent event) {
      // todo is this logic necessary?
      List<VirtualFile> existingFiles = myProjectsTree.getProjectsFiles();
      List<VirtualFile> newFiles = new ArrayList<VirtualFile>();
      List<VirtualFile> deletedFiles = new ArrayList<VirtualFile>();

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
    String suffix = "/" + MavenConstants.PROFILES_XML;
    if (!path.endsWith(suffix)) return false;
    int pos = path.lastIndexOf(suffix);
    return myProjectsTree.isPotentialProject(path.substring(0, pos) + "/" + MavenConstants.POM_XML);
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
      if (each.getFile() == f) return true;
    }
    return false;
  }

  private class MyFileChangeListener extends MyFileChangeListenerBase {
    private List<VirtualFile> filesToUpdate;
    private List<VirtualFile> filesToRemove;
    private boolean settingsHaveChanged;
    private boolean forceImportAndResolve;

    protected boolean isRelevant(String path) {
      return isPomFile(path) || isProfilesFile(path) || isSettingsFile(path);
    }

    protected void updateFile(VirtualFile file) {
      doUpdateFile(file, false);
    }

    protected void deleteFile(VirtualFile file) {
      doUpdateFile(file, true);
    }

    private void doUpdateFile(VirtualFile file, boolean remove) {
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
        filesToUpdate.add(pom);
        return;
      }

      if (remove) {
        filesToRemove.add(file);
      }
      else {
        filesToUpdate.add(file);
      }
    }

    private VirtualFile getPomFileProfilesFile(VirtualFile f) {
      if (!f.getName().equals(MavenConstants.PROFILES_XML)) return null;
      return f.getParent().findChild(MavenConstants.POM_XML);
    }

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
      // The better way to fix if, of course, is to do simething with
      // subscription - add listener not during postStartupActivity
      // but on project initialization to avoid this situation.
      if (areFileSetsInitialised()) return;

      filesToUpdate = new ArrayList<VirtualFile>();
      filesToRemove = new ArrayList<VirtualFile>();
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

    protected abstract void updateFile(VirtualFile file);

    protected abstract void deleteFile(VirtualFile file);

    protected abstract void apply();

    public void before(List<? extends VFileEvent> events) {
      for (VFileEvent each : events) {
        if (each instanceof VFileDeleteEvent) {
          deleteRecursively(each.getFile());
        }
        else {
          if (!isRelevant(each.getPath())) continue;
          if (each instanceof VFilePropertyChangeEvent) {
            if (((VFilePropertyChangeEvent)each).getPropertyName().equals(VirtualFile.PROP_NAME)) {
              deleteRecursively(each.getFile());
            }
          }
          else if (each instanceof VFileMoveEvent) {
            VFileMoveEvent moveEvent = (VFileMoveEvent)each;
            String newPath = moveEvent.getNewParent().getPath() + "/" + moveEvent.getFile().getName();
            if (!isRelevant(newPath)) {
              deleteRecursively(moveEvent.getFile());
            }
          }
        }
      }
    }

    private void deleteRecursively(VirtualFile f) {
      if (isRelevant(f.getPath())) deleteFile(f);
      if (f.isDirectory()) {
        // prevent reading directories content if not already cached.
        Iterable<VirtualFile> children = f instanceof NewVirtualFile
                                         ? ((NewVirtualFile)f).iterInDbChildren()
                                         : Arrays.asList(f.getChildren());
        for (VirtualFile each : children) {
          deleteRecursively(each);
        }
      }
    }

    public void after(List<? extends VFileEvent> events) {
      for (VFileEvent each : events) {
        if (!isRelevant(each.getPath())) continue;

        if (each instanceof VFileCreateEvent) {
          VFileCreateEvent createEvent = (VFileCreateEvent)each;
          VirtualFile newChild = createEvent.getParent().findChild(createEvent.getChildName());
          if (newChild != null) {
            updateFile(newChild);
          }
        }
        else if (each instanceof VFileCopyEvent) {
          VFileCopyEvent copyEvent = (VFileCopyEvent)each;
          VirtualFile newChild = copyEvent.getNewParent().findChild(copyEvent.getNewChildName());
          if (newChild != null) {
            updateFile(newChild);
          }
        }
        else if (each instanceof VFileContentChangeEvent) {
          updateFile(each.getFile());
        }
        else if (each instanceof VFilePropertyChangeEvent) {
          if (((VFilePropertyChangeEvent)each).getPropertyName().equals(VirtualFile.PROP_NAME)) {
            updateFile(each.getFile());
          }
        }
        else if (each instanceof VFileMoveEvent) {
          updateFile(each.getFile());
        }
      }
      apply();
    }
  }
}
