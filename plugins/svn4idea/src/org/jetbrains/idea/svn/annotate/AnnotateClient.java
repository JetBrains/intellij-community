package org.jetbrains.idea.svn.annotate;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.SvnClient;
import org.jetbrains.idea.svn.diff.DiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

/**
 * @author Konstantin Kolosovsky.
 */
public interface AnnotateClient extends SvnClient {

  void annotate(@NotNull SvnTarget target,
                @NotNull SVNRevision startRevision,
                @NotNull SVNRevision endRevision,
                boolean includeMergedRevisions,
                @Nullable DiffOptions diffOptions,
                @Nullable AnnotationConsumer handler) throws VcsException;
}
