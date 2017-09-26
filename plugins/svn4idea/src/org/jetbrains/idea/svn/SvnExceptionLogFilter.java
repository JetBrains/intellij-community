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
import com.intellij.util.containers.ContainerUtil;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNErrorCode;

import java.util.Set;

import static com.intellij.util.ObjectUtils.tryCast;
import static com.intellij.util.containers.ContainerUtil.exists;
import static com.intellij.util.containers.ContainerUtil.find;

public class SvnExceptionLogFilter implements RareLogger.LogFilter {

  private static final int ourLogUsualInterval = 20 * 1000;
  private static final int ourLogRareInterval = 30 * 1000;

  private static final Set<SVNErrorCode> ourLogRarelyCodes = ContainerUtil
    .newHashSet(SVNErrorCode.WC_UNSUPPORTED_FORMAT, SVNErrorCode.WC_CORRUPT, SVNErrorCode.WC_CORRUPT_TEXT_BASE, SVNErrorCode.WC_NOT_FILE,
                SVNErrorCode.WC_NOT_DIRECTORY, SVNErrorCode.WC_PATH_NOT_FOUND);

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
