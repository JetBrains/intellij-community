/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDataConstants;
import com.intellij.openapi.vcs.fileView.impl.FileViewPanel;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class VcsContextWrapper implements VcsContext {
  protected final DataContext myContext;
  protected final int myModifiers;
  private final String myPlace;

  public VcsContextWrapper(DataContext context, int modifiers, String place) {
    myContext = context;
    myModifiers = modifiers;
    myPlace = place;
  }

  public String getPlace() {
    return myPlace;
  }

  public PsiElement getPsiElement() {
    return (PsiElement)myContext.getData(DataConstants.PSI_ELEMENT);
  }

  public static VcsContext createCachedInstanceOn(AnActionEvent event) {
    return new CachedVcsContext(createInstanceOn(event));
  }

  public static VcsContextWrapper createInstanceOn(final AnActionEvent event) {
    return new VcsContextWrapper(event.getDataContext(), event.getModifiers(), event.getPlace());
  }

  public Project getProject() {
    return (Project)myContext.getData(DataConstants.PROJECT);
  }

  public VirtualFile getSelectedFile() {
    VirtualFile[] files = getSelectedFiles();
    if (files == null || files.length == 0) return null;
    return files[0];
  }

  public VirtualFile[] getSelectedFiles() {
    VirtualFile[] fileArray = (VirtualFile[])myContext.getData(DataConstants.VIRTUAL_FILE_ARRAY);
    if (fileArray != null) {
      return filterLocalFiles(fileArray);
    }

    VirtualFile virtualFile = (VirtualFile)myContext.getData(DataConstants.VIRTUAL_FILE);
    if (virtualFile != null && isLocal(virtualFile)) {
      return new VirtualFile[]{virtualFile};
    }

    return VirtualFile.EMPTY_ARRAY;
  }

  private static boolean isLocal(VirtualFile virtualFile) {
    return virtualFile.getFileSystem() == LocalFileSystem.getInstance();
  }

  private static VirtualFile[] filterLocalFiles(VirtualFile[] fileArray) {
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (VirtualFile virtualFile : fileArray) {
      if (isLocal(virtualFile)) {
        result.add(virtualFile);
      }
    }
    return result.toArray(new VirtualFile[result.size()]);
  }

  public Editor getEditor() {
    return (Editor)myContext.getData(DataConstants.EDITOR);
  }

  public Collection<VirtualFile> getSelectedFilesCollection() {
    return Arrays.asList(getSelectedFiles());
  }

  public File getSelectedIOFile() {
    File file = (File)myContext.getData(VcsDataConstants.IO_FILE);
    if (file != null) return file;
    File[] files = (File[])myContext.getData(VcsDataConstants.IO_FILE_ARRAY);
    if (files == null) return null;
    if (files.length == 0) return null;
    return files[0];
  }

  public File[] getSelectedIOFiles() {
    File[] files = (File[])myContext.getData(VcsDataConstants.IO_FILE_ARRAY);
    if (files != null && files.length > 0) return files;
    File file = getSelectedIOFile();
    if (file != null) return new File[]{file};
    return null;
  }

  public int getModifiers() {
    return myModifiers;
  }

  public Refreshable getRefreshableDialog() {
    final Object dataFromContext = myContext.getData(Refreshable.PANEL);
    if (dataFromContext != null) {
      return ((Refreshable)dataFromContext);
    }
    final Project project = getProject();
    if (project == null) return null;

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    if (toolWindowManager == null) return null;

    ToolWindow fileViewToolWindow = toolWindowManager.getToolWindow(ProjectLevelVcsManager.FILE_VIEW_TOOL_WINDOW_ID);
    if (fileViewToolWindow.isAvailable()) {
      final JComponent component = fileViewToolWindow.getComponent();
      return ((FileViewPanel) component);
    } else {
      return null;
    }
  }

  public FilePath[] getSelectedFilePaths() {
    ArrayList<FilePath> result = new ArrayList<FilePath>();
    FilePath path = (FilePath)myContext.getData(VcsDataConstants.FILE_PATH);
    if (path != null) {
      result.add(path);
    }

    FilePath[] paths = (FilePath[])myContext.getData(VcsDataConstants.FILE_PATH_ARRAY);
    if (paths != null) {
      for (FilePath filePath : paths) {
        if (!result.contains(filePath)) {
          result.add(filePath);
        }
      }
    }

    VirtualFile[] selectedFiles = getSelectedFiles();
    if (selectedFiles != null) {
      for (VirtualFile selectedFile : selectedFiles) {
        FilePathImpl filePath = new FilePathImpl(selectedFile);
        if (!result.contains(filePath)) {
          result.add(filePath);
        }
      }
    }

    File[] selectedIOFiles = getSelectedIOFiles();
    if (selectedIOFiles != null){
      for (File selectedFile : selectedIOFiles) {
        FilePathImpl filePath = FilePathImpl.create(selectedFile);
        if ((filePath != null) && !result.contains(filePath)) {
          result.add(filePath);
        }
      }

    }

    return result.toArray(new FilePath[result.size()]);

  }

  public FilePath getSelectedFilePath() {
    FilePath[] selectedFilePaths = getSelectedFilePaths();
    if (selectedFilePaths.length == 0) {
      return null;
    }
    else {
      return selectedFilePaths[0];
    }
  }
}
