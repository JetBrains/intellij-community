package org.jetbrains.idea.svn.add;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.SVNDepth;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface AddClient extends SvnClient {

  void add(@NotNull File file,
           @Nullable SVNDepth depth,
           boolean makeParents,
           boolean includeIgnored,
           boolean force,
           @Nullable ProgressTracker handler) throws VcsException;
}
