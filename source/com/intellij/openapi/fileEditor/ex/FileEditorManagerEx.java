package com.intellij.openapi.fileEditor.ex;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;

public abstract class FileEditorManagerEx extends FileEditorManager {
  public static FileEditorManagerEx getInstanceEx(Project project) {
    return (FileEditorManagerEx)getInstance(project);
  }

  /**
   * @return <code>JComponent</code> which represent the place where all editors are located
   */ 
  public abstract JComponent getComponent();

  /**
   * @return preferred focused component inside myEditor tabbed container.
   * This method does similar things like {@link FileEditor#getPreferredFocusedComponent()}
   * but it also tracks (and remember) focus movement inside tabbed container.
   *
   * @see EditorComposite#getPreferredFocusedComponent()
   */
  public abstract JComponent getPreferredFocusedComponent();
  
  public abstract Pair<FileEditor[], FileEditorProvider[]> getEditorsWithProviders(VirtualFile file);

  /**
   * @param editor never null
   * 
   * @return can be null
   */ 
  public abstract VirtualFile getFile(FileEditor editor);

  /**
   *
   * @return current window in splitters
   */
  public abstract EditorWindow getCurrentWindow();

  public abstract void setCurrentWindow(EditorWindow window);

  /**
   * @return <code>true</code> if there are two tab groups, othrwise the
   * method returns <code>false</code>
   */
  public abstract boolean hasSplitters();

  public abstract void unsplitWindow();

  public abstract void unsplitAllWindow();

  public abstract EditorWindow [] getWindows ();

  /**
   * @return arrays of all files (including <code>file</code> itself) that belong
   * to the same tabbed container. The method returns empty array if <code>file</code>
   * is not open. The returned files have the same order as they have in the
   * tabbed container.
   */
  public abstract VirtualFile[] getSiblings(VirtualFile file);

  /**
   * Moves focus cyclically to the next myEditor
   */
  public abstract void moveFocusToNextEditor();

  public abstract void createSplitter(int orientation);

  public abstract void changeSplitterOrientation();

  public abstract void flipTabs();
  public abstract boolean tabsMode();

  public abstract boolean isInSplitter();

  public abstract boolean hasOpenedFile ();

  public abstract VirtualFile getCurrentFile();

  public abstract Pair <FileEditor, FileEditorProvider> getSelectedEditorWithProvider(VirtualFile file);

  public abstract void closeAllFiles();

  public FileEditor[] openFile(final VirtualFile file, final boolean focusEditor) {
    return openFileWithProviders(file, focusEditor).getFirst ();
  }

  public abstract Pair<FileEditor[],FileEditorProvider[]> openFileWithProviders(VirtualFile file, boolean focusEditor);

  public abstract boolean isChanged(EditorComposite editor);

  public abstract EditorWindow getNextWindow(final EditorWindow window);

  public abstract EditorWindow getPrevWindow(final EditorWindow window);
}
