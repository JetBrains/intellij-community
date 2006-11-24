/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 23.11.2006
 * Time: 19:06:26
 */
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public class ShelvedChange {
  private String myPatchPath;
  private String myPatchedFilePath;
  private FileStatus myFileStatus;
  private Change myChange;

  public ShelvedChange(final String patchPath, final String filePath, final FileStatus fileStatus) {
    myPatchPath = patchPath;
    myPatchedFilePath = filePath;
    myFileStatus = fileStatus;
  }

  public String getPatchedFilePath() {
    return myPatchedFilePath;
  }

  public String getFileName() {
    int pos = myPatchedFilePath.lastIndexOf('/');
    if (pos >= 0) return myPatchedFilePath.substring(pos+1);
    return myPatchedFilePath;
  }

  public String getDirectory() {
    int pos = myPatchedFilePath.lastIndexOf('/');
    if (pos >= 0) return myPatchedFilePath.substring(0, pos).replace('/', File.separatorChar);
    return File.separator;
  }

  public FileStatus getFileStatus() {
    return myFileStatus;
  }

  public Change getChange(Project project) {
    if (myChange == null) {
      ContentRevision beforeRevision = null;
      ContentRevision afterRevision = null;
      FilePath filePath = null;
      VirtualFile baseDir = project.getProjectFile().getParent();

      if (myFileStatus != FileStatus.ADDED) {
        VirtualFile changedFile = VfsUtil.findRelativeFile(myPatchedFilePath, baseDir);
        if (changedFile != null) {
          filePath = new FilePathImpl(changedFile);
          beforeRevision = new CurrentContentRevision(filePath);
        }
      }
      if (filePath == null) {
        VirtualFile changedDir = VfsUtil.findRelativeFile(getDirectory(), baseDir);
        filePath = new FilePathImpl(changedDir, getFileName(), false);
      }

      if (myFileStatus != FileStatus.DELETED) {
        afterRevision = new PatchedContentRevision(filePath);
      }
      myChange = new Change(beforeRevision, afterRevision, myFileStatus);
    }
    return myChange;
  }

  private class PatchedContentRevision implements ContentRevision {
    private final FilePath myFilePath;

    public PatchedContentRevision(final FilePath filePath) {
      myFilePath = filePath;
    }

    @Nullable
    public String getContent() {
      final Document doc = FileDocumentManager.getInstance().getDocument(myFilePath.getVirtualFile());
      String baseContent = doc.getText();

      try {
        List<FilePatch> filePatches = ShelveChangesManager.loadPatches(myPatchPath);
        for(FilePatch patch: filePatches) {
          if (myPatchedFilePath.equals(patch.getBeforeName())) {
            StringBuilder newText = new StringBuilder();
            patch.applyModifications(baseContent, newText);
            return newText.toString();
          }
        }
      }
      catch (Exception e) {
        return null;
      }

      return null;
    }

    @NotNull
    public FilePath getFile() {
      return myFilePath;
    }

    @NotNull
    public VcsRevisionNumber getRevisionNumber() {
      return VcsRevisionNumber.NULL;
    }
  }
}