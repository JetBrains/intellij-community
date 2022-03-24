// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class TerminalOptionsConfigurable implements SearchableConfigurable, Disposable {
  private static final String TERMINAL_SETTINGS_HELP_REFERENCE = "reference.settings.terminal";

  private TerminalSettingsPanel myPanel;
  private final Project myProject;
  private final TerminalOptionsProvider myOptionsProvider;
  private final TerminalProjectOptionsProvider myProjectOptionsProvider;

  public TerminalOptionsConfigurable(@NotNull Project project) {
    myProject = project;
    myOptionsProvider = TerminalOptionsProvider.getInstance();
    myProjectOptionsProvider = TerminalProjectOptionsProvider.getInstance(project);
  }

  @NotNull
  @Override
  public String getId() {
    return "terminal";
  }

  @Nls
  @Override
  public String getDisplayName() {
    return IdeBundle.message("configurable.TerminalOptionsConfigurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return TERMINAL_SETTINGS_HELP_REFERENCE;
  }

  @Override
  public JComponent createComponent() {
    myPanel = new TerminalSettingsPanel();
    return myPanel.createPanel(myProject, myOptionsProvider, myProjectOptionsProvider);
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  @Override
  public void reset() {
    myPanel.reset();
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(this);
  }

  @Override
  public void dispose() {
    myPanel = null;
  }
}
