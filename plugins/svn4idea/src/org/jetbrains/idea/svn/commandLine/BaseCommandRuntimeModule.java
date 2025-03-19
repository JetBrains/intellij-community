// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.commandLine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.auth.AuthenticationService;

public abstract class BaseCommandRuntimeModule implements CommandRuntimeModule {

  protected final @NotNull CommandRuntime myRuntime;
  protected final @NotNull AuthenticationService myAuthenticationService;
  protected final @NotNull SvnVcs myVcs;

  public BaseCommandRuntimeModule(@NotNull CommandRuntime runtime) {
    myRuntime = runtime;
    myAuthenticationService = runtime.getAuthenticationService();
    myVcs = runtime.getVcs();
  }

  @Override
  public void onStart(@NotNull Command command) {

  }
}
