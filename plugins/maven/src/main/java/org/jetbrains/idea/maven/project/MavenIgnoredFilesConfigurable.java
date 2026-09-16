// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.MavenUIUtil;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.Strings;

import javax.swing.JComponent;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MavenIgnoredFilesConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private static final char SEPARATOR = ',';

  private final MavenProjectsManager myManager;

  private Collection<String> myOriginallyIgnoredFilesPaths;
  private String myOriginallyIgnoredFilesPatterns;

  private final MavenIgnoredFilesUi ui = new MavenIgnoredFilesUi();

  public MavenIgnoredFilesConfigurable(Project project) {
    myManager = MavenProjectsManager.getInstance(project);
  }

  @Override
  public JComponent createComponent() {
    return ui.panel;
  }

  @Override
  public boolean isModified() {
    return !MavenUtil.equalAsSets(myOriginallyIgnoredFilesPaths, ui.ignoredFilesPathsChooser.getMarkedElements()) ||
           !myOriginallyIgnoredFilesPatterns.equals(ui.ignoredFilesPattersEditor.getText());
  }

  @Override
  public void apply() throws ConfigurationException {
    myManager.setIgnoredFilesPaths(ui.ignoredFilesPathsChooser.getMarkedElements());
    myManager.setIgnoredFilesPatterns(Strings.tokenize(ui.ignoredFilesPattersEditor.getText(), Strings.WHITESPACE + SEPARATOR));
  }

  @Override
  public void reset() {
    myOriginallyIgnoredFilesPaths = myManager.getIgnoredFilesPaths();
    myOriginallyIgnoredFilesPatterns = Strings.detokenize(myManager.getIgnoredFilesPatterns(), SEPARATOR);

    List<VirtualFile> projectsFiles = myManager.isInitialized() ? myManager.getProjectsFiles() : Collections.emptyList();
    MavenUIUtil.setElements(ui.ignoredFilesPathsChooser,
                            MavenUtil.collectPaths(projectsFiles),
                            myOriginallyIgnoredFilesPaths,
                            (o1, o2) -> FileUtil.comparePaths(o1, o2));
    ui.ignoredFilesPattersEditor.setText(myOriginallyIgnoredFilesPatterns);
  }

  @Override
  public @Nls String getDisplayName() {
    return MavenConfigurableBundle.message("maven.settings.ignored.title");
  }

  @Override
  public @NotNull @NonNls String getHelpTopic() {
    return "reference.settings.project.maven.ignored.files";
  }

  @Override
  public @NotNull String getId() {
    return getHelpTopic();
  }
}
