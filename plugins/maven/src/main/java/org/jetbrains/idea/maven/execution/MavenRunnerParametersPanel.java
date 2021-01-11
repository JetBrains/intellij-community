// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.externalSystem.service.execution.cmd.ParametersListLexer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.TextFieldCompletionProvider;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenConfigurableBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenRunnerParametersPanel implements PanelWithAnchor, MavenSettingsObservable {
  private JPanel panel;
  protected LabeledComponent<TextFieldWithBrowseButton> workingDirComponent;
  protected LabeledComponent<EditorTextField> goalsComponent;
  private LabeledComponent<EditorTextField> profilesComponent;
  private JBLabel myFakeLabel;
  private JCheckBox myResolveToWorkspaceCheckBox;
  private FixedSizeButton showProjectTreeButton;
  private JComponent anchor;

  public MavenRunnerParametersPanel(@NotNull final Project project) {

    workingDirComponent.getComponent().addBrowseFolderListener(
      RunnerBundle.message("maven.select.working.directory"), "", project,
      new MavenPomFileChooserDescriptor(project));

    if (!project.isDefault()) {
      TextFieldCompletionProvider profilesCompletionProvider = new TextFieldCompletionProvider(true) {
        @Override
        protected final void addCompletionVariants(@NotNull String text, int offset, @NotNull String prefix, @NotNull CompletionResultSet result) {
          MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
          for (String profile : manager.getAvailableProfiles()) {
            result.addElement(LookupElementBuilder.create(ParametersListUtil.join(profile)));
          }
        }

        @NotNull
        @Override
        protected String getPrefix(@NotNull String currentTextPrefix) {
          ParametersListLexer lexer = new ParametersListLexer(currentTextPrefix);
          while (lexer.nextToken()) {
            if (lexer.getTokenEnd() == currentTextPrefix.length()) {
              String prefix = lexer.getCurrentToken();
              if (prefix.startsWith("-") || prefix.startsWith("!")) {
                prefix = prefix.substring(1);
              }
              return prefix;
            }
          }

          return "";
        }
      };

      profilesComponent.setComponent(profilesCompletionProvider.createEditor(project));

      goalsComponent.setComponent(new MavenArgumentsCompletionProvider(project).createEditor(project));
    }

    showProjectTreeButton.setIcon(AllIcons.Nodes.Module);

    MavenSelectProjectPopup.attachToWorkingDirectoryField(MavenProjectsManager.getInstance(project),
                                                          workingDirComponent.getComponent().getTextField(),
                                                          showProjectTreeButton,
                                                          goalsComponent.getComponent());

    setAnchor(profilesComponent.getLabel());
  }

  public JComponent createComponent() {
    return panel;
  }

  public void disposeUIResources() {
  }

  public String getDisplayName() {
    return RunnerBundle.message("maven.runner.parameters.title");
  }

  protected void setData(final MavenRunnerParameters data) {
    data.setWorkingDirPath(workingDirComponent.getComponent().getText());

    List<String> commandLine = ParametersListUtil.parse(goalsComponent.getComponent().getText());
    int pomFileNameIndex = 1 + commandLine.indexOf("-f");
    if (pomFileNameIndex != 0) {
      if (pomFileNameIndex < commandLine.size()) {
        data.setPomFileName(commandLine.remove(pomFileNameIndex));
      }
      commandLine.remove(pomFileNameIndex - 1);
    }

    data.setGoals(commandLine);
    data.setResolveToWorkspace(myResolveToWorkspaceCheckBox.isEnabled() && myResolveToWorkspaceCheckBox.isSelected());

    Map<String, Boolean> profilesMap = new LinkedHashMap<>();

    List<String> profiles = ParametersListUtil.parse(profilesComponent.getComponent().getText());

    for (String profile : profiles) {
      boolean isEnabled = true;
      if (profile.startsWith("-") || profile.startsWith("!")) {
        profile = profile.substring(1);
        if (profile.isEmpty()) continue;

        isEnabled = false;
      }

      profilesMap.put(profile, isEnabled);
    }
    data.setProfilesMap(profilesMap);
  }

  protected void getData(final MavenRunnerParameters data) {
    workingDirComponent.getComponent().setText(data.getWorkingDirPath());
    String commandLine = ParametersList.join(data.getGoals());
    if (data.getPomFileName() != null) {
      commandLine += " -f " + data.getPomFileName();
    }
    goalsComponent.getComponent().setText(commandLine);
    myResolveToWorkspaceCheckBox.setSelected(data.isResolveToWorkspace());

    ParametersList parametersList = new ParametersList();

    for (Map.Entry<String, Boolean> entry : data.getProfilesMap().entrySet()) {
      String profileName = entry.getKey();

      if (!entry.getValue()) {
        profileName = '-' + profileName;
      }

      parametersList.add(profileName);
    }

    profilesComponent.getComponent().setText(parametersList.getParametersString());
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    workingDirComponent.setAnchor(anchor);
    goalsComponent.setAnchor(anchor);
    profilesComponent.setAnchor(anchor);
    myFakeLabel.setAnchor(anchor);
  }

  @ApiStatus.Internal
  void applyTargetEnvironmentConfiguration(@Nullable String targetName) {
    boolean localTarget = targetName == null;
    myResolveToWorkspaceCheckBox.setEnabled(localTarget);
    if (!localTarget) {
      myResolveToWorkspaceCheckBox.setSelected(false);
      myResolveToWorkspaceCheckBox.setToolTipText(MavenConfigurableBundle.message("maven.settings.on.targets.runner.resolve.workspace.artifacts.tooltip"));
    } else {
      myResolveToWorkspaceCheckBox.setToolTipText(MavenConfigurableBundle.message("maven.settings.runner.resolve.workspace.artifacts.tooltip"));
    }
  }

  @Override
  public void registerSettingsWatcher(@NotNull MavenRCSettingsWatcher watcher) {
    watcher.registerComponent("workingDir", workingDirComponent);
    watcher.registerComponent("goals", goalsComponent);
    watcher.registerComponent("profiles", profilesComponent);
    watcher.registerComponent("resolveToWorkspace", myResolveToWorkspaceCheckBox);
  }
}
