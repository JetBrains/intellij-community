/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.palette.impl;

import com.intellij.ide.palette.PaletteItem;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author yole
 */
public class PaletteManager implements ProjectComponent {
  private Project myProject;
  private FileEditorManager myFileEditorManager;
  private PaletteWindow myPaletteWindow;
  private ToolWindow myPaletteToolWindow;
  private PaletteManager.MyFileEditorManagerListener myListener;

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
        myPaletteToolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow("Palette",
                                                                                          new JScrollPane(myPaletteWindow),
                                                                                          ToolWindowAnchor.RIGHT);
      }
    });
  }

  public void projectClosed() {
    if (myPaletteWindow != null) {
      ToolWindowManager.getInstance(myProject).unregisterToolWindow("Palette");
      myPaletteWindow = null;
    }
    myFileEditorManager.removeFileEditorManagerListener(myListener);
  }

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

  private void processFileEditorChange() {
    myPaletteWindow.refreshPaletteIfChanged();
    if (myPaletteWindow.getActiveGroupCount() == 0) {
      myPaletteToolWindow.hide(null);
    }
    else {
      myPaletteToolWindow.show(null);
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
