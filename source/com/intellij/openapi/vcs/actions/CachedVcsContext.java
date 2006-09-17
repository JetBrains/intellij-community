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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

public class CachedVcsContext implements VcsContext {
  private final Project myProject;
  private final VirtualFile mySelectedFile;
  private final VirtualFile[] mySelectedFiles;
  private final Collection<VirtualFile> mySelectedFilesCollection;
  private final Editor myEditor;
  private final File[] mySelectedIOFiles;
  private final int myModifiers;
  private final Refreshable myRefreshablePanel;
  private final String myPlace;
  private final PsiElement myPsiElement;
  private final File mySelectedIOFile;
  private final FilePath[] mySelectedFilePaths;
  private final FilePath mySelectedFilePath;
  private final ChangeList[] mySelectedChangeLists;
  private final Change[] mySelectedChanges;

  public CachedVcsContext(VcsContext baseContext) {
    myProject = baseContext.getProject();
    mySelectedFile = baseContext.getSelectedFile();
    mySelectedFiles = baseContext.getSelectedFiles();
    mySelectedFilesCollection = baseContext.getSelectedFilesCollection();
    myEditor = baseContext.getEditor();
    mySelectedIOFiles = baseContext.getSelectedIOFiles();
    myModifiers = baseContext.getModifiers();
    myRefreshablePanel = baseContext.getRefreshableDialog();
    myPlace = baseContext.getPlace();
    myPsiElement = baseContext.getPsiElement();
    mySelectedIOFile = baseContext.getSelectedIOFile();
    mySelectedFilePaths = baseContext.getSelectedFilePaths();
    mySelectedFilePath = baseContext.getSelectedFilePath();
    mySelectedChangeLists = baseContext.getSelectedChangeLists();
    mySelectedChanges = baseContext.getSelectedChanges();
  }

  public String getPlace() {
    return myPlace;
  }

  public PsiElement getPsiElement() {
    return myPsiElement;
  }

  public Project getProject() {
    return myProject;
  }

  public VirtualFile getSelectedFile() {
    return mySelectedFile;
  }

  public VirtualFile[] getSelectedFiles() {
    return mySelectedFiles;
  }

  public Editor getEditor() {
    return myEditor;
  }

  public Collection<VirtualFile> getSelectedFilesCollection() {
    return mySelectedFilesCollection;
  }

  public File[] getSelectedIOFiles() {
    return mySelectedIOFiles;
  }

  public int getModifiers() {
    return myModifiers;
  }

  public Refreshable getRefreshableDialog() {
    return myRefreshablePanel;
  }

  public File getSelectedIOFile() {
    return mySelectedIOFile;
  }

  public FilePath[] getSelectedFilePaths() {
    return mySelectedFilePaths;
  }

  public FilePath getSelectedFilePath() {
    return mySelectedFilePath;
  }

  @Nullable
  public ChangeList[] getSelectedChangeLists() {
    return mySelectedChangeLists;
  }

  @Nullable
  public Change[] getSelectedChanges() {
    return mySelectedChanges;
  }
}
