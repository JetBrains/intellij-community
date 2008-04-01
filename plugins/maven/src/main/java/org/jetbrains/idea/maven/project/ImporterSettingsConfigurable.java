package org.jetbrains.idea.maven.project;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.maven.model.Model;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.IdeaAPIHelper;
import org.jetbrains.idea.maven.core.util.ProjectUtil;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

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
  private MavenProjectsManager myProjectsManager;

  private JPanel panel;
  private ImporterSettingsForm mySettingsForm;
  private ElementsChooser<String> profileChooser;

  private List<String> myOriginalProfiles;

  public ImporterSettingsConfigurable(MavenImporterSettings importerSettings,
                                      MavenImporterState importerState,
                                      MavenProjectsManager projectsManager) {
    myImporterSettings = importerSettings;
    myImporterState = importerState;
    myProjectsManager = projectsManager;
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
      collectVisibleProjects(myProjectsManager, new TreeSet<VirtualFile>(ProjectUtil.ourProjectDirComparator));

    IdeaAPIHelper.addElements(profileChooser, collectProfiles(myProjectsManager, files, new TreeSet<String>()), myOriginalProfiles);
  }

  public void disposeUIResources() {
  }

  private static Collection<VirtualFile> collectVisibleProjects(final MavenProjectsManager projectsManager,
                                                                final Collection<VirtualFile> files) {
    for (VirtualFile file : projectsManager.getFiles()) {
      if (!projectsManager.isIgnored(file)) {
        files.add(file);
      }
    }
    return files;
  }

  private static Set<String> collectProfiles(final MavenProjectsManager projectsManager,
                                             final Collection<VirtualFile> files,
                                             final Set<String> profiles) {
    for (VirtualFile file : files) {
      Model model = projectsManager.getModel(file);
      ProjectUtil.collectProfileIds(model, profiles);
    }
    return profiles;
  }
}