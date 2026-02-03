// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.SvnClient;
import org.jetbrains.idea.svn.api.Url;

import java.io.File;
import java.util.function.Predicate;

public interface ImportClient extends SvnClient {

  long doImport(@NotNull File path,
                @NotNull Url url,
                @Nullable Depth depth,
                @NotNull String message,
                boolean noIgnore,
                @Nullable CommitEventHandler handler,
                @Nullable Predicate<File> filter) throws VcsException;
}
