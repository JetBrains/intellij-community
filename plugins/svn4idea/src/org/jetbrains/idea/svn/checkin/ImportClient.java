package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.SvnClient;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.util.function.Predicate;

public interface ImportClient extends SvnClient {

  long doImport(@NotNull File path,
                @NotNull SVNURL url,
                @Nullable Depth depth,
                @NotNull String message,
                boolean noIgnore,
                @Nullable CommitEventHandler handler,
                @Nullable Predicate<File> filter) throws VcsException;
}
