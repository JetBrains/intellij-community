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
import com.intellij.codeInsight.lookup.LookupElement;
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
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenPluginInfo;
import org.jetbrains.idea.maven.utils.Strings;

import javax.swing.*;
import java.util.*;

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

  public MavenRunnerParametersConfigurable(@NotNull Project project) {
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
      MyCompletionProvider profilesCompletionProvider = new MyCompletionProvider(project) {
        @NotNull
        @Override
        protected String getPrefix(@NotNull String currentTextPrefix) {
          String prefix = super.getPrefix(currentTextPrefix);
          if (prefix.startsWith("-") || prefix.startsWith("!")) {
            prefix = prefix.substring(1);
          }
          return prefix;
        }

        @Override
        protected void addVariants(@NotNull CompletionResultSet result, MavenProjectsManager manager) {
          for (String profile : manager.getAvailableProfiles()) {
            result.addElement(LookupElementBuilder.create(profile));
          }
        }
      };

      profilesComponent.setComponent(profilesCompletionProvider.createEditor(project));

      MyCompletionProvider goalsCompletionProvider = new MyCompletionProvider(project) {

        private volatile List<LookupElement> myCachedElements;

        @Override
        protected void addVariants(@NotNull CompletionResultSet result, MavenProjectsManager manager) {
          List<LookupElement> cachedElements = myCachedElements;
          if (cachedElements == null) {
            Set<String> goals = new HashSet<String>();
            goals.addAll(MavenConstants.PHASES);

            for (MavenProject mavenProject : manager.getProjects()) {
              for (MavenPlugin plugin : mavenProject.getPlugins()) {
                MavenPluginInfo pluginInfo = MavenArtifactUtil.readPluginInfo(manager.getLocalRepository(), plugin.getMavenId());
                if (pluginInfo != null) {
                  for (MavenPluginInfo.Mojo mojo : pluginInfo.getMojos()) {
                    goals.add(mojo.getDisplayName());
                  }
                }
              }
            }

            cachedElements = new ArrayList<LookupElement>(goals.size());
            for (String goal : goals) {
              cachedElements.add(LookupElementBuilder.create(goal).withIcon(icons.MavenIcons.Phase));
            }

            myCachedElements = cachedElements;
          }

          result.addAllElements(cachedElements);
        }
      };

      goalsComponent.setComponent(goalsCompletionProvider.createEditor(project));
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
  
  private abstract class MyCompletionProvider extends TextFieldCompletionProvider {
    private final Project myProject;

    protected MyCompletionProvider(Project project) {
      myProject = project;
    }

    @NotNull
    @Override
    protected String getPrefix(@NotNull String currentTextPrefix) {
      return currentTextPrefix.substring(currentTextPrefix.lastIndexOf(' ') + 1);
    }
    
    @Override
    protected final void addCompletionVariants(@NotNull String text, int offset, @NotNull String prefix, @NotNull CompletionResultSet result) {
      MavenProjectsManager manager = MavenProjectsManager.getInstance(myProject);
      addVariants(result, manager);
    }
    
    protected abstract void addVariants(@NotNull CompletionResultSet result, MavenProjectsManager manager);
  }
  
}
