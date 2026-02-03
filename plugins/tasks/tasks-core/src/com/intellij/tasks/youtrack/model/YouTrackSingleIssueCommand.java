// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.youtrack.model;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @noinspection FieldMayBeFinal, FieldCanBeLocal, unused
 */
public class YouTrackSingleIssueCommand {
  private final String query;
  private final int caret;
  private final List<IssueDescriptor> issues;

  public YouTrackSingleIssueCommand(@NotNull String issueId, @NotNull String command) {
    query = command;
    caret = query.length();
    issues = Collections.singletonList(new IssueDescriptor(issueId));
  }

  private static class IssueDescriptor {
    String idReadable;

    IssueDescriptor(@NotNull String id) {
      idReadable = id;
    }
  }
}
