// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.statistics;

import com.intellij.notification.impl.NotificationIdsHolder;

import java.util.List;

public class MavenNotificationDisplayIds implements NotificationIdsHolder {

  public static final String FIRST_IMPORT_NOTIFICATION = "maven.workspace.first.import.notification";
  public static final String WORKSPACE_EXTERNAL_STORAGE = "maven.workspace.external.storage.notification";
  public static final String WRAPPER_DOWNLOADING_ERROR = "maven.wrapper.downloading.error.notification";
  public static final String WRAPPER_FILE_NOT_FOUND = "maven.wrapper.file.not.found.notification";
  public static final String WRAPPER_EMPTY_URL = "maven.wrapper.empty.url.notification";
  public static final String WRAPPER_INFORMATION = "maven.wrapper.information.notification";

  @Override
  public List<String> getNotificationIds() {
    return List.of(
      FIRST_IMPORT_NOTIFICATION,
      WORKSPACE_EXTERNAL_STORAGE,
      WRAPPER_DOWNLOADING_ERROR,
      WRAPPER_FILE_NOT_FOUND,
      WRAPPER_EMPTY_URL,
      WRAPPER_INFORMATION
    );
  }
}
