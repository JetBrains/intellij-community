// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.delete;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.SvnClient;
import org.jetbrains.idea.svn.api.Url;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public interface DeleteClient extends SvnClient {

  void delete(@NotNull File path, boolean force, boolean dryRun, @Nullable ProgressTracker handler) throws VcsException;

  long delete(@NotNull Url url, @NotNull String message) throws VcsException;
}
