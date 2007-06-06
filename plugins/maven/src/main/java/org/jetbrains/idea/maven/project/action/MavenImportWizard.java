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
import org.jetbrains.idea.maven.core.util.FileFinder;
import org.jetbrains.idea.maven.core.util.MavenEnv;
import org.jetbrains.idea.maven.project.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenImportWizard extends ProjectImportWizard
  implements MavenImportProcessorContext, SelectImportedProjectsStep.Context<MavenProjectModel.Node> {

  private Project projectToUpdate;
  private MavenImporterPreferences preferences;

  MavenImportProcessor myImportProcessor;
  private VirtualFile importRoot;

  private boolean openModulesConfigurator;

  public String getName() {
    return ProjectBundle.message("maven.name");
  }

  public AddModuleWizard.ModuleWizardStepFactory getStepsFactory(final Project currentProject, final boolean updateCurrent) {

    if ((updateCurrent)) {
      projectToUpdate = currentProject;
      preferences = MavenImporterPreferencesComponent.getInstance(currentProject).getState().safeClone();
    }
    else {
      projectToUpdate = null;
      preferences = new MavenImporterPreferences();
    }

    myImportProcessor = new MavenImportProcessor(projectToUpdate, preferences);

    return new AddModuleWizard.ModuleWizardStepFactory() {
      public ModuleWizardStep[] createSteps(final WizardContext wizardContext) {
        return new ModuleWizardStep[]{new MavenImportRootStep(wizardContext, MavenImportWizard.this, preferences),
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

  public void commitImport(final Project project) {

    myImportProcessor.resolve(project);

    myImportProcessor.commit(project, preferences.isAutoImportNew());

    if (preferences.isAutoImportNew()) {
      final MavenImporterState importerState = project.getComponent(MavenImporter.class).getState();
      // visit topmost non-linked projects
      myImportProcessor.getMavenProjectModel().visit(new MavenProjectModel.MavenProjectVisitorPlain() {
        public void visit(MavenProjectModel.Node node) {
          if (!node.isLinked()) {
            importerState.remember(node.getPath());
          }
        }

        public Iterable<MavenProjectModel.Node> getChildren(final MavenProjectModel.Node node) {
          return node.isLinked() ? super.getChildren(node) : null;
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
    myImportProcessor.clearMavenProjectModel();

    importRoot = FileFinder.refreshRecursively(root);

    if (importRoot != null) {
      ProgressManager.getInstance().run(new Task.Modal(null, ProjectBundle.message("maven.scanning.projects"), true) {
        public void run(ProgressIndicator indicator) {
          indicator.setText(ProjectBundle.message("maven.locating.files"));
          final Collection<VirtualFile> files = FileFinder.findFilesByName(importRoot.getChildren(), MavenEnv.POM_FILE,
                                                                           new ArrayList<VirtualFile>(), null, indicator,
                                                                           preferences.isLookForNested());
          indicator.setText2("");
          if (indicator.isCanceled()) return;

          myImportProcessor.createMavenProjectModel(new HashMap<VirtualFile, Module>(), files);
        }

        public void onCancel() {
          myImportProcessor.clearMavenProjectModel();
        }
      });
    }

    return myImportProcessor.getMavenProjectModel() != null;
  }

  public List<MavenProjectModel.Node> getList() {
    return myImportProcessor.getMavenProjectModel().getRootProjects();
  }

  public void setList(List<MavenProjectModel.Node> nodes) {
    for (MavenProjectModel.Node node : myImportProcessor.getMavenProjectModel().getRootProjects()) {
      node.setIncluded(nodes.contains(node));
    }
    myImportProcessor.createMavenToIdeaMapping(false);
  }

  public boolean isOpenProjectSettingsAfter() {
    return openModulesConfigurator;
  }

  public void setOpenProjectSettingsAfter(boolean on) {
    openModulesConfigurator = on;
  }
}
