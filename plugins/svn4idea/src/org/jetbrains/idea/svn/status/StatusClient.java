// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.status;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.SvnClient;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import java.io.File;

public interface StatusClient extends SvnClient {
  void doStatus(@NotNull File path,
                @NotNull Depth depth,
                boolean remote,
                boolean reportAll,
                boolean includeIgnored,
                boolean collectParentExternals,
                @NotNull StatusConsumer handler) throws SvnBindException;

  @Nullable
  Status doStatus(@NotNull File path, boolean remote) throws SvnBindException;
}
