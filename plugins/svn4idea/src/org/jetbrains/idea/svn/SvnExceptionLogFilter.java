/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn;

import com.intellij.idea.RareLogger;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.ContainerUtil;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;

import java.util.Set;

/**
 * @author Konstantin Kolosovsky.
 */
@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
public class SvnExceptionLogFilter implements RareLogger.LogFilter {

  private static final int ourLogUsualInterval = 20 * 1000;
  private static final int ourLogRareInterval = 30 * 1000;

  private static final Set<SVNErrorCode> ourLogRarelyCodes = ContainerUtil
    .newHashSet(SVNErrorCode.WC_UNSUPPORTED_FORMAT, SVNErrorCode.WC_CORRUPT, SVNErrorCode.WC_CORRUPT_TEXT_BASE, SVNErrorCode.WC_NOT_FILE,
                SVNErrorCode.WC_NOT_DIRECTORY, SVNErrorCode.WC_PATH_NOT_FOUND);

  @Override
  public Object getKey(@NotNull Level level, @NonNls String message, @Nullable Throwable t, @NonNls String... details) {
    SVNException e = getSvnException(t);
    boolean shouldFilter = e != null && ourLogRarelyCodes.contains(e.getErrorMessage().getErrorCode());

    return shouldFilter ? e.getErrorMessage().getErrorCode() : null;
  }

  @Override
  @NotNull
  public Integer getAllowedLoggingInterval(Level level, String message, Throwable t, String[] details) {
    SVNException e = getSvnException(t);
    boolean shouldFilter = e != null && ourLogRarelyCodes.contains(e.getErrorMessage().getErrorCode());

    return shouldFilter ? ourLogRareInterval : ourLogUsualInterval;
  }

  @Nullable
  private static SVNException getSvnException(@Nullable Throwable t) {
    SVNException result = null;
    if (t instanceof SVNException) {
      result = (SVNException)t;
    }
    else if (t instanceof VcsException && t.getCause() instanceof SVNException) {
      result = (SVNException)t.getCause();
    }
    return result;
  }
}
