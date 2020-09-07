// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.Disposer;
import org.intellij.plugins.markdown.MarkdownBundle;
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class MarkdownSettingsConfigurable implements SearchableConfigurable {
  @Nullable
  private MarkdownSettingsForm myForm = null;
  @NotNull
  private final MarkdownApplicationSettings myMarkdownApplicationSettings;

  public MarkdownSettingsConfigurable() {
    myMarkdownApplicationSettings = MarkdownApplicationSettings.getInstance();
  }

  @NotNull
  @Override
  public String getId() {
    return "Settings.Markdown";
  }

  @Nls
  @Override
  public String getDisplayName() {
    return MarkdownBundle.message("markdown.settings.name");
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    MarkdownSettingsForm form = getForm();
    if (form == null) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(new JLabel(MarkdownBundle.message("markdown.settings.no.providers")), BorderLayout.NORTH);
      return panel;
    }
    return form.getComponent();
  }

  @Nullable
  public MarkdownSettingsForm getForm() {
    if (!MarkdownHtmlPanelProvider.hasAvailableProviders()) {
      return null;
    }

    if (myForm == null) {
      myForm = new MarkdownSettingsForm();
    }
    return myForm;
  }

  @Override
  public boolean isModified() {
    MarkdownSettingsForm form = getForm();
    if (form == null) {
      return false;
    }
    return !form.getMarkdownCssSettings().equals(myMarkdownApplicationSettings.getMarkdownCssSettings()) ||
           !form.getMarkdownPreviewSettings().equals(myMarkdownApplicationSettings.getMarkdownPreviewSettings()) ||
           !form.getExtensionsEnabledState().equals(myMarkdownApplicationSettings.getExtensionsEnabledState()) ||
           form.isDisableInjections() != myMarkdownApplicationSettings.isDisableInjections() ||
           form.isHideErrors() != myMarkdownApplicationSettings.isHideErrors();
  }

  @Override
  public void apply() throws ConfigurationException {
    final MarkdownSettingsForm form = getForm();
    if (form == null) {
      return;
    }

    form.validate();

    myMarkdownApplicationSettings.setMarkdownCssSettings(form.getMarkdownCssSettings());
    myMarkdownApplicationSettings.setMarkdownPreviewSettings(form.getMarkdownPreviewSettings());
    myMarkdownApplicationSettings.setDisableInjections(form.isDisableInjections());
    myMarkdownApplicationSettings.setHideErrors(form.isHideErrors());
    myMarkdownApplicationSettings.setExtensionsEnabledState(form.getExtensionsEnabledState());

    ApplicationManager.getApplication().getMessageBus().syncPublisher(MarkdownApplicationSettings.SettingsChangedListener.TOPIC)
      .settingsChanged(myMarkdownApplicationSettings);
  }

  @Override
  public void reset() {
    MarkdownSettingsForm form = getForm();
    if (form == null) {
      return;
    }
    form.setMarkdownCssSettings(myMarkdownApplicationSettings.getMarkdownCssSettings());
    form.setMarkdownPreviewSettings(myMarkdownApplicationSettings.getMarkdownPreviewSettings());
    form.setDisableInjections(myMarkdownApplicationSettings.isDisableInjections());
    form.setHideErrors(myMarkdownApplicationSettings.isHideErrors());
    form.setExtensionsEnabledState(myMarkdownApplicationSettings.getExtensionsEnabledState());
  }

  @Override
  public void disposeUIResources() {
    if (myForm != null) {
      Disposer.dispose(myForm);
    }
    myForm = null;
  }

  @Override
  public @NotNull String getHelpTopic() {
    return "Settings.Markdown";
  }
}
