/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.LineSeparator;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;

import java.util.Map;
import java.util.regex.Matcher;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/25/13
 * Time: 5:57 PM
 *
 * Marker exception
 */
public class SvnBindException extends VcsException {

  private Map<Integer, String> errors = new HashMap<Integer, String>();

  public SvnBindException(String message) {
    super(message);

    if (!StringUtil.isEmpty(message)) {
      parseErrors(message);
    }
  }

  public SvnBindException(Throwable throwable) {
    super(throwable);

    if (throwable instanceof SVNException) {
      SVNException e = (SVNException)throwable;
      int code = e.getErrorMessage().getErrorCode().getCode();

      put(code, e.getMessage());
    }
  }

  public boolean contains(int error) {
    return errors.containsKey(error);
  }

  public boolean contains(@NotNull SVNErrorCode error) {
    return errors.containsKey(error.getCode());
  }

  private void parseErrors(@NotNull String message) {
    Matcher matcher = SvnUtil.ERROR_PATTERN.matcher(message);

    while (matcher.find()) {
      put(Integer.valueOf(matcher.group(2)), matcher.group());
    }
  }

  private void put(int code, @Nullable String message) {
    if (errors.containsKey(code)) {
      if (!StringUtil.isEmpty(message)) {
        errors.put(code, errors.get(code) + LineSeparator.LF.getSeparatorString() + message);
      }
    }
    else {
      errors.put(code, message);
    }
  }
}
