package org.jetbrains.idea.svn.content;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.*;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.io.IOException;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdContentClient extends BaseSvnClient implements ContentClient {

  @Override
  public byte[] getContent(@NotNull SvnTarget target, @Nullable SVNRevision revision, @Nullable SVNRevision pegRevision)
    throws VcsException, FileTooBigRuntimeException {
    // TODO: rewrite this to provide output as Stream
    // TODO: Also implement max size constraint like in SvnKitContentClient
    // TODO: As alternative implementation we could indicate in Command that it requires binary output and create special
    // TODO: ProcessHandler that will read process input stream as binary

    SvnTarget from = resolveTarget(target, pegRevision);
    File temp = null;
    byte[] bytes;
    try {
      temp = FileUtil.createTempDirectory("svn_content", null);
      myFactory.createExportClient().export(from, temp, revision, SVNDepth.EMPTY, null, false, false, null);
      bytes = FileUtil.loadFileBytes(new File(temp, SVNPathUtil.tail(getPath(from))));
    } catch (IOException e) {
      throw new SvnBindException(e);
    } finally {
      if (temp != null) {
        FileUtil.delete(temp);
      }
    }

    ContentRevisionCache.checkContentsSize(target.getPathOrUrlString(), bytes.length);

    return bytes;
  }

  @NotNull
  private static String getPath(@NotNull SvnTarget target) {
    return (target.isFile() ? target.getFile().getAbsolutePath() : target.getURL().toDecodedString()).replace("\\", "/");
  }

  @NotNull
  private static SvnTarget resolveTarget(@NotNull SvnTarget target, @Nullable SVNRevision pegRevision) throws SvnBindException {
    return target.isFile() ? SvnTarget.fromFile(target.getFile(), pegRevision) : SvnTarget.fromURL(target.getURL(), pegRevision);
  }
}
