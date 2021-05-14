// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import org.intellij.plugins.markdown.extensions.MarkdownCodeFencePluginGeneratingProvider;
import org.jetbrains.annotations.NotNull;

class MarkdownLAFListener implements LafManagerListener {
  @Override
  public void lookAndFeelChanged(@NotNull LafManager source) {
    reinit();
  }

  /**
   * Reinitialize plugin after change in look and feel.
   * <p>
   * For example, it would reinitialize preview and clear caches
   */
  public static void reinit() {
    MarkdownCodeFencePluginGeneratingProvider.Companion.notifyLAFChanged();
    updateCssSettingsForced();
  }

  private static void updateCssSettingsForced() {
    final MarkdownCssSettings currentCssSettings = MarkdownApplicationSettings.getInstance().getMarkdownCssSettings();
    final String stylesheetUri = StringUtil.isEmpty(currentCssSettings.getCustomStylesheetPath())
                                 ? MarkdownCssSettings.DEFAULT.getCustomStylesheetPath()
                                 : currentCssSettings.getCustomStylesheetPath();

    final MarkdownCssSettings newSettings = new MarkdownCssSettings(
      currentCssSettings.isCustomStylesheetEnabled(),
      stylesheetUri,
      currentCssSettings.isTextEnabled(),
      currentCssSettings.getCustomStylesheetText(),
      currentCssSettings.getFontSize(),
      currentCssSettings.getFontFamily());

    MarkdownApplicationSettings.getInstance().setMarkdownCssSettings(newSettings);

    ApplicationManager.getApplication().getMessageBus()
      .syncPublisher(MarkdownApplicationSettings.SettingsChangedListener.TOPIC)
      .settingsChanged(MarkdownApplicationSettings.getInstance());
  }
}
