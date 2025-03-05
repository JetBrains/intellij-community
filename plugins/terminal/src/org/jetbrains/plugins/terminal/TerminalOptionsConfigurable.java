// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.block.BlockTerminalOptions;

import javax.swing.*;

public final class TerminalOptionsConfigurable implements SearchableConfigurable, Disposable {
  private static final String TERMINAL_SETTINGS_HELP_REFERENCE = "reference.settings.terminal";

  private TerminalSettingsPanel myPanel;
  private final Project myProject;
  private final TerminalOptionsProvider myOptionsProvider;
  private final TerminalProjectOptionsProvider myProjectOptionsProvider;
  private final BlockTerminalOptions myBlockTerminalOptions;

  public TerminalOptionsConfigurable(@NotNull Project project) {
    myProject = project;
    myOptionsProvider = TerminalOptionsProvider.getInstance();
    myProjectOptionsProvider = TerminalProjectOptionsProvider.getInstance(project);
    myBlockTerminalOptions = BlockTerminalOptions.getInstance();
  }

  @Override
  public @NotNull String getId() {
    return "terminal";
  }

  @Override
  public @Nls String getDisplayName() {
    return IdeBundle.message("configurable.TerminalOptionsConfigurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return TERMINAL_SETTINGS_HELP_REFERENCE;
  }

  @Override
  public JComponent createComponent() {
    myPanel = new TerminalSettingsPanel();
    return myPanel.createPanel(myProject, myOptionsProvider, myProjectOptionsProvider, myBlockTerminalOptions);
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
