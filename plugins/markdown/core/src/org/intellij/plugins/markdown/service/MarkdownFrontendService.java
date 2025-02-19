// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.platform.project.ProjectId;
import org.intellij.plugins.markdown.dto.MarkdownHeaderInfo;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Collection;

public interface MarkdownFrontendService {
  void openFile(ProjectId projectId, @NotNull URI uri);
  Collection<MarkdownHeaderInfo> collectHeaders(ProjectId projectId, @NotNull URI uri);
  Project guessProjectForUri(URI uri);
  void navigateToHeader(ProjectId projectId, MarkdownHeaderInfo headerInfo);

  static MarkdownFrontendService getInstance() {
    return ApplicationManager.getApplication().getService(MarkdownFrontendService.class);
  }
}
