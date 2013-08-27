package org.jetbrains.idea.svn.properties;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface PropertyClient extends SvnClient {

  @Nullable
  SVNPropertyData getProperty(@NotNull final File path,
                              @NotNull final String property,
                              boolean revisionProperty,
                              @Nullable SVNRevision pegRevision,
                              @Nullable SVNRevision revision) throws VcsException;
}
