// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  public @Nls String getDisplayName() {
    return MavenConfigurableBundle.message("maven.settings.ignored.title");
  }

  @Override
  public @Nullable @NonNls String getHelpTopic() {
    return "reference.settings.project.maven.ignored.files";
  }

  @Override
  public @NotNull String getId() {
    return getHelpTopic();
  }
}
