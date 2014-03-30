package org.jetbrains.idea.svn.content;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

/**
 * @author Konstantin Kolosovsky.
 */
public interface ContentClient extends SvnClient {

  byte[] getContent(@NotNull SvnTarget target, @Nullable SVNRevision revision, @Nullable SVNRevision pegRevision)
    throws VcsException, FileTooBigRuntimeException;
}
