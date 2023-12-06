// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn;

import com.intellij.openapi.diagnostic.DelegatingLogger;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.ErrorCode;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import java.util.concurrent.TimeUnit;

import static org.jetbrains.idea.svn.api.ErrorCode.*;

class SvnFilteringExceptionLogger extends DelegatingLogger<Logger> {
  private static final long EXPIRATION = TimeUnit.SECONDS.toNanos(30);

  private static final ErrorCode[] ourErrorsToFilter =
    {WC_UNSUPPORTED_FORMAT, WC_CORRUPT, WC_CORRUPT_TEXT_BASE, WC_NOT_FILE, WC_NOT_WORKING_COPY, WC_PATH_NOT_FOUND};

  private final long[] myExpirationTime = new long[ourErrorsToFilter.length];

  SvnFilteringExceptionLogger(@NotNull Logger delegate) {
    super(delegate);
  }

  @Override
  public void debug(String message, @Nullable Throwable t) {
    if (report(t)) {
      super.debug(message, t);
    }
  }

  @Override
  public void info(String message, @Nullable Throwable t) {
    if (report(t)) {
      super.info(message, t);
    }
  }

  @Override
  public void warn(String message, @Nullable Throwable t) {
    if (report(t)) {
      super.warn(message, t);
    }
  }

  private boolean report(@Nullable Throwable t) {
    if (t instanceof SvnBindException) {
      for (int i = 0; i < ourErrorsToFilter.length; i++) {
        ErrorCode key = ourErrorsToFilter[i];
        if (((SvnBindException)t).contains(key)) {
          long now = System.nanoTime();
          if (myExpirationTime[i] > now) {
            return false;
          }
          myExpirationTime[i] = now + EXPIRATION;
          break;
        }
      }
    }
    return true;
  }
}
