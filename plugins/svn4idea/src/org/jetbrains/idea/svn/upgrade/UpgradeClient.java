package org.jetbrains.idea.svn.upgrade;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;

import java.io.File;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public interface UpgradeClient extends SvnClient {

  void upgrade(@NotNull File path, @NotNull WorkingCopyFormat format, @Nullable ISVNEventHandler handler) throws VcsException;

  List<WorkingCopyFormat> getSupportedFormats() throws VcsException;
}
