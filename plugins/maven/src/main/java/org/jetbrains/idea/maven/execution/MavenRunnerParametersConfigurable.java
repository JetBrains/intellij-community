/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.execution;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.TextFieldCompletionProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.Strings;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class MavenRunnerParametersConfigurable implements Configurable, PanelWithAnchor {
  private JPanel panel;
  protected LabeledComponent<TextFieldWithBrowseButton> workingDirComponent;
  protected LabeledComponent<EditorTextField> goalsComponent;
  private LabeledComponent<EditorTextField> profilesComponent;
  private JBLabel myFakeLabel;
  private JComponent anchor;

  public MavenRunnerParametersConfigurable(@NotNull final Project project) {
    workingDirComponent.getComponent().addBrowseFolderListener(
      RunnerBundle.message("maven.select.maven.project.file"), "", project,
      new FileChooserDescriptor(false, true, false, false, false, false) {
        @Override
        public boolean isFileSelectable(VirtualFile file) {
          if (!super.isFileSelectable(file)) return false;
          return file.findChild(MavenConstants.POM_XML) != null;
        }
      });

    if (!project.isDefault()) {
      TextFieldCompletionProvider profilesCompletionProvider = new TextFieldCompletionProvider(true) {
        @Override
        protected final void addCompletionVariants(@NotNull String text, int offset, @NotNull String prefix, @NotNull CompletionResultSet result) {
          MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
          for (String profile : manager.getAvailableProfiles()) {
            result.addElement(LookupElementBuilder.create(profile));
          }
        }

        @NotNull
        @Override
        protected String getPrefix(@NotNull String currentTextPrefix) {
          String prefix = super.getPrefix(currentTextPrefix);
          if (prefix.startsWith("-") || prefix.startsWith("!")) {
            prefix = prefix.substring(1);
          }
          return prefix;
        }
      };

      profilesComponent.setComponent(profilesCompletionProvider.createEditor(project));

      goalsComponent.setComponent(new MavenArgumentsCompletionProvider(project).createEditor(project));
    }

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

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  public boolean isModified() {
    MavenRunnerParameters formParameters = new MavenRunnerParameters();
    setData(formParameters);
    return !formParameters.equals(getParameters());
  }

  public void apply() throws ConfigurationException {
    setData(getParameters());
  }

  public void reset() {
    getData(getParameters());
  }

  private void setData(final MavenRunnerParameters data) {
    data.setWorkingDirPath(workingDirComponent.getComponent().getText());
    data.setGoals(Strings.tokenize(goalsComponent.getComponent().getText(), " "));

    Map<String, Boolean> profilesMap = new LinkedHashMap<String, Boolean>();

    for (String profile : Strings.tokenize(profilesComponent.getComponent().getText(), " ,;")) {
      Boolean isEnabled = true;
      if (profile.startsWith("-") || profile.startsWith("!")) {
        profile = profile.substring(1);
        if (profile.isEmpty()) continue;

        isEnabled = false;
      }

      profilesMap.put(profile, isEnabled);
    }
    data.setProfilesMap(profilesMap);
  }

  private void getData(final MavenRunnerParameters data) {
    workingDirComponent.getComponent().setText(data.getWorkingDirPath());
    goalsComponent.getComponent().setText(Strings.detokenize(data.getGoals(), ' '));

    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, Boolean> entry : data.getProfilesMap().entrySet()) {
      if (sb.length() != 0) {
        sb.append(" ");
      }
      if (!entry.getValue()) {
        sb.append("-");
      }

      sb.append(entry.getKey());
    }

    profilesComponent.getComponent().setText(sb.toString());
  }

  protected abstract MavenRunnerParameters getParameters();

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
}
