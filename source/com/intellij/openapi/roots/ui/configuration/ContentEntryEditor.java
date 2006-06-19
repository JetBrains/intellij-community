package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.EventDispatcher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.EventListener;

/**
 * @author Eugene Zhuravlev
 * Date: Oct 8, 2003
 * Time: 3:48:13 PM
 */
public final class ContentEntryEditor implements ContentRootPanel.ActionCallback {

  private final ContentEntry myContentEntry;
  private final ModifiableRootModel myRootModel;
  private boolean myIsSelected;
  private ContentRootPanel myContentRootPanel;
  private JPanel myMainPanel;
  private EventDispatcher<ContentEntryEditorListener> myEventDispatcher;

  public static interface ContentEntryEditorListener extends EventListener{
    void editingStarted(ContentEntryEditor editor);
    void beforeEntryDeleted(ContentEntryEditor editor);
    void sourceFolderAdded(ContentEntryEditor editor, SourceFolder folder);
    void sourceFolderRemoved(ContentEntryEditor editor, VirtualFile file, boolean isTestSource);
    void folderExcluded(ContentEntryEditor editor, VirtualFile file);
    void folderIncluded(ContentEntryEditor editor, VirtualFile file);
    void navigationRequested(ContentEntryEditor editor, VirtualFile file);
    void packagePrefixSet(ContentEntryEditor editor, SourceFolder folder);
  }

  public ContentEntryEditor(ContentEntry contentEntry, ModifiableRootModel rootModel) {
    myContentEntry = contentEntry;
    myRootModel = rootModel;
    myMainPanel = new JPanel(new BorderLayout());
    myMainPanel.setOpaque(false);
    myMainPanel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        myEventDispatcher.getMulticaster().editingStarted(ContentEntryEditor.this);
      }
      public void mouseEntered(MouseEvent e) {
        if (!myIsSelected) {
          highlight(true);
        }
      }
      public void mouseExited(MouseEvent e) {
        if (!myIsSelected) {
          highlight(false);
        }
      }
    });
    myEventDispatcher = EventDispatcher.create(ContentEntryEditorListener.class);
    setSelected(false);
    update();
  }


  public void deleteContentEntry() {
    final int answer = Messages.showYesNoDialog(ProjectBundle.message("module.paths.remove.content.prompt",
                                                                      VirtualFileManager.extractPath(myContentEntry.getUrl()).replace('/', File.separatorChar)),
                                                ProjectBundle.message("module.paths.remove.content.title"), Messages.getQuestionIcon());
    if (answer != 0) { // no
      return;
    }
    myEventDispatcher.getMulticaster().beforeEntryDeleted(this);
    myRootModel.removeContentEntry(myContentEntry);
  }

  public void deleteContentFolder(ContentEntry contentEntry, ContentFolder folder) {
    if (folder instanceof SourceFolder) {
      removeSourceFolder((SourceFolder)folder);
      update();
    }
    else if (folder instanceof ExcludeFolder) {
      removeExcludeFolder((ExcludeFolder)folder);
      update();
    }

  }

  public void navigateFolder(ContentEntry contentEntry, ContentFolder contentFolder) {
    final VirtualFile file = contentFolder.getFile();
    if (file != null) { // file can be deleted externally
      myEventDispatcher.getMulticaster().navigationRequested(this, file);
    }
  }

  public void setPackagePrefix(SourceFolder folder, String prefix) {
    folder.setPackagePrefix(prefix);
    update();
    myEventDispatcher.getMulticaster().packagePrefixSet(this, folder);
  }

  void addContentEntryEditorListener(ContentEntryEditorListener listener) {
    myEventDispatcher.addListener(listener);
  }

  void removeContentEntryEditorListener(ContentEntryEditorListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  public void setSelected(boolean isSelected) {
    if (myIsSelected != isSelected) {
      highlight(isSelected);
      myIsSelected = isSelected;
    }
  }

  private void highlight(boolean selected) {
    if (myContentRootPanel != null) {
      myContentRootPanel.setSelected(selected);
    }
  }

  public JComponent getComponent() {
    return myMainPanel;
  }

  public ContentEntry getContentEntry() {
    return myContentEntry;
  }

  public void update() {
    if (myContentRootPanel != null) {
      myMainPanel.remove(myContentRootPanel);
    }
    myContentRootPanel = new ContentRootPanel(myContentEntry, this);
    myContentRootPanel.setSelected(myIsSelected);
    myMainPanel.add(myContentRootPanel, BorderLayout.CENTER);
    myMainPanel.revalidate();
  }


  public SourceFolder addSourceFolder(VirtualFile file, boolean isTestSource) {
    final SourceFolder sourceFolder = myContentEntry.addSourceFolder(file, isTestSource);
    try {
      return sourceFolder;
    }
    finally {
      myEventDispatcher.getMulticaster().sourceFolderAdded(this, sourceFolder);
      update();
    }
  }

  public void removeSourceFolder(SourceFolder sourceFolder) {
    final VirtualFile file = sourceFolder.getFile();
    final boolean isTestSource = sourceFolder.isTestSource();
    try {
      myContentEntry.removeSourceFolder(sourceFolder);
    }
    finally {
      myEventDispatcher.getMulticaster().sourceFolderRemoved(this, file, isTestSource);
      update();
    }
  }

  public ExcludeFolder addExcludeFolder(VirtualFile file) {
    try {
      final boolean isCompilerOutput = isCompilerOutput(file);
      boolean isExplodedDirectory = isExplodedDirectory(file);
      if (isCompilerOutput || isExplodedDirectory) {
        if (isCompilerOutput) {
          myRootModel.setExcludeOutput(true);
        }
        if (isExplodedDirectory) {
          myRootModel.setExcludeExplodedDirectory(true);
        }
        return null;
      }
      else {
        final ExcludeFolder excludeFolder = myContentEntry.addExcludeFolder(file);
        return excludeFolder;
      }
    }
    finally {
      myEventDispatcher.getMulticaster().folderExcluded(this, file);
      update();
    }
  }

  public void removeExcludeFolder(ExcludeFolder excludeFolder) {
    final VirtualFile file = excludeFolder.getFile();
    try {
      if (isCompilerOutput(file)) {
        myRootModel.setExcludeOutput(false);
      }
      if (isExplodedDirectory(file)) {
        myRootModel.setExcludeExplodedDirectory(false);
      }
      if (!excludeFolder.isSynthetic()) {
        myContentEntry.removeExcludeFolder(excludeFolder);
      }
    }
    finally {
      myEventDispatcher.getMulticaster().folderIncluded(this, file);
      update();
    }
  }

  public boolean isCompilerOutput(VirtualFile file) {
    final VirtualFile compilerOutputPath = myRootModel.getCompilerOutputPath();
    if (compilerOutputPath != null) {
      if (compilerOutputPath.equals(file)) {
        return true;
      }
    }

    final VirtualFile compilerOutputPathForTests = myRootModel.getCompilerOutputPathForTests();
    if (compilerOutputPathForTests != null) {
      if (compilerOutputPathForTests.equals(file)) {
        return true;
      }
    }

    if (myRootModel.isCompilerOutputPathInherited()){
      final VirtualFile compilerOutput = ProjectRootManager.getInstance(myRootModel.getModule().getProject()).getCompilerOutput();
      if (Comparing.equal(compilerOutput, file)){
        return true;
      }
    }

    return false;
  }

  public boolean isExplodedDirectory(VirtualFile file) {
    final VirtualFile explodedDir = myRootModel.getExplodedDirectory();
    if (explodedDir != null) {
      if (explodedDir.equals(file)) {
        return true;
      }
    }
    return false;
  }

  public boolean isSource(VirtualFile file) {
    final SourceFolder sourceFolder = getSourceFolder(file);
    return sourceFolder != null && !sourceFolder.isTestSource();
  }

  public boolean isTestSource(VirtualFile file) {
    final SourceFolder sourceFolder = getSourceFolder(file);
    return sourceFolder != null && sourceFolder.isTestSource();
  }

  public boolean isExcluded(VirtualFile file) {
    return getExcludeFolder(file) != null;
  }

  public boolean isUnderExcludedDirectory(final VirtualFile file) {
    if (myContentEntry == null) {
      return false;
    }
    final ExcludeFolder[] excludeFolders = myContentEntry.getExcludeFolders();
    for (ExcludeFolder excludeFolder : excludeFolders) {
      final VirtualFile excludedDir = excludeFolder.getFile();
      if (excludedDir == null) {
        continue;
      }
      if (VfsUtil.isAncestor(excludedDir, file, true)) {
        return true;
      }
    }
    return false;
  }

  public ExcludeFolder getExcludeFolder(VirtualFile file) {
    if (myContentEntry == null) {
      return null;
    }
    final ExcludeFolder[] excludeFolders = myContentEntry.getExcludeFolders();
    for (final ExcludeFolder excludeFolder : excludeFolders) {
      final VirtualFile f = excludeFolder.getFile();
      if (f == null) {
        continue;
      }
      if (f.equals(file)) {
        return excludeFolder;
      }
    }
    return null;
  }

  public SourceFolder getSourceFolder(VirtualFile file) {
    if (myContentEntry == null) {
      return null;
    }
    final SourceFolder[] sourceFolders = myContentEntry.getSourceFolders();
    for (SourceFolder sourceFolder : sourceFolders) {
      final VirtualFile f = sourceFolder.getFile();
      if (f == null) {
        continue;
      }
      if (f.equals(file)) {
        return sourceFolder;
      }
    }
    return null;
  }
}
