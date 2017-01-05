package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.diff.DiffOptions;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.List;

import static java.util.Collections.singletonList;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitMergeClient extends BaseSvnClient implements MergeClient {

  private static final List<SVNRevisionRange> ALL_REVISIONS_RANGE =
    singletonList(new SVNRevisionRange(SVNRevision.create(1), SVNRevision.HEAD));

  public void merge(@NotNull SvnTarget source,
                    @NotNull File destination,
                    boolean dryRun,
                    boolean reintegrate,
                    @Nullable DiffOptions diffOptions,
                    @Nullable ProgressTracker handler) throws VcsException {
    assertUrl(source);

    SVNDiffClient client = createClient(diffOptions, handler);
    try {
      if (reintegrate) {
        client.doMergeReIntegrate(source.getURL(), source.getPegRevision(), destination, dryRun);
      }
      else {
        client.doMerge(source.getURL(), source.getPegRevision(), ALL_REVISIONS_RANGE, destination, SVNDepth.UNKNOWN, true, false, dryRun,
                       false);
      }
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  @Override
  public void merge(@NotNull SvnTarget source,
                    @NotNull SVNRevisionRange range,
                    @NotNull File destination,
                    @Nullable Depth depth,
                    boolean dryRun,
                    boolean recordOnly,
                    boolean force,
                    @Nullable DiffOptions diffOptions,
                    @Nullable ProgressTracker handler) throws VcsException {
    assertUrl(source);

    try {
      createClient(diffOptions, handler).doMerge(source.getURL(), source.getPegRevision(), singletonList(range), destination,
                                                 toDepth(depth), true, force, dryRun, recordOnly);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  @Override
  public void merge(@NotNull SvnTarget source1,
                    @NotNull SvnTarget source2,
                    @NotNull File destination,
                    @Nullable Depth depth,
                    boolean useAncestry,
                    boolean dryRun,
                    boolean recordOnly,
                    boolean force,
                    @Nullable DiffOptions diffOptions,
                    @Nullable ProgressTracker handler) throws VcsException {
    assertUrl(source1);
    assertUrl(source2);

    try {
      createClient(diffOptions, handler).doMerge(source1.getURL(), source1.getPegRevision(), source2.getURL(), source2.getPegRevision(),
                                                 destination, toDepth(depth), useAncestry, force, dryRun, recordOnly);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  @NotNull
  private SVNDiffClient createClient(@Nullable DiffOptions diffOptions, @Nullable ProgressTracker handler) {
    SVNDiffClient client = myVcs.getSvnKitManager().createDiffClient();

    client.setMergeOptions(toDiffOptions(diffOptions));
    client.setEventHandler(toEventHandler(handler));

    return client;
  }
}
