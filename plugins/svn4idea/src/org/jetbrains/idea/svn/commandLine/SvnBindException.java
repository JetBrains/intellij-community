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

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/25/13
 * Time: 5:57 PM
 *
 * Marker exception
 */
public class SvnBindException extends VcsException {

  public static final int ERROR_BASE = 120000;
  public static final int CATEGORY_SIZE = 5000;

  public static final String ERROR_MESSAGE_FORMAT = "svn: E%d: %s";

  @NotNull private final MultiMap<Integer, String> errors = MultiMap.create();
  @NotNull private final MultiMap<Integer, String> warnings = MultiMap.create();

  public SvnBindException(@NotNull SVNErrorCode code, @NotNull String message) {
    super(String.format(ERROR_MESSAGE_FORMAT, code.getCode(), message));
    errors.putValue(code.getCode(), getMessage());
  }

  public SvnBindException(@Nullable String message) {
    this(message, null);
  }

  public SvnBindException(@Nullable Throwable cause) {
    this(null, cause);
  }

  public SvnBindException(@Nullable String message, @Nullable Throwable cause) {
    super(ObjectUtils.chooseNotNull(message, getMessage(cause)), cause);

    init(message);
    init(cause);
  }

  private void init(@Nullable Throwable throwable) {
    if (throwable instanceof SVNException) {
      SVNException e = (SVNException)throwable;
      int code = e.getErrorMessage().getErrorCode().getCode();
      int type = e.getErrorMessage().getType();

      (type == SVNErrorMessage.TYPE_ERROR ? errors : warnings).putValue(code, e.getMessage());
    }
  }

  private void init(@Nullable String message) {
    if (!StringUtil.isEmpty(message)) {
      parse(message, SvnUtil.ERROR_PATTERN, errors);
      parse(message, SvnUtil.WARNING_PATTERN, warnings);
    }
  }

  public boolean contains(int code) {
    return errors.containsKey(code) || warnings.containsKey(code);
  }

  public boolean contains(@NotNull SVNErrorCode code) {
    return contains(code.getCode());
  }

  public boolean containsCategory(int category) {
    final int categoryCode = getCategoryCode(category);
    Condition<Integer> belongsToCategoryCondition = new Condition<Integer>() {
      @Override
      public boolean value(Integer code) {
        return getCategoryCode(code) == categoryCode;
      }
    };

    return ContainerUtil.exists(errors.keySet(), belongsToCategoryCondition) ||
           ContainerUtil.exists(warnings.keySet(), belongsToCategoryCondition);
  }

  private static int getCategoryCode(int category) {
    return (category - ERROR_BASE) / CATEGORY_SIZE;
  }

  private static void parse(@NotNull String message, @NotNull Pattern pattern, @NotNull MultiMap<Integer, String> map) {
    Matcher matcher = pattern.matcher(message);

    while (matcher.find()) {
      map.putValue(Integer.valueOf(matcher.group(2)), matcher.group());
    }
  }
}
