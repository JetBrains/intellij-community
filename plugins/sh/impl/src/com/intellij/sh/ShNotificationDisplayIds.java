// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh;

import com.intellij.notification.impl.NotificationIdsHolder;

import java.util.List;

public class ShNotificationDisplayIds implements NotificationIdsHolder {
  public static final String INSTALL_FORMATTER = "sh.install.formatter";
  public static final String INSTALL_FORMATTER_SUCCESS = "sh.install.formatter.success";
  public static final String INSTALL_FORMATTER_ERROR = "sh.install.formatter.error";
  public static final String UPDATE_FORMATTER = "sh.update.formatter";
  public static final String UPDATE_FORMATTER_SUCCESS = "sh.update.formatter.success";
  public static final String UPDATE_FORMATTER_ERROR = "sh.update.formatter.error";
  public static final String UPDATE_SHELLCHECK = "sh.update.shellcheck";
  public static final String UPDATE_SHELLCHECK_SUCCESS = "sh.update.shellcheck.success";
  public static final String UPDATE_SHELLCHECK_ERROR = "sh.update.shellcheck.error";

  @Override
  public List<String> getNotificationIds() {
    return List.of(INSTALL_FORMATTER, INSTALL_FORMATTER_SUCCESS, INSTALL_FORMATTER_ERROR,
                   UPDATE_FORMATTER, UPDATE_FORMATTER_SUCCESS, UPDATE_FORMATTER_ERROR,
                   UPDATE_SHELLCHECK, UPDATE_SHELLCHECK_SUCCESS, UPDATE_SHELLCHECK_ERROR);
  }
}
