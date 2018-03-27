// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.idea.RareLogger;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.ErrorCode;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import java.util.Set;

import static com.intellij.util.ObjectUtils.tryCast;
import static com.intellij.util.containers.ContainerUtil.*;
import static org.jetbrains.idea.svn.api.ErrorCode.*;

public class SvnExceptionLogFilter implements RareLogger.LogFilter {

  private static final int ourLogUsualInterval = 20 * 1000;
  private static final int ourLogRareInterval = 30 * 1000;

  private static final Set<ErrorCode> ourLogRarelyCodes =
    newHashSet(WC_UNSUPPORTED_FORMAT, WC_CORRUPT, WC_CORRUPT_TEXT_BASE, WC_NOT_FILE, WC_NOT_WORKING_COPY, WC_PATH_NOT_FOUND);

  @Override
  public Object getKey(@NotNull Level level, @NonNls String message, @Nullable Throwable t, @NonNls String... details) {
    SvnBindException e = tryCast(t, SvnBindException.class);

    return e != null ? find(ourLogRarelyCodes, e::contains) : null;
  }

  @Override
  @NotNull
  public Integer getAllowedLoggingInterval(Level level, String message, Throwable t, String[] details) {
    SvnBindException e = tryCast(t, SvnBindException.class);

    return e != null && exists(ourLogRarelyCodes, e::contains) ? ourLogRareInterval : ourLogUsualInterval;
  }
}
