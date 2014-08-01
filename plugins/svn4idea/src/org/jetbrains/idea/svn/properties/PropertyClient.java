package org.jetbrains.idea.svn.properties;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface PropertyClient extends SvnClient {

  @Nullable
  PropertyValue getProperty(@NotNull final SvnTarget target,
                            @NotNull final String property,
                            boolean revisionProperty,
                            @Nullable SVNRevision revision) throws VcsException;

  void getProperty(@NotNull SvnTarget target, @NotNull String property,
                   @Nullable SVNRevision revision,
                   @Nullable Depth depth,
                   @Nullable PropertyConsumer handler) throws VcsException;

  void list(@NotNull SvnTarget target,
            @Nullable SVNRevision revision,
            @Nullable Depth depth,
            @Nullable PropertyConsumer handler) throws VcsException;

  void setProperty(@NotNull File file,
                   @NotNull String property,
                   @Nullable PropertyValue value,
                   @Nullable Depth depth,
                   boolean force) throws VcsException;

  void setProperties(@NotNull File file, @NotNull PropertiesMap properties) throws VcsException;

  void setRevisionProperty(@NotNull SvnTarget target,
                           @NotNull String property,
                           @NotNull SVNRevision revision,
                           @Nullable PropertyValue value,
                           boolean force) throws VcsException;
}
