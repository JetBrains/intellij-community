/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.palette.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.palette.PaletteDragEventListener;
import com.intellij.ide.palette.PaletteItem;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author yole
 */
public class PaletteManager implements ProjectComponent {
  private Project myProject;
  private FileEditorManager myFileEditorManager;
  private PaletteWindow myPaletteWindow;
  private ToolWindow myPaletteToolWindow;
  private PaletteManager.MyFileEditorManagerListener myListener;
  private List<KeyListener> myKeyListeners = new CopyOnWriteArrayList<KeyListener>();
  private List<PaletteDragEventListener> myDragEventListeners = new CopyOnWriteArrayList<PaletteDragEventListener>();
  private List<ListSelectionListener> mySelectionListeners = new CopyOnWriteArrayList<ListSelectionListener>();

  public PaletteManager(Project project, FileEditorManager fileEditorManager) {
    myProject = project;
    myFileEditorManager = fileEditorManager;
    myListener = new MyFileEditorManagerListener();
    myFileEditorManager.addFileEditorManagerListener(myListener);
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        myPaletteWindow = new PaletteWindow(myProject);
        myPaletteToolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow(IdeBundle.message("toolwindow.palette"),
                                                                                          myPaletteWindow,
                                                                                          ToolWindowAnchor.RIGHT);
        myPaletteToolWindow.setIcon(IconLoader.getIcon("/general/toolWindowPalette.png"));
        myPaletteToolWindow.setAvailable(false, null);
      }
    });
  }

  public void projectClosed() {
    if (myPaletteWindow != null) {
      ToolWindowManager.getInstance(myProject).unregisterToolWindow(IdeBundle.message("toolwindow.palette"));
      myPaletteWindow = null;
    }
    myFileEditorManager.removeFileEditorManagerListener(myListener);
  }

  @NotNull
  public String getComponentName() {
    return "PaletteManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static PaletteManager getInstance(final Project project) {
    return project.getComponent(PaletteManager.class);
  }

  public void clearActiveItem() {
    if (myPaletteWindow != null) {
      myPaletteWindow.clearActiveItem();
    }
  }

  @Nullable
  public PaletteItem getActiveItem() {
    if (myPaletteWindow != null) {
      return myPaletteWindow.getActiveItem();
    }
    return null;
  }

  @Nullable
  public <T extends PaletteItem> T getActiveItem(Class<T> cls) {
    PaletteItem item = getActiveItem();
    if (item != null && item.getClass().isInstance(item)) {
      //noinspection unchecked
      return (T) item;
    }
    return null;
  }

  public void addKeyListener(KeyListener l) {
    myKeyListeners.add(l);
  }

  public void removeKeyListener(KeyListener l) {
    myKeyListeners.remove(l);
  }

  public void addDragEventListener(PaletteDragEventListener l) {
    myDragEventListeners.add(l);
  }

  public void removeDragEventListener(PaletteDragEventListener l) {
    myDragEventListeners.remove(l);
  }

  public void addSelectionListener(ListSelectionListener l) {
    mySelectionListeners.add(l);
  }

  public void removeSelectionListener(ListSelectionListener l) {
    mySelectionListeners.remove(l);
  }

  private void processFileEditorChange() {
    myPaletteWindow.refreshPaletteIfChanged();
    if (myPaletteWindow.getActiveGroupCount() == 0) {
      myPaletteToolWindow.setAvailable(false, null);
    }
    else {
      myPaletteToolWindow.setAvailable(true, null);
      myPaletteToolWindow.show(null);
    }
  }

  void notifyKeyEvent(final KeyEvent e) {
    for(KeyListener l: myKeyListeners) {
      if (e.getID() == KeyEvent.KEY_PRESSED) {
        l.keyPressed(e);
      }
      else if (e.getID() == KeyEvent.KEY_RELEASED) {
        l.keyReleased(e);
      }
      else if (e.getID() == KeyEvent.KEY_TYPED) {
        l.keyTyped(e);
      }
    }
  }

  void notifyDropActionChanged(int gestureModifiers) {
    for(PaletteDragEventListener l: myDragEventListeners) {
      l.dropActionChanged(gestureModifiers);
    }
  }

  void notifySelectionChanged(final ListSelectionEvent event) {
    for(ListSelectionListener l: mySelectionListeners) {
      l.valueChanged(event);
    }
  }

  private class MyFileEditorManagerListener implements FileEditorManagerListener {
    public void fileOpened(FileEditorManager source, VirtualFile file) {
      processFileEditorChange();
    }

    public void fileClosed(FileEditorManager source, VirtualFile file) {
      processFileEditorChange();
    }

    public void selectionChanged(FileEditorManagerEvent event) {
      processFileEditorChange();
    }
  }
}
