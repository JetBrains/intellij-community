package org.jetbrains.idea.maven.project;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.IdeaAPIHelper;
import org.jetbrains.idea.maven.core.util.ProjectUtil;
import org.jetbrains.idea.maven.state.MavenProjectsState;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Vladislav.Kaznacheev
 */
public class ImporterSettingsConfigurable implements Configurable {
  private MavenImporterSettings myImporterSettings;

  private MavenImporterState myImporterState;
  private MavenProjectsState myProjectsState;

  private JPanel panel;
  private ImporterSettingsForm mySettingsForm;
  private ElementsChooser<String> profileChooser;

  private List<String> myOriginalProfiles;

  public ImporterSettingsConfigurable(MavenImporterSettings importerSettings,
                                      MavenImporterState importerState,
                                      MavenProjectsState projectsState) {
    myImporterSettings = importerSettings;
    myImporterState = importerState;
    myProjectsState = projectsState;
    myOriginalProfiles = myImporterState.getMemorizedProfiles();
  }

  private void createUIComponents() {
    profileChooser = new ElementsChooser<String>(true);
  }

  @Nls
  public String getDisplayName() {
    return ProjectBundle.message("maven.import");
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return panel;
  }

  public boolean isModified() {
    return mySettingsForm.isModified(myImporterSettings) ||
           !IdeaAPIHelper.equalAsSets(myOriginalProfiles, profileChooser.getMarkedElements());
  }

  public void apply() throws ConfigurationException {
    mySettingsForm.getData(myImporterSettings);
    myImporterState.memorizeProfiles(profileChooser.getMarkedElements());
  }

  public void reset() {
    mySettingsForm.setData(myImporterSettings);

    final Collection<VirtualFile> files =
      collectVisibleProjects(myProjectsState, new TreeSet<VirtualFile>(ProjectUtil.ourProjectDirComparator));

    IdeaAPIHelper.addElements(profileChooser, collectProfiles(myProjectsState, files, new TreeSet<String>()), myOriginalProfiles);
  }

  public void disposeUIResources() {
  }

  private static Collection<VirtualFile> collectVisibleProjects(final MavenProjectsState projectsState,
                                                                final Collection<VirtualFile> files) {
    for (VirtualFile file : projectsState.getFiles()) {
      if (!projectsState.isIgnored(file)) {
        files.add(file);
      }
    }
    return files;
  }

  private Collection<String> collectPaths(final Collection<VirtualFile> files, final Collection<String> paths) {
    for (VirtualFile file : files) {
      paths.add(FileUtil.toSystemDependentName(file.getPath()));
    }
    return paths;
  }

  private static Set<String> collectProfiles(final MavenProjectsState projectsState,
                                             final Collection<VirtualFile> files,
                                             final Set<String> profiles) {
    for (VirtualFile file : files) {
      ProjectUtil.collectProfileIds(projectsState.getMavenProject(file), profiles);
    }
    return profiles;
  }
}