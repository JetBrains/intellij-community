// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.commandLine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.auth.AuthenticationService;

import static org.jetbrains.idea.svn.SvnUtil.createUrl;

public abstract class AuthCallbackCase {
  protected final Url myUrl;
  protected final @NotNull AuthenticationService myAuthenticationService;

  AuthCallbackCase(@NotNull AuthenticationService authenticationService, Url url) {
    myAuthenticationService = authenticationService;
    myUrl = url;
  }

  public abstract boolean canHandle(final String error);

  abstract boolean getCredentials(final String errText) throws SvnBindException;

  public void updateParameters(@NotNull Command command) {
  }

  protected Url parseUrl(String urlValue) {
    try {
      return createUrl(urlValue);
    }
    catch (SvnBindException e) {
      return null;
    }
  }
}
