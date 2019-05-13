package org.jetbrains.idea.svn.add;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.SvnClient;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface AddClient extends SvnClient {

  void add(@NotNull File file,
           @Nullable Depth depth,
           boolean makeParents,
           boolean includeIgnored,
           boolean force,
           @Nullable ProgressTracker handler) throws VcsException;
}
