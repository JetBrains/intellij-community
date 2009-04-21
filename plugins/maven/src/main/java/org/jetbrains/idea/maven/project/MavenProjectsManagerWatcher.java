package org.jetbrains.idea.maven.project;

import com.intellij.ProjectTopics;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.PathUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.utils.MavenConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MavenProjectsManagerWatcher {
  private Project myProject;
  private MavenEmbeddersManager myEmbeddersManager;
  private MavenProjectsTree myTree;
  private MavenGeneralSettings myGeneralSettings;
  private MavenProjectsProcessor myReadingProcessor;
  private MavenGeneralSettings.Listener mySettingsPathsChangesListener;
  private MessageBusConnection myBusConnection;
  private List<VirtualFilePointer> mySettingsFilesPointers = new ArrayList<VirtualFilePointer>();
  private boolean isRunning;

  public MavenProjectsManagerWatcher(Project project,
                                     MavenEmbeddersManager embeddersManager,
                                     MavenProjectsTree tree,
                                     MavenGeneralSettings generalSettings,
                                     MavenProjectsProcessor readingProcessor) {
    myProject = project;
    myEmbeddersManager = embeddersManager;
    myTree = tree;
    myGeneralSettings = generalSettings;
    myReadingProcessor = readingProcessor;
  }

  public void start() {
    myBusConnection = myProject.getMessageBus().connect();

    myBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, new MyFileChangeListener());
    myBusConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyRootChangesListener());

    mySettingsPathsChangesListener = new MavenGeneralSettings.Listener() {
      public void pathChanged() {
        updateSettingsFilePointers();
        onSettingsChange();
      }
    };
    myGeneralSettings.addListener(mySettingsPathsChangesListener);
    updateSettingsFilePointers();

    isRunning = true;
  }

  private void updateSettingsFilePointers() {
    mySettingsFilesPointers.clear();
    addFilePointer(MavenEmbedderFactory.resolveGlobalSettingsFile(myGeneralSettings.getMavenHome()));
    addFilePointer(MavenEmbedderFactory.resolveUserSettingsFile(myGeneralSettings.getMavenSettingsFile()));
  }

  private void addFilePointer(File settingsFile) {
    if (settingsFile == null) return;
    String url = VfsUtil.pathToUrl(FileUtil.toSystemIndependentName((PathUtil.getCanonicalPath(settingsFile.getAbsolutePath()))));
    mySettingsFilesPointers.add(VirtualFilePointerManager.getInstance().create(url, myProject, new VirtualFilePointerListener() {
      public void beforeValidityChanged(VirtualFilePointer[] pointers) {
      }

      public void validityChanged(VirtualFilePointer[] pointers) {
      }
    }));
  }

  public void stop() {
    isRunning = false;

    mySettingsFilesPointers.clear();
    myGeneralSettings.removeListener(mySettingsPathsChangesListener);
    myBusConnection.disconnect();
  }

  public void setManagedFiles(List<VirtualFile> files) {
    myTree.setManagedFiles(files);
    scheduleUpdateAll();
  }

  public void addManagedFile(VirtualFile file) {
    myTree.addManagedFile(file);
    scheduleUpdate(Collections.singletonList(file), Collections.<VirtualFile>emptyList());
  }

  public void removeManagedFiles(List<VirtualFile> files) {
    List<VirtualFile> removedFiles = myTree.removeManagedFiles(files);
    scheduleUpdate(Collections.<VirtualFile>emptyList(), removedFiles);
  }

  public void setActiveProfiles(List<String> profiles) {
    myTree.setActiveProfiles(profiles);
    scheduleUpdateAll();
  }

  private void scheduleUpdateAll() {
    if (!isRunning) return;
    myReadingProcessor.scheduleTask(new MavenProjectsProcessorQuickReadingTask(myProject, myTree, myGeneralSettings));
  }

  private void scheduleUpdate(List<VirtualFile> filesToUpdate, List<VirtualFile> filesToDelete) {
    if (!isRunning) return;
    myReadingProcessor.scheduleTask(new MavenProjectsProcessorQuickReadingTask(myProject,
                                                                               myTree,
                                                                               myGeneralSettings,
                                                                               filesToUpdate,
                                                                               filesToDelete));
  }

  private void onSettingsChange() {
    myEmbeddersManager.reset();
    scheduleUpdateAll();
  }

  private class MyRootChangesListener implements ModuleRootListener {
    public void beforeRootsChange(ModuleRootEvent event) {
    }

    public void rootsChanged(ModuleRootEvent event) {
      // todo is this logic necessary?
      List<VirtualFile> existingFiles = myTree.getFiles();
      List<VirtualFile> newFiles = new ArrayList<VirtualFile>();
      List<VirtualFile> deletedFiles = new ArrayList<VirtualFile>();

      for (VirtualFile f : myTree.getExistingManagedFiles()) {
        if (!existingFiles.contains(f)) {
          newFiles.add(f);
        }
      }

      for (VirtualFile f : existingFiles) {
        if (!f.isValid()) deletedFiles.add(f);
      }

      scheduleUpdate(newFiles, deletedFiles);
    }
  }

  private class MyFileChangeListener extends MyFileChangeListenerBase {
    private List<VirtualFile> filesToUpdate;
    private List<VirtualFile> filesToRemove;
    private boolean settingsHaveChanged;

    protected boolean isRelevant(String path) {
      return isPomFile(path) || isProfilesFile(path) || isSettingsFile(path);
    }

    private boolean isPomFile(String path) {
      if (!path.endsWith("/" + MavenConstants.POM_XML)) return false;
      return myTree.isPotentialProject(path);
    }

    private boolean isProfilesFile(String path) {
      String suffix = "/" + MavenConstants.PROFILES_XML;
      if (!path.endsWith(suffix)) return false;
      int pos = path.lastIndexOf(suffix);
      return myTree.isPotentialProject(path.substring(0, pos) + "/" + MavenConstants.POM_XML);
    }

    private boolean isSettingsFile(String path) {
      for (VirtualFilePointer each : mySettingsFilesPointers) {
        VirtualFile f = each.getFile();
        if (f != null && FileUtil.pathsEqual(path, f.getPath())) return true;
      }
      return false;
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

    private boolean isSettingsFile(VirtualFile f) {
      for (VirtualFilePointer each : mySettingsFilesPointers) {
        if (each.getFile() == f) return true;
      }
      return false;
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
          onSettingsChange();
        }
        else {
          filesToUpdate.removeAll(filesToRemove);
          scheduleUpdate(filesToUpdate, filesToRemove);
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
          deleteRecursively(((VFileDeleteEvent)each).getFile());
        }
        else {
          if (!isRelevant(each.getPath())) continue;
          if (each instanceof VFilePropertyChangeEvent) {
            if (((VFilePropertyChangeEvent)each).getPropertyName().equals(VirtualFile.PROP_NAME)) {
              deleteRecursively(((VFilePropertyChangeEvent)each).getFile());
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
        for (VirtualFile each : f.getChildren()) {
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
          updateFile(((VFileContentChangeEvent)each).getFile());
        }
        else if (each instanceof VFilePropertyChangeEvent) {
          if (((VFilePropertyChangeEvent)each).getPropertyName().equals(VirtualFile.PROP_NAME)) {
            updateFile(((VFilePropertyChangeEvent)each).getFile());
          }
        }
        else if (each instanceof VFileMoveEvent) {
          updateFile(((VFileMoveEvent)each).getFile());
        }
      }
      apply();
    }
  }
}
