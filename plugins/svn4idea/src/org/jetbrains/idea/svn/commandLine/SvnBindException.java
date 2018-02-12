// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.api.ErrorCategory;
import org.jetbrains.idea.svn.api.ErrorCode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.util.ObjectUtils.chooseNotNull;
import static org.jetbrains.idea.svn.api.ErrorCategory.categoryCodeOf;

public class SvnBindException extends VcsException {

  public static final String ERROR_MESSAGE_FORMAT = "svn: E%d: %s";

  @NotNull private final MultiMap<Integer, String> errors = MultiMap.create();
  @NotNull private final MultiMap<Integer, String> warnings = MultiMap.create();

  public SvnBindException(@NotNull ErrorCode code, @NotNull String message) {
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
    super(chooseNotNull(message, getMessage(cause)), cause);

    init(message);
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

  public boolean contains(@NotNull ErrorCode code) {
    return contains(code.getCode());
  }

  public boolean containsCategory(ErrorCategory category) {
    Condition<Integer> belongsToCategoryCondition = errorCode -> categoryCodeOf(errorCode) == category.getCode();

    return ContainerUtil.exists(errors.keySet(), belongsToCategoryCondition) ||
           ContainerUtil.exists(warnings.keySet(), belongsToCategoryCondition);
  }

  private static void parse(@NotNull String message, @NotNull Pattern pattern, @NotNull MultiMap<Integer, String> map) {
    Matcher matcher = pattern.matcher(message);

    while (matcher.find()) {
      map.putValue(Integer.valueOf(matcher.group(2)), matcher.group());
    }
  }
}
