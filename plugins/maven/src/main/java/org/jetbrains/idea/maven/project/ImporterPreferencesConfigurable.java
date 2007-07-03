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
import java.util.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class ImporterPreferencesConfigurable implements Configurable {
  private MavenImporterPreferences myImporterPreferences;
  private MavenImporterState myImporterState;
  private final MavenProjectsState myProjectsState;

  private JPanel panel;
  private ImporterPreferencesForm preferencesForm;
  private ElementsChooser<String> projectChooser;
  private ElementsChooser<String> profileChooser;

  private List<String> myOriginalProjects;
  private List<String> myOriginalProfiles;

  public ImporterPreferencesConfigurable(final MavenImporterPreferences importerPreferences,
                                         final MavenImporterState importerState,
                                         final MavenProjectsState projectsState) {
    myImporterPreferences = importerPreferences;
    myImporterState = importerState;
    myProjectsState = projectsState;

    myOriginalProjects = myImporterState.getRememberedProjects();
    myOriginalProfiles = myImporterState.getRememberedProfiles();
  }

  private void createUIComponents() {
    projectChooser = new ElementsChooser<String>(true);
    //projectChooser.setPreferredSize(new Dimension(-1, -1));
    //projectChooser.setMinimumSize(new Dimension(-1, 80));
    profileChooser = new ElementsChooser<String>(true);
    //profileChooser.setPreferredSize(new Dimension(-1, -1));
    //profileChooser.setMinimumSize(new Dimension(-1, 80));
  }

  @Nls
  public String getDisplayName() {
    return ProjectBundle.message("maven.tab.import");
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
    return preferencesForm.isModified(myImporterPreferences) ||
           !IdeaAPIHelper.equalAsSets(myOriginalProjects, projectChooser.getMarkedElements()) ||
           !IdeaAPIHelper.equalAsSets(myOriginalProfiles, profileChooser.getMarkedElements());
  }

  public void apply() throws ConfigurationException {
    preferencesForm.getData(myImporterPreferences);
    myImporterState.rememberProjects(projectChooser.getMarkedElements());
    myImporterState.rememberProfiles(profileChooser.getMarkedElements());
  }

  public void reset() {
    preferencesForm.setData(myImporterPreferences);

    final Collection<VirtualFile> files =
      collectVisibleProjects(myProjectsState, new TreeSet<VirtualFile>(ProjectUtil.ourProjectDirComparator));

    IdeaAPIHelper.addElements(projectChooser, collectPaths(files, new ArrayList<String>()), myOriginalProjects);
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