package org.jetbrains.idea.maven.project.action;

import com.intellij.ide.util.projectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportWizard;
import com.intellij.projectImport.SelectImportedProjectsStep;
import org.apache.maven.embedder.MavenEmbedder;
import org.jetbrains.idea.maven.core.util.FileFinder;
import org.jetbrains.idea.maven.core.util.MavenEnv;
import org.jetbrains.idea.maven.core.util.ProjectUtil;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.state.MavenProjectsState;

import java.io.File;
import java.util.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenImportWizard extends ProjectImportWizard
  implements MavenImportProcessorContext, SelectImportedProjectsStep.Context<MavenProjectModel.Node> {

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

  protected void initImport(final Project currentProject, final boolean updateCurrent) {
    super.initImport(currentProject, updateCurrent);

    if (updateCurrent) {
      projectToUpdate = currentProject;
      preferences = MavenImporterPreferencesComponent.getInstance(currentProject).getState().clone();
      importRoot = currentProject.getBaseDir();
    }
    else {
      projectToUpdate = null;
      preferences = new MavenImporterPreferences();
      importRoot = null;
    }
  }

  public AddModuleWizard.ModuleWizardStepFactory getStepsFactory(final Project currentProject, final boolean updateCurrent) {
    return new AddModuleWizard.ModuleWizardStepFactory() {
      public ModuleWizardStep[] createSteps(final WizardContext wizardContext) {
        return new ModuleWizardStep[]{new MavenImportRootStep(wizardContext, MavenImportWizard.this, preferences),
          new SelectProfilesStep(MavenImportWizard.this),
          new SelectImportedProjectsStep<MavenProjectModel.Node>(MavenImportWizard.this, updateCurrent) {
            protected String getElementText(final MavenProjectModel.Node node) {
              final StringBuilder stringBuilder = new StringBuilder();
              stringBuilder.append(node.getArtifact().toString());
              final String relPath = VfsUtil.getRelativePath(node.getFile().getParent(), getRootDirectory(), File.separatorChar);
              if (relPath.length() != 0) {
                stringBuilder.append(" [").append(relPath).append("]");
              }
              return stringBuilder.toString();
            }
          }};
      }
    };
  }

  protected void cleanup() {
    myImportProcessor = null;
  }

  public void afterProjectOpen(final Project project) {

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
    return projectToUpdate;
  }

  public VirtualFile getRootDirectory() {
    return importRoot;
  }

  public boolean setRootDirectory(final String root) {
    myFiles = null;
    importRoot = FileFinder.refreshRecursively(root);
    if (importRoot != null) {
      ProgressManager.getInstance().run(new Task.Modal(null, ProjectBundle.message("maven.scanning.projects"), true) {
        public void run(ProgressIndicator indicator) {
          indicator.setText(ProjectBundle.message("maven.locating.files"));
          myFiles = FileFinder.findFilesByName(importRoot.getChildren(), MavenEnv.POM_FILE, new ArrayList<VirtualFile>(), null, indicator,
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

    final MavenEmbedder embedder = MavenImportProcessor.createEmbedder(projectToUpdate);
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
        myImportProcessor = new MavenImportProcessor(projectToUpdate, preferences);
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

  protected boolean canQuickImport(VirtualFile file) {
    return file.getName().equals(MavenEnv.POM_FILE);
  }

  public boolean doQuickImport(VirtualFile file) {
    myFiles = Arrays.asList(file);

    if(!setProfiles(new ArrayList<String>())){
      return false;
    }

    final List<MavenProjectModel.Node> projects = getList();
    try {
      setList(projects);
    }
    catch (ValidationException e) {
      return false;
    }

    if(projects.size()!=1){
      return false;
    }

    myNewProjectName = projects.get(0).getMavenProject().getArtifactId();
    return true;
  }
}
