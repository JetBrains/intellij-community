// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.jcef;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.jcef.JBCefApp;
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel;
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelProvider;
import org.jetbrains.annotations.NotNull;

public class JCEFHtmlPanelProvider extends MarkdownHtmlPanelProvider {

  @NotNull
  @Override
  public MarkdownHtmlPanel createHtmlPanel() {
    return new MarkdownJCEFHtmlPanel();
  }

  @Override
  public @NotNull MarkdownHtmlPanel createHtmlPanel(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    return new MarkdownJCEFHtmlPanel(project, virtualFile);
  }

  @NotNull
  @Override
  public AvailabilityInfo isAvailable() {
    return JBCefApp.isSupported() ? AvailabilityInfo.AVAILABLE : AvailabilityInfo.UNAVAILABLE;
  }

  @NotNull
  @Override
  public ProviderInfo getProviderInfo() {
    return new ProviderInfo("JCEF Browser", JCEFHtmlPanelProvider.class.getName());
  }
}
