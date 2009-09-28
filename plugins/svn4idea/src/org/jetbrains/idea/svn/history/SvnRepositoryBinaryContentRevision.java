package org.jetbrains.idea.svn.history;

import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;

/**
 * @author yole
 */
public class SvnRepositoryBinaryContentRevision extends SvnRepositoryContentRevision implements BinaryContentRevision {
  private byte[] myBinaryContent;

  public SvnRepositoryBinaryContentRevision(final SvnVcs vcs, final String repositoryRoot, final String path,
                                            @Nullable final FilePath localPath, final long revision) {
    super(vcs, repositoryRoot, path, localPath, revision);
  }

  @Nullable
  public byte[] getBinaryContent() throws VcsException {
    if (myBinaryContent == null) {
      myBinaryContent = loadContent().toByteArray();
    }
    return myBinaryContent;    
  }
}