// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class MarkdownLAFListener implements LafManagerListener {
  private boolean isLastLAFWasDarcula = StartupUiUtil.isUnderDarcula();

  @Override
  public void lookAndFeelChanged(@NotNull LafManager source) {
    final UIManager.LookAndFeelInfo newLookAndFeel = source.getCurrentLookAndFeel();
    final boolean isNewLookAndFeelDarcula = isDarcula(newLookAndFeel);

    if (isNewLookAndFeelDarcula == isLastLAFWasDarcula) {
      return;
    }

    updateCssSettingsForced(isNewLookAndFeelDarcula);
  }

  public void updateCssSettingsForced(boolean isDarcula) {
    final MarkdownCssSettings currentCssSettings = MarkdownApplicationSettings.getInstance().getMarkdownCssSettings();
    final String stylesheetUri = StringUtil.isEmpty(currentCssSettings.getStylesheetUri())
                       ? MarkdownCssSettings.getDefaultCssSettings(isDarcula).getStylesheetUri()
                       : currentCssSettings.getStylesheetUri();

    MarkdownApplicationSettings.getInstance().setMarkdownCssSettings(new MarkdownCssSettings(
      currentCssSettings.isUriEnabled(),
      stylesheetUri,
      currentCssSettings.isTextEnabled(),
      currentCssSettings.getStylesheetText()
    ));
    isLastLAFWasDarcula = isDarcula;

    ApplicationManager.getApplication().getMessageBus().syncPublisher(MarkdownApplicationSettings.SettingsChangedListener.TOPIC)
      .settingsChanged(MarkdownApplicationSettings.getInstance());
  }

  public static boolean isDarcula(@Nullable UIManager.LookAndFeelInfo laf) {
    if (laf == null) {
      return false;
    }
    return laf.getName().contains("Darcula");
  }
}