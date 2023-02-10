// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.treeStructure.SimpleTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector;

import java.awt.event.InputEvent;

import static org.jetbrains.idea.maven.navigator.MavenProjectsNavigator.TOOL_WINDOW_PLACE_ID;

class RunConfigurationNode extends MavenSimpleNode {
  private final RunnerAndConfigurationSettings mySettings;

  RunConfigurationNode(MavenProjectsStructure structure, RunConfigurationsNode parent, RunnerAndConfigurationSettings settings) {
    super(structure, parent);
    mySettings = settings;
    getTemplatePresentation().setIcon(ProgramRunnerUtil.getConfigurationIcon(settings, false));
  }

  public RunnerAndConfigurationSettings getSettings() {
    return mySettings;
  }

  @Override
  public String getName() {
    return mySettings.getName();
  }

  @Override
  protected void doUpdate(@NotNull PresentationData presentation) {
    setNameAndTooltip(presentation,
                      getName(),
                      null,
                      StringUtil.join(((MavenRunConfiguration)mySettings.getConfiguration()).getRunnerParameters().getGoals(), " "));
  }

  @Nullable
  @Override
  String getMenuId() {
    return "Maven.RunConfigurationMenu";
  }

  public void updateRunConfiguration() {

  }

  @Override
  public void handleDoubleClickOrEnter(SimpleTree tree, InputEvent inputEvent) {
    MavenActionsUsagesCollector
      .trigger(myProject, MavenActionsUsagesCollector.EXECUTE_MAVEN_CONFIGURATION, TOOL_WINDOW_PLACE_ID, false, null);
    ProgramRunnerUtil.executeConfiguration(mySettings, DefaultRunExecutor.getRunExecutorInstance());
  }
}
