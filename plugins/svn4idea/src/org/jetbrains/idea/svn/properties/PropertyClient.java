package org.jetbrains.idea.svn.properties;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface PropertyClient extends SvnClient {

  @Nullable
  SVNPropertyData getProperty(@NotNull final SvnTarget target,
                              @NotNull final String property,
                              boolean revisionProperty,
                              @Nullable SVNRevision revision) throws VcsException;

  void getProperty(@NotNull SvnTarget target, @NotNull String property,
                   @Nullable SVNRevision revision,
                   @Nullable SVNDepth depth,
                   @Nullable ISVNPropertyHandler handler) throws VcsException;

  void list(@NotNull SvnTarget target,
            @Nullable SVNRevision revision,
            @Nullable SVNDepth depth,
            @Nullable ISVNPropertyHandler handler) throws VcsException;

  void setProperty(@NotNull File file,
                   @NotNull String property,
                   @Nullable SVNPropertyValue value,
                   @Nullable SVNDepth depth,
                   boolean force) throws VcsException;
}
