package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportBuilder;
import org.apache.maven.embedder.MavenEmbedder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.core.MavenCoreSettings;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.core.util.FileFinder;
import org.jetbrains.idea.maven.core.util.ProjectUtil;
import org.jetbrains.idea.maven.embedder.EmbedderFactory;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

import javax.swing.*;
import java.io.File;
import java.util.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenImportBuilder extends ProjectImportBuilder<MavenProjectModel.Node> implements MavenImportProcessorContext {
  private final static Icon ICON = IconLoader.getIcon("/images/mavenEmblem.png");

  private Project projectToUpdate;

  private MavenCoreSettings myCoreSettings;
  private MavenImporterSettings myImporterSettings;
  private MavenArtifactSettings myArtifactSettings;

  private VirtualFile importRoot;
  private Collection<VirtualFile> myFiles;
  private Map<VirtualFile, Set<String>> myFilesWithProfiles = new HashMap<VirtualFile, Set<String>>();
  private MavenImportProcessor myImportProcessor;

  private boolean openModulesConfigurator;
  private ArrayList<Pair<File, List<String>>> myResolutionProblems;

  public String getName() {
    return ProjectBundle.message("maven.name");
  }

  public Icon getIcon() {
    return ICON;
  }

  public void cleanup() {
    super.cleanup();
    myImportProcessor = null;
    importRoot = null;
    projectToUpdate = null;
  }

  @Override
  public boolean validate(Project current, Project dest) {
    return true;
  }

  private void logResolutionProblems() {
    String formatted = "There were resolution problems:";
    for (Pair<File, List<String>> problems : myResolutionProblems) {
      formatted += "\n" + problems.first;
      for (String message : problems.second) {
        formatted += "\n\t" + message;
      }
      formatted += "\n";
    }
    MavenLog.LOG.error(formatted);
  }

  public void commit(final Project project) {
    myImportProcessor.commit(project);

    MavenProjectsManager.getInstance(project).setMavenProject();
    MavenProjectsManager.getInstance(project).setDoesNotRequireReimport();

    StartupManager.getInstance(project).registerPostStartupActivity(new Runnable() {
      public void run() {
        MavenProjectsManager.getInstance(project).setImportProcessor(myImportProcessor);
        MavenProjectsManager.getInstance(project).resolve();
      }
    });

    MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
    manager.setOriginalFiles(myFiles);
    if (!myFilesWithProfiles.isEmpty()) {
      for (Map.Entry<VirtualFile,Set<String>> each : myFilesWithProfiles.entrySet()) {
        manager.setActiveProfiles(each.getKey(), each.getValue());
      }
    }

    project.getComponent(MavenWorkspaceSettingsComponent.class).getState().myImporterSettings = getImporterPreferences();
    project.getComponent(MavenWorkspaceSettingsComponent.class).getState().myArtifactSettings = getArtifactPreferences();
    project.getComponent(MavenCore.class).loadState(myCoreSettings);

    enusreRepositoryPathMacro();
  }

  private void enusreRepositoryPathMacro() {
    final File repo = myCoreSettings.getEffectiveLocalRepository();
    if (repo == null) return;

    final PathMacros macros = PathMacros.getInstance();

    for (String each : macros.getAllMacroNames()) {
      String path = macros.getValue(each);
      if (path == null) continue;
      if (new File(path).equals(repo)) return;
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        macros.setMacro("MAVEN_REPOSITORY", repo.getPath(), null);
      }
    });
  }

  public Project getUpdatedProject() {
    return getProjectToUpdate();
  }

  public VirtualFile getRootDirectory() {
    return getImportRoot();
  }

  public boolean setRootDirectory(final String root) throws ConfigurationException {
    myFiles = null;
    myFilesWithProfiles.clear();
    myImportProcessor = null;

    importRoot = FileFinder.refreshRecursively(root);
    if (getImportRoot() == null) return false;

    return runConfigurationProcess(ProjectBundle.message("maven.scanning.projects"), new MavenProgress.MavenTask() {
      public void run(MavenProgress p) throws MavenException, CanceledException {
        p.setText(ProjectBundle.message("maven.locating.files"));
        myFiles = FileFinder.findPomFiles(getImportRoot().getChildren(),
                                          getImporterPreferences().isLookForNested(),
                                          p.getIndicator(),
                                          new ArrayList<VirtualFile>());

        myFilesWithProfiles = collectProfiles(myFiles);

        if (myFilesWithProfiles.isEmpty()) {
          createImportProcessor(p);
        }

        p.setText2("");
      }
    });
  }

  private Map<VirtualFile, Set<String>> collectProfiles(Collection<VirtualFile> files) throws MavenException {
    Map<VirtualFile, Set<String>> result = new HashMap<VirtualFile, Set<String>>();

    try {
      MavenEmbedder e = EmbedderFactory.createEmbedderForRead(getCoreState());
      MavenProjectReader r = new MavenProjectReader(e);

      for (VirtualFile f : files) {
        Set<String> profiles = new LinkedHashSet<String>();
        ProjectUtil.collectProfileIds(r.readModel(f.getPath()), profiles);
        if (!profiles.isEmpty()) result.put(f, profiles);
      }

      EmbedderFactory.releaseEmbedder(e);
    }
    catch (MavenException ignore) {
    }

    return result;
  }

  public List<String> getAllProfiles() {
    Set<String> result = new LinkedHashSet<String>();
    for (Set<String> each : myFilesWithProfiles.values()) {
      result.addAll(each);
    }
    return new ArrayList<String>(result);
  }

  public boolean setSelectedProfiles(List<String> profiles) throws ConfigurationException {
    myImportProcessor = null;

    for (Map.Entry<VirtualFile,Set<String>> each : myFilesWithProfiles.entrySet()) {
      each.getValue().retainAll(profiles);
    }

    return runConfigurationProcess(ProjectBundle.message("maven.scanning.projects"), new MavenProgress.MavenTask() {
      public void run(MavenProgress p) throws MavenException, CanceledException {
        createImportProcessor(p);
        p.setText2("");
      }
    });
  }

  private boolean runConfigurationProcess(String message, MavenProgress.MavenTask p) throws ConfigurationException {
    try {
      MavenProgress.run(null, message, p);
    }
    catch (MavenException e) {
      throw new ConfigurationException(e.getMessage());
    }
    catch (CanceledException e) {
      return false;
    }

    return true;
  }

  private void createImportProcessor(MavenProgress p) throws MavenException, CanceledException {
    myImportProcessor = new MavenImportProcessor(getProject(),
                                                 getCoreState(),
                                                 getImporterPreferences(),
                                                 getArtifactPreferences());

    myImportProcessor.createMavenProjectModel(getProject(), myFiles, getAllProfiles(), p);
  }

  public List<MavenProjectModel.Node> getList() {
    return myImportProcessor.getMavenProjectModel().getRootProjects();
  }

  public boolean isMarked(final MavenProjectModel.Node element) {
    return true;
  }

  public void setList(List<MavenProjectModel.Node> nodes) throws ConfigurationException {
    for (MavenProjectModel.Node node : myImportProcessor.getMavenProjectModel().getRootProjects()) {
      node.setIncluded(nodes.contains(node));
    }
  }

  public boolean isOpenProjectSettingsAfter() {
    return openModulesConfigurator;
  }

  public void setOpenProjectSettingsAfter(boolean on) {
    openModulesConfigurator = on;
  }

  public MavenCoreSettings getCoreState() {
    if (myCoreSettings == null) {
      myCoreSettings = getProject().getComponent(MavenCore.class).getState().clone();
    }
    return myCoreSettings;
  }

  public MavenImporterSettings getImporterPreferences() {
    if (myImporterSettings == null) {
      myImporterSettings = getProject().getComponent(MavenWorkspaceSettingsComponent.class).getState()
        .myImporterSettings.clone();
    }
    return myImporterSettings;
  }

  private MavenArtifactSettings getArtifactPreferences() {
    if (myArtifactSettings == null) {
      myArtifactSettings = getProject().getComponent(MavenWorkspaceSettingsComponent.class).getState()
        .myArtifactSettings.clone();
    }
    return myArtifactSettings;
  }

  private Project getProject() {
    return isUpdate() ? getProjectToUpdate() : ProjectManager.getInstance().getDefaultProject();
  }

  public void setFiles(final Collection<VirtualFile> files) {
    myFiles = files;
  }

  @Nullable
  public Project getProjectToUpdate() {
    if (projectToUpdate == null) {
      projectToUpdate = getCurrentProject();
    }
    return projectToUpdate;
  }

  @Nullable
  public VirtualFile getImportRoot() {
    if (importRoot == null && isUpdate()) {
      final Project project = getProjectToUpdate();
      assert project != null;
      importRoot = project.getBaseDir();
    }
    return importRoot;
  }

  public String getSuggestedProjectName() {
    final List<MavenProjectModel.Node> list = myImportProcessor.getMavenProjectModel().getRootProjects();
    if(list.size()==1){
      return list.get(0).getMavenId().artifactId;
    }
    return null;
  }
}
