package org.jetbrains.idea.svn.upgrade;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.SvnClient;

import java.io.File;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public interface UpgradeClient extends SvnClient {

  void upgrade(@NotNull File path, @NotNull WorkingCopyFormat format, @Nullable ProgressTracker handler) throws VcsException;

  List<WorkingCopyFormat> getSupportedFormats() throws VcsException;
}
