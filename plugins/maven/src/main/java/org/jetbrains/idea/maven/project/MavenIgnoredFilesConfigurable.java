/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenUIUtil;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.Strings;

import javax.swing.*;
import java.util.Collection;
import java.util.Comparator;

public class MavenIgnoredFilesConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private static final char SEPARATOR = ',';

  private final MavenProjectsManager myManager;

  private Collection<String> myOriginallyIgnoredFilesPaths;
  private String myOriginallyIgnoredFilesPatterns;

  private JPanel myMainPanel;
  private ElementsChooser<String> myIgnoredFilesPathsChooser;
  private JTextArea myIgnoredFilesPattersEditor;

  public MavenIgnoredFilesConfigurable(Project project) {
    myManager = MavenProjectsManager.getInstance(project);
  }

  private void createUIComponents() {
    myIgnoredFilesPathsChooser = new ElementsChooser<>(true);
    myIgnoredFilesPathsChooser.getEmptyText().setText(ProjectBundle.message("maven.ingored.no.file"));
  }

  public JComponent createComponent() {
    return myMainPanel;
  }

  public void disposeUIResources() {
  }

  public boolean isModified() {
    return !MavenUtil.equalAsSets(myOriginallyIgnoredFilesPaths, myIgnoredFilesPathsChooser.getMarkedElements()) ||
           !myOriginallyIgnoredFilesPatterns.equals(myIgnoredFilesPattersEditor.getText());
  }

  public void apply() throws ConfigurationException {
    myManager.setIgnoredFilesPaths(myIgnoredFilesPathsChooser.getMarkedElements());
    myManager.setIgnoredFilesPatterns(Strings.tokenize(myIgnoredFilesPattersEditor.getText(), Strings.WHITESPACE + SEPARATOR));
  }

  public void reset() {
    myOriginallyIgnoredFilesPaths = myManager.getIgnoredFilesPaths();
    myOriginallyIgnoredFilesPatterns = Strings.detokenize(myManager.getIgnoredFilesPatterns(), SEPARATOR);

    MavenUIUtil.setElements(myIgnoredFilesPathsChooser,
                            MavenUtil.collectPaths(myManager.getProjectsFiles()),
                            myOriginallyIgnoredFilesPaths,
                            (o1, o2) -> FileUtil.comparePaths(o1, o2));
    myIgnoredFilesPattersEditor.setText(myOriginallyIgnoredFilesPatterns);
  }

  @Nls
  public String getDisplayName() {
    return ProjectBundle.message("maven.tab.ignored.files");
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settings.project.maven.ignored.files";
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }
}
