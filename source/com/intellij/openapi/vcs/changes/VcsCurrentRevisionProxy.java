package com.intellij.openapi.vcs.changes;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public class VcsCurrentRevisionProxy implements ContentRevision {
  private DiffProvider myDiffProvider;
  private VirtualFile myFile;
  private ContentRevision myVcsRevision;

  public VcsCurrentRevisionProxy(final DiffProvider diffProvider, final VirtualFile file) {
    myDiffProvider = diffProvider;
    myFile = file;
  }

  @Nullable
  public String getContent() throws VcsException {
    return getVcsRevision().getContent();
  }

  @NotNull
  public FilePath getFile() {
    return new FilePathImpl(myFile);
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    try {
      return getVcsRevision().getRevisionNumber();
    }
    catch(VcsException ex) {
      return VcsRevisionNumber.NULL;
    }
  }

  private ContentRevision getVcsRevision() throws VcsException {
    if (myVcsRevision == null) {
      final VcsRevisionNumber currentRevision = myDiffProvider.getCurrentRevision(myFile);
      if (currentRevision == null) {
        throw new VcsException("Failed to fetch current revision");
      }
      myVcsRevision = myDiffProvider.createFileContent(currentRevision, myFile);
      if (myVcsRevision == null) {
        throw new VcsException("Failed to create content for current revision");
      }
    }
    return myVcsRevision;
  }
}