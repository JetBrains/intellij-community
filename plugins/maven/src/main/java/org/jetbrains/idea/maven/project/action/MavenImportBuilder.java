package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.projectImport.SelectImportedProjectsStep;
import org.apache.maven.embedder.MavenEmbedder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.FileFinder;
import org.jetbrains.idea.maven.core.util.MavenEnv;
import org.jetbrains.idea.maven.core.util.ProjectUtil;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.state.MavenProjectsState;

import javax.swing.*;
import java.util.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenImportBuilder extends ProjectImportBuilder
  implements MavenImportProcessorContext, SelectImportedProjectsStep.Context<MavenProjectModel.Node> {

  private final static Icon ICON = IconLoader.getIcon("/images/mavenEmblem.png");

  private Project projectToUpdate;
  private MavenImporterPreferences preferences;

  private VirtualFile importRoot;
  private Collection<VirtualFile> myFiles;
  private List<String> myProfiles;
  MavenImportProcessor myImportProcessor;

  private boolean openModulesConfigurator;

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

  public void commit(final Project project) {
    myImportProcessor.resolve(project, myProfiles);

    myImportProcessor.commit(project, preferences.isAutoImportNew());

    final MavenImporterState importerState = project.getComponent(MavenImporter.class).getState();
    if (preferences.isAutoImportNew()) {
      // visit topmost non-linked projects
      myImportProcessor.getMavenProjectModel().visit(new MavenProjectModel.MavenProjectVisitorRoot() {
        public void visit(MavenProjectModel.Node node) {
          importerState.rememberProject(node.getPath());
        }
      });
    }

    if (!myProfiles.isEmpty()) {
      for (String profile : myProfiles) {
        importerState.rememberProfile(profile);
      }

      final MavenProjectsState projectsState = project.getComponent(MavenProjectsState.class);
      myImportProcessor.getMavenProjectModel().visit(new MavenProjectModel.MavenProjectVisitorPlain() {
        public void visit(MavenProjectModel.Node node) {
          final Set<String> projectProfiles = ProjectUtil.collectProfileIds(node.getMavenProject(), new HashSet<String>());
          projectProfiles.retainAll(myProfiles);
          projectsState.setProfiles(node.getFile(), projectProfiles);
        }
      });
    }
    MavenImporterPreferencesComponent.getInstance(project).loadState(preferences);
  }

  public Project getUpdatedProject() {
    return getProjectToUpdate();
  }

  public VirtualFile getRootDirectory() {
    return getImportRoot();
  }

  public boolean setRootDirectory(final String root) {
    myFiles = null;
    importRoot = FileFinder.refreshRecursively(root);
    if (getImportRoot() != null) {
      ProgressManager.getInstance().run(new Task.Modal(null, ProjectBundle.message("maven.scanning.projects"), true) {
        public void run(ProgressIndicator indicator) {
          indicator.setText(ProjectBundle.message("maven.locating.files"));
          myFiles = FileFinder.findFilesByName(getImportRoot().getChildren(), MavenEnv.POM_FILE, new ArrayList<VirtualFile>(), null, indicator,
                                               preferences.isLookForNested());
          indicator.setText2("");
        }

        public void onCancel() {
          myFiles = null;
        }
      });
    }
    return myFiles != null;
  }

  public List<String> getProfiles() {
    final SortedSet<String> profiles = new TreeSet<String>();

    final MavenEmbedder embedder = MavenImportProcessor.createEmbedder(getProjectToUpdate());
    final MavenProjectReader reader = new MavenProjectReader(embedder);
    for (VirtualFile file : myFiles) {
      ProjectUtil.collectProfileIds(reader.readBare(file.getPath()), profiles);
    }
    MavenEnv.releaseEmbedder(embedder);

    return new ArrayList<String>(profiles);
  }

  public boolean setProfiles(final List<String> profiles) {
    myImportProcessor = null;
    myProfiles = new ArrayList<String>(profiles);
    ProgressManager.getInstance().run(new Task.Modal(null, ProjectBundle.message("maven.scanning.projects"), true) {
      public void run(ProgressIndicator indicator) {
        myImportProcessor = new MavenImportProcessor(getProjectToUpdate(), preferences);
        myImportProcessor.createMavenProjectModel(new HashMap<VirtualFile, Module>(), myFiles, myProfiles);
        indicator.setText2("");
      }

      public void onCancel() {
        myImportProcessor = null;
      }
    });
    return myImportProcessor != null;
  }

  public List<MavenProjectModel.Node> getList() {
    return myImportProcessor.getMavenProjectModel().getRootProjects();
  }

  public boolean isMarked(final MavenProjectModel.Node element) {
    return true;
  }

  public void setList(List<MavenProjectModel.Node> nodes) throws ValidationException {
    for (MavenProjectModel.Node node : myImportProcessor.getMavenProjectModel().getRootProjects()) {
      node.setIncluded(nodes.contains(node));
    }
    myImportProcessor.createMavenToIdeaMapping(false);
    checkDuplicates();
  }

  private void checkDuplicates() throws ValidationException {
    final Collection<String> duplicates = myImportProcessor.getMavenToIdeaMapping().getDuplicateNames();
    if (!duplicates.isEmpty()) {
      StringBuilder builder = new StringBuilder(ProjectBundle.message("maven.import.duplicate.modules"));
      for (String duplicate : duplicates) {
        builder.append("\n").append(duplicate);
      }
      throw new ValidationException(builder.toString());
    }
  }

  public boolean isOpenProjectSettingsAfter() {
    return openModulesConfigurator;
  }

  public void setOpenProjectSettingsAfter(boolean on) {
    openModulesConfigurator = on;
  }

  public MavenImporterPreferences getPreferences() {
    if (preferences == null) {
      if (isUpdate()) {
        preferences = MavenImporterPreferencesComponent.getInstance(getProjectToUpdate()).getState().clone();
      }
      else {
        preferences = new MavenImporterPreferences();
      }
    }
    return preferences;
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
}
