package org.jetbrains.idea.maven.wizards;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportBuilder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.runner.SoutMavenConsole;
import org.jetbrains.idea.maven.utils.FileFinder;

import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenProjectBuilder extends ProjectImportBuilder<MavenProjectModel> {
  private final static Icon ICON = IconLoader.getIcon("/images/mavenEmblem.png");

  private static class Parameters {
    private Project myProjectToUpdate;

    private MavenGeneralSettings myGeneralSettingsCache;
    private MavenImportingSettings myImportingSettingsCache;
    private MavenDownloadingSettings myDownloadingSettingsCache;

    private VirtualFile myImportRoot;
    private List<VirtualFile> myFiles;
    private List<String> myProfiles = new ArrayList<String>();
    private List<String> mySelectedProfiles = new ArrayList<String>();

    private MavenProjectsTree myMavenProjectTree;

    private boolean myOpenModulesConfigurator;
  }

  private Parameters myParamaters;

  public String getName() {
    return ProjectBundle.message("maven.name");
  }

  public Icon getIcon() {
    return ICON;
  }

  public void cleanup() {
    myParamaters = null;
    super.cleanup();
  }

  private Parameters getParameters() {
    if (myParamaters == null) {
      myParamaters = new Parameters();
    }
    return myParamaters;
  }

  @Override
  public boolean validate(Project current, Project dest) {
    return true;
  }

  public List<Module> commit(final Project project, final ModifiableModuleModel model, final ModulesProvider modulesProvider) {
    MavenWorkspaceSettings settings = project.getComponent(MavenWorkspaceSettingsComponent.class).getState();

    settings.generalSettings = getGeneralSettings();
    settings.importingSettings = getImportingSettings();
    settings.downloadingSettings = getDownloadingSettings();

    MavenProjectsManager manager = MavenProjectsManager.getInstance(project);

    manager.setManagedFiles(getParameters().myFiles);
    manager.setActiveProfiles(getParameters().mySelectedProfiles);
    manager.setImportedMavenProjectModelManager(getParameters().myMavenProjectTree);

    return manager.commit(model, modulesProvider);
  }

  public VirtualFile getRootDirectory() {
    return getImportRoot();
  }

  public boolean setRootDirectory(final String root) throws ConfigurationException {
    getParameters().myFiles = null;
    getParameters().myProfiles.clear();
    getParameters().myMavenProjectTree = null;

    getParameters().myImportRoot = FileFinder.refreshRecursively(root);
    if (getImportRoot() == null) return false;

    return runConfigurationProcess(ProjectBundle.message("maven.scanning.projects"), new MavenProcess.MavenTask() {
      public void run(MavenProcess p) throws MavenProcessCanceledException {
        p.setText(ProjectBundle.message("maven.locating.files"));
        getParameters().myFiles = FileFinder.findPomFiles(getImportRoot().getChildren(),
                                                          getImportingSettings().isLookForNested(),
                                                          p.getIndicator(),
                                                          new ArrayList<VirtualFile>());

        collectProfiles(p);

        if (getParameters().myProfiles.isEmpty()) {
          readMavenProjectTree(p);
        }

        p.setText2("");
      }
    });
  }

  private void collectProfiles(MavenProcess process) {
    getParameters().myProfiles = new ArrayList<String>();

    MavenEmbedderWrapper e = MavenEmbedderFactory.createEmbedderForRead(getGeneralSettings(), new SoutMavenConsole(), process);
    try {
      for (VirtualFile f : getParameters().myFiles) {
        MavenProjectState state = MavenReader.readProject(e, f, new ArrayList<String>(), process).first;
        if (!state.isValid()) continue;

        Set<String> profiles = new LinkedHashSet<String>(state.getProfilesIds());
        if (!profiles.isEmpty()) getParameters().myProfiles.addAll(profiles);
      }
    }
    catch (MavenProcessCanceledException ignore) {
    }
    finally {
      e.release();
    }
  }

  public List<String> getProfiles() {
    return getParameters().myProfiles;
  }

  public List<String> getSelectedProfiles() {
    return getParameters().mySelectedProfiles;
  }

  public boolean setSelectedProfiles(List<String> profiles) throws ConfigurationException {
    getParameters().myMavenProjectTree = null;
    getParameters().mySelectedProfiles = profiles;

    return runConfigurationProcess(ProjectBundle.message("maven.scanning.projects"), new MavenProcess.MavenTask() {
      public void run(MavenProcess p) throws MavenProcessCanceledException {
        readMavenProjectTree(p);
        p.setText2("");
      }
    });
  }

  private boolean runConfigurationProcess(String message, MavenProcess.MavenTask p) throws ConfigurationException {
    try {
      MavenProcess.run(null, message, p);
    }
    catch (MavenProcessCanceledException e) {
      return false;
    }

    return true;
  }

  private void readMavenProjectTree(MavenProcess p) throws MavenProcessCanceledException {
    getParameters().myMavenProjectTree = new MavenProjectsTree();
    getParameters().myMavenProjectTree
      .read(getParameters().myFiles, getParameters().mySelectedProfiles, getGeneralSettings(), new SoutMavenConsole(), p);
  }

  public List<MavenProjectModel> getList() {
    return getParameters().myMavenProjectTree.getRootProjects();
  }

  public boolean isMarked(final MavenProjectModel element) {
    return true;
  }

  public void setList(List<MavenProjectModel> nodes) throws ConfigurationException {
    for (MavenProjectModel node : getParameters().myMavenProjectTree.getRootProjects()) {
      node.setIncluded(nodes.contains(node));
    }
  }

  public boolean isOpenProjectSettingsAfter() {
    return getParameters().myOpenModulesConfigurator;
  }

  public void setOpenProjectSettingsAfter(boolean on) {
    getParameters().myOpenModulesConfigurator = on;
  }

  public MavenGeneralSettings getGeneralSettings() {
    if (getParameters().myGeneralSettingsCache == null) {
      getParameters().myGeneralSettingsCache = getDirectProjectsSettings().generalSettings.clone();
    }
    return getParameters().myGeneralSettingsCache;
  }

  public MavenImportingSettings getImportingSettings() {
    if (getParameters().myImportingSettingsCache == null) {
      getParameters().myImportingSettingsCache = getDirectProjectsSettings().importingSettings.clone();
    }
    return getParameters().myImportingSettingsCache;
  }

  private MavenDownloadingSettings getDownloadingSettings() {
    if (getParameters().myDownloadingSettingsCache == null) {
      getParameters().myDownloadingSettingsCache = getDirectProjectsSettings().downloadingSettings.clone();
    }
    return getParameters().myDownloadingSettingsCache;
  }

  private MavenWorkspaceSettings getDirectProjectsSettings() {
    return getProject().getComponent(MavenWorkspaceSettingsComponent.class).getState();
  }

  private Project getProject() {
    return isUpdate() ? getProjectToUpdate() : ProjectManager.getInstance().getDefaultProject();
  }

  public void setFiles(List<VirtualFile> files) {
    getParameters().myFiles = files;
  }

  @Nullable
  public Project getProjectToUpdate() {
    if (getParameters().myProjectToUpdate == null) {
      getParameters().myProjectToUpdate = getCurrentProject();
    }
    return getParameters().myProjectToUpdate;
  }

  @Nullable
  public VirtualFile getImportRoot() {
    if (getParameters().myImportRoot == null && isUpdate()) {
      final Project project = getProjectToUpdate();
      assert project != null;
      getParameters().myImportRoot = project.getBaseDir();
    }
    return getParameters().myImportRoot;
  }

  public String getSuggestedProjectName() {
    final List<MavenProjectModel> list = getParameters().myMavenProjectTree.getRootProjects();
    if (list.size() == 1) {
      return list.get(0).getMavenId().artifactId;
    }
    return null;
  }
}
