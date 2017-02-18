package org.jetbrains.idea.svn.content;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.CommandExecutor;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdContentClient extends BaseSvnClient implements ContentClient {

  private static final Logger LOG = Logger.getInstance(CmdContentClient.class);

  private static final String NO_PRISTINE_VERSION_FOR_FILE = "has no pristine version until it is committed";

  @Override
  public byte[] getContent(@NotNull SvnTarget target, @Nullable SVNRevision revision, @Nullable SVNRevision pegRevision)
    throws VcsException, FileTooBigRuntimeException {
    // TODO: rewrite this to provide output as Stream
    // TODO: Also implement max size constraint like in SvnKitContentClient
    // NOTE: Export could not be used to get content of scheduled for deletion file

    List<String> parameters = new ArrayList<>();
    CommandUtil.put(parameters, target.getPathOrUrlString(), pegRevision);
    CommandUtil.put(parameters, revision);

    CommandExecutor command = null;
    try {
      command = execute(myVcs, target, SvnCommandName.cat, parameters, null);
    }
    catch (SvnBindException e) {
      // "no pristine version" error is thrown, for instance, for locally replaced files (not committed yet)
      if (StringUtil.containsIgnoreCase(e.getMessage(), NO_PRISTINE_VERSION_FOR_FILE)) {
        LOG.debug(e);
      }
      else {
        throw e;
      }
    }

    byte[] bytes = command != null ? command.getBinaryOutput().toByteArray() : ArrayUtil.EMPTY_BYTE_ARRAY;
    ContentRevisionCache.checkContentsSize(target.getPathOrUrlString(), bytes.length);

    return bytes;
  }
}
