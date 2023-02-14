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
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenUIUtil;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.Strings;

import javax.swing.*;
import java.util.Collection;

public class MavenIgnoredFilesConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private static final char SEPARATOR = ',';

  private final MavenProjectsManager myManager;

  private Collection<String> myOriginallyIgnoredFilesPaths;
  private String myOriginallyIgnoredFilesPatterns;

  private JPanel myMainPanel;
  private ElementsChooser<String> myIgnoredFilesPathsChooser;
  private JTextArea myIgnoredFilesPattersEditor;
  private JPanel myIgnoredFilesPatternsPanel;
  private JPanel myIgnoredFilesPanel;

  public MavenIgnoredFilesConfigurable(Project project) {
    myManager = MavenProjectsManager.getInstance(project);
    myIgnoredFilesPatternsPanel.setBorder(
      IdeBorderFactory.createTitledBorder(MavenConfigurableBundle.message("maven.settings.ignored.tooltip"), false, JBUI.insetsTop(8)).setShowLine(false));

    myIgnoredFilesPanel.setBorder(
      IdeBorderFactory.createTitledBorder(MavenConfigurableBundle.message("maven.settings.ignored.title"), false, JBUI.insetsTop(8)).setShowLine(false));
  }

  private void createUIComponents() {
    myIgnoredFilesPathsChooser = new ElementsChooser<>(true);
    myIgnoredFilesPathsChooser.getEmptyText().setText(MavenConfigurableBundle.message("maven.settings.ignored.no.file"));
  }

  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    return !MavenUtil.equalAsSets(myOriginallyIgnoredFilesPaths, myIgnoredFilesPathsChooser.getMarkedElements()) ||
           !myOriginallyIgnoredFilesPatterns.equals(myIgnoredFilesPattersEditor.getText());
  }

  @Override
  public void apply() throws ConfigurationException {
    myManager.setIgnoredFilesPaths(myIgnoredFilesPathsChooser.getMarkedElements());
    myManager.setIgnoredFilesPatterns(Strings.tokenize(myIgnoredFilesPattersEditor.getText(), Strings.WHITESPACE + SEPARATOR));
  }

  @Override
  public void reset() {
    myOriginallyIgnoredFilesPaths = myManager.getIgnoredFilesPaths();
    myOriginallyIgnoredFilesPatterns = Strings.detokenize(myManager.getIgnoredFilesPatterns(), SEPARATOR);

    MavenUIUtil.setElements(myIgnoredFilesPathsChooser,
                            MavenUtil.collectPaths(myManager.getProjectsFiles()),
                            myOriginallyIgnoredFilesPaths,
                            (o1, o2) -> FileUtil.comparePaths(o1, o2));
    myIgnoredFilesPattersEditor.setText(myOriginallyIgnoredFilesPatterns);
  }

  @Override
  @Nls
  public String getDisplayName() {
    return MavenConfigurableBundle.message("maven.settings.ignored.title");
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settings.project.maven.ignored.files";
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }
}
