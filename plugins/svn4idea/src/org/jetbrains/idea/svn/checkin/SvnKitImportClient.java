package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.DefaultSVNCommitHandler;
import org.tmatesoft.svn.core.wc.ISVNFileFilter;
import org.tmatesoft.svn.core.wc.SVNCommitClient;

import java.io.File;
import java.util.function.Predicate;

public class SvnKitImportClient extends BaseSvnClient implements ImportClient {

  @Override
  public long doImport(@NotNull File path,
                       @NotNull SVNURL url,
                       @Nullable Depth depth,
                       @NotNull String message,
                       boolean noIgnore,
                       @Nullable CommitEventHandler handler,
                       @Nullable Predicate<File> filter) throws VcsException {
    SVNCommitClient client = myVcs.getSvnKitManager().createCommitClient();

    client.setEventHandler(toEventHandler(handler));
    client.setCommitHandler(filter != null ? new CommitHandler(filter) : null);

    try {
      SVNCommitInfo info = client.doImport(path, url, message, null, !noIgnore, false, toDepth(depth));
      return info.getNewRevision();
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }
  private static class CommitHandler extends DefaultSVNCommitHandler implements ISVNFileFilter {
    @NotNull private final Predicate<File> myFilter;

    public CommitHandler(@NotNull Predicate<File> filter) {
      myFilter = filter;
    }

    public boolean accept(File file) {
      return myFilter.test(file);
    }
  }
}
