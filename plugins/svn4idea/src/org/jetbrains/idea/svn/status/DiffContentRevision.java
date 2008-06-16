package org.jetbrains.idea.svn.status;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;

import java.io.ByteArrayOutputStream;

public class DiffContentRevision implements ContentRevision {
  private String myPath;
  private SVNRepository myRepository;
  private String myContents;
  private FilePath myFilePath;
  private long myRevision;

  public DiffContentRevision(String path, @NotNull SVNRepository repos, long revision) {
    this(path, repos, revision, VcsUtil.getFilePath(path));
  }

  public DiffContentRevision(final String path, final SVNRepository repository, final long revision, final FilePath filePath) {
    myPath = path;
    myRepository = repository;
    myFilePath = filePath;
    myRevision = revision;
  }

  @Nullable
  public String getContent() throws VcsException {
    if (myContents == null) {
      ByteArrayOutputStream bos = new ByteArrayOutputStream(2048);
      try {
        myRepository.getFile(myPath, -1, null, bos);
        myRepository.closeSession();
      } catch (SVNException e) {
        throw new VcsException(e);
      }
      myContents = new String(bos.toByteArray());
    }
    return myContents;
  }

  @NotNull
  public FilePath getFile() {
    return myFilePath;
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return new VcsRevisionNumber.Long(myRevision);
  }
}
