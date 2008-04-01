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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.ApplyPatchException;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ShelvedChange {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.shelf.ShelvedChange");

  private String myPatchPath;
  private String myBeforePath;
  private String myAfterPath;
  private FileStatus myFileStatus;
  private Change myChange;

  public ShelvedChange(final String patchPath, final String beforePath, final String afterPath, final FileStatus fileStatus) {
    myPatchPath = patchPath;
    myBeforePath = beforePath;
    myAfterPath = afterPath;
    myFileStatus = fileStatus;
  }

  public String getBeforePath() {
    return myBeforePath;
  }

  public String getAfterPath() {
    return myAfterPath;
  }

  public String getBeforeFileName() {
    int pos = myBeforePath.lastIndexOf('/');
    if (pos >= 0) return myBeforePath.substring(pos+1);
    return myBeforePath;
  }

  public String getBeforeDirectory() {
    int pos = myBeforePath.lastIndexOf('/');
    if (pos >= 0) return myBeforePath.substring(0, pos).replace('/', File.separatorChar);
    return File.separator;
  }

  public FileStatus getFileStatus() {
    return myFileStatus;
  }

  public Change getChange(Project project) {
    if (myChange == null) {
      ContentRevision beforeRevision = null;
      ContentRevision afterRevision = null;
      File baseDir = new File(project.getBaseDir().getPath());

      File file = getAbsolutePath(baseDir, myBeforePath);
      final FilePathImpl beforePath = new FilePathImpl(file, false);
      beforePath.refresh();
      if (myFileStatus != FileStatus.ADDED) {
        beforeRevision = new CurrentContentRevision(beforePath) {
          @NotNull
          public VcsRevisionNumber getRevisionNumber() {
            return new TextRevisionNumber(VcsBundle.message("local.version.title"));
          }
        };
      }
      if (myFileStatus != FileStatus.DELETED) {
        final FilePathImpl afterPath = new FilePathImpl(getAbsolutePath(baseDir, myAfterPath), false);
        afterRevision = new PatchedContentRevision(beforePath, afterPath);
      }
      myChange = new Change(beforeRevision, afterRevision, myFileStatus);
    }
    return myChange;
  }

  private static File getAbsolutePath(final File baseDir, final String relativePath) {
    File file;
    try {
      file = new File(baseDir, relativePath).getCanonicalFile();
    }
    catch (IOException e) {
      LOG.info(e);
      file = new File(baseDir, relativePath);
    }
    return file;
  }

  @Nullable
  public TextFilePatch loadFilePatch() throws IOException, PatchSyntaxException {
    List<TextFilePatch> filePatches = ShelveChangesManager.loadPatches(myPatchPath);
    for(TextFilePatch patch: filePatches) {
      if (myBeforePath.equals(patch.getBeforeName())) {
        return patch;
      }
    }
    return null;
  }

  private class PatchedContentRevision implements ContentRevision {
    private final FilePath myBeforeFilePath;
    private final FilePath myAfterFilePath;
    private String myContent;

    public PatchedContentRevision(final FilePath beforeFilePath, final FilePath afterFilePath) {
      myBeforeFilePath = beforeFilePath;
      myAfterFilePath = afterFilePath;
    }

    @Nullable
    public String getContent() throws VcsException {
      if (myContent == null) {
        try {
          myContent = loadContent();
        }
        catch (Exception e) {
          throw new VcsException(e);
        }
      }

      return myContent;
    }

    @Nullable
    private String loadContent() throws IOException, PatchSyntaxException, ApplyPatchException {
      TextFilePatch patch = loadFilePatch();
      if (patch != null) {
        return loadContent(patch);
      }
      return null;
    }

    private String loadContent(final TextFilePatch patch) throws ApplyPatchException {
      if (patch.isNewFile()) {
        return patch.getNewFileText();
      }
      if (patch.isDeletedFile()) {
        return null;
      }
      StringBuilder newText = new StringBuilder();
      patch.applyModifications(getBaseContent(), newText);
      return newText.toString();
    }

    private String getBaseContent() {
      myBeforeFilePath.refresh();
      final Document doc = FileDocumentManager.getInstance().getDocument(myBeforeFilePath.getVirtualFile());
      return doc.getText();
    }

    @NotNull
    public FilePath getFile() {
      return myAfterFilePath;
    }

    @NotNull
    public VcsRevisionNumber getRevisionNumber() {
      return new TextRevisionNumber(VcsBundle.message("shelved.version.name"));
    }
  }

  private static class TextRevisionNumber implements VcsRevisionNumber {
    private final String myText;

    public TextRevisionNumber(final String text) {
      myText = text;
    }

    public String asString() {
      return myText;
    }

    public int compareTo(final VcsRevisionNumber o) {
      return 0;
    }
  }
}