package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportBuilder;
import org.apache.maven.embedder.MavenEmbedder;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.MavenCore;
import org.jetbrains.idea.maven.core.MavenCoreSettings;
import org.jetbrains.idea.maven.core.MavenFactory;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.core.util.FileFinder;
import org.jetbrains.idea.maven.core.util.ProjectUtil;
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
  private List<String> myProfiles;
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
    try {
      myResolutionProblems = new ArrayList<Pair<File, List<String>>>();
      myImportProcessor.resolve(dest, myProfiles, myResolutionProblems);

      if (ApplicationManager.getApplication().isHeadlessEnvironment()
          && !myResolutionProblems.isEmpty()) {
        logResolutionProblems();
        return false;
      }
    }
    catch (MavenException e) {
      Messages.showErrorDialog(dest, e.getMessage(), getTitle());
      return false;
    }
    catch (CanceledException e) {
      return false;
    }
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
    myImportProcessor.commit(project, myProfiles);

    MavenImporter importerComponent = MavenImporter.getInstance(project);
    importerComponent.setDoesNotRequireSynchronization();

    StartupManager.getInstance(project).registerPostStartupActivity(new Runnable() {
      public void run() {
        MavenImportToolWindow toolWindow = new MavenImportToolWindow(project, ProjectBundle.message("maven.import"));
        toolWindow.displayResolutionProblems(myResolutionProblems);
      }
    });

    MavenImporterState importerState = importerComponent.getState();
    if (!myProfiles.isEmpty()) {
      for (String profile : myProfiles) {
        importerState.memorizeProfile(profile);
      }

      final MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
      myImportProcessor.getMavenProjectModel().visit(new MavenProjectModel.MavenProjectVisitorPlain() {
        public void visit(MavenProjectModel.Node node) {
          MavenProject p = node.getMavenProject();
          Set<String> profiles = ProjectUtil.collectProfileIds(p.getModel(), new HashSet<String>());
          profiles.retainAll(myProfiles);
          projectsManager.setProfiles(node.getFile(), profiles);
        }
      });
    }
    project.getComponent(MavenWorkspaceSettingsComponent.class).getState().myImporterSettings = getImporterPreferences();
    project.getComponent(MavenWorkspaceSettingsComponent.class).getState().myArtifactSettings = getArtifactPreferences();
    project.getComponent(MavenCore.class).loadState(myCoreSettings);
  }

  public Project getUpdatedProject() {
    return getProjectToUpdate();
  }

  public VirtualFile getRootDirectory() {
    return getImportRoot();
  }

  public boolean setRootDirectory(final String root) throws ConfigurationException {
    myFiles = null;
    myProfiles = null;
    myImportProcessor = null;

    importRoot = FileFinder.refreshRecursively(root);
    if (getImportRoot() == null) return false;

    return runConfigurationProcess(ProjectBundle.message("maven.scanning.projects"), new Progress.Process() {
      public void run(Progress p) throws MavenException, CanceledException {
        p.setText(ProjectBundle.message("maven.locating.files"));
        myFiles = FileFinder.findFilesByName(getImportRoot().getChildren(), Constants.POM_XML,
                                             new ArrayList<VirtualFile>(), null,
                                             p.getIndicator(),
                                             getImporterPreferences().isLookForNested());

        myProfiles = collectProfiles(myFiles);

        if (myProfiles.isEmpty()) {
          createImportProcessor(p);
        }

        p.setText2("");
      }
    });
  }

  private List<String> collectProfiles(Collection<VirtualFile> files) throws MavenException {
    SortedSet<String> profiles = new TreeSet<String>();

    try {
      MavenEmbedder e = MavenFactory.createEmbedderForRead(getCoreState());
      MavenProjectReader r = new MavenProjectReader(e);
      for (VirtualFile f : files) {
        ProjectUtil.collectProfileIds(r.readModel(f.getPath()), profiles);
      }
      MavenFactory.releaseEmbedder(e);
    }
    catch (MavenException ignore) {
    }

    return new ArrayList<String>(profiles);
  }

  public List<String> getProfiles() {
    return myProfiles;
  }

  public boolean setProfiles(final List<String> profiles) throws ConfigurationException {
    myImportProcessor = null;
    myProfiles = new ArrayList<String>(profiles);

    return runConfigurationProcess(ProjectBundle.message("maven.scanning.projects"), new Progress.Process() {
      public void run(Progress p) throws MavenException, CanceledException {
        createImportProcessor(p);
        p.setText2("");
      }
    });
  }

  private boolean runConfigurationProcess(String message, Progress.Process p) throws ConfigurationException {
    try {
      Progress.run(null, message, p);
    }
    catch (MavenException e) {
      throw new ConfigurationException(e.getMessage());
    }
    catch (CanceledException e) {
      return false;
    }

    return true;
  }

  private void createImportProcessor(Progress p) throws MavenException, CanceledException {
    myImportProcessor = new MavenImportProcessor(getProject(),
                                                 getCoreState(),
                                                 getImporterPreferences(),
                                                 getArtifactPreferences());

    myImportProcessor.createMavenProjectModel(myFiles, new HashMap<VirtualFile, Module>(), myProfiles, p);
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
    myImportProcessor.createMavenToIdeaMapping();
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
      return list.get(0).getId().artifactId;
    }
    return null;
  }
}
