package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.SvnClient;
import org.jetbrains.idea.svn.diff.DiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface MergeClient extends SvnClient {

  void merge(@NotNull SvnTarget source,
             @NotNull File destination,
             boolean dryRun,
             @Nullable DiffOptions diffOptions,
             @Nullable ProgressTracker handler) throws VcsException;

  void merge(@NotNull SvnTarget source,
             @NotNull SVNRevisionRange range,
             @NotNull File destination,
             @Nullable Depth depth,
             boolean dryRun,
             boolean recordOnly,
             boolean force,
             @Nullable DiffOptions diffOptions,
             @Nullable ProgressTracker handler) throws VcsException;

  void merge(@NotNull SvnTarget source1,
             @NotNull SvnTarget source2,
             @NotNull File destination,
             @Nullable Depth depth,
             boolean useAncestry,
             boolean dryRun,
             boolean recordOnly,
             boolean force,
             @Nullable DiffOptions diffOptions,
             @Nullable ProgressTracker handler) throws VcsException;
}
