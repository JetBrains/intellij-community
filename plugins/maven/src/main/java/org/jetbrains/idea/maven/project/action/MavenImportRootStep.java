package org.jetbrains.idea.maven.project.action;

import com.intellij.ide.util.projectWizard.NamePathComponent;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportWizardStep;
import org.jetbrains.idea.maven.project.ImporterPreferencesForm;
import org.jetbrains.idea.maven.project.MavenImportProcessorContext;
import org.jetbrains.idea.maven.project.MavenImporterPreferences;
import org.jetbrains.idea.maven.project.ProjectBundle;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * @author Vladislav.Kaznacheev
 */
class MavenImportRootStep extends ProjectImportWizardStep {

  private final WizardContext myWizardContext;
  private final MavenImportProcessorContext myImportContext;
  private final MavenImporterPreferences myImporterPreferences;

  private final JPanel myPanel;
  private NamePathComponent myRootPathComponent;
  private final ImporterPreferencesForm myImporterPreferencesForm;

  public MavenImportRootStep(WizardContext wizardContext,
                             final MavenImportProcessorContext importContext,
                             final MavenImporterPreferences importerPreferences) {
    super(importContext.getUpdatedProject()!=null);
    myWizardContext = wizardContext;
    myImportContext = importContext;
    myImporterPreferences = importerPreferences;

    myImporterPreferencesForm = new ImporterPreferencesForm() {
      public String getDefaultModuleDir() {
        return myRootPathComponent.getPath();
      }
    };

    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEtchedBorder());

    myRootPathComponent = new NamePathComponent("", ProjectBundle.message("maven.import.label.select.root"),
                                                ProjectBundle.message("maven.import.title.select.root"), "", false);
    myRootPathComponent.setNameComponentVisible(false);

    myPanel.add(myRootPathComponent, new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                                            GridBagConstraints.HORIZONTAL, new Insets(5, 6, 0, 6), 0, 0));

    myPanel.add(myImporterPreferencesForm.createComponent(), new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST,
                                                                                    GridBagConstraints.HORIZONTAL, new Insets(15, 6, 0, 6),
                                                                                    0, 0));
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void updateDataModel() {
    myImporterPreferencesForm.getData(myImporterPreferences);
    myImportContext.createMavenProjectModel(FileUtil.toSystemDependentName(myRootPathComponent.getPath()));
  }

  public boolean validate() {
    return myRootPathComponent.getPath() != null && new File(myRootPathComponent.getPath()).exists();
  }

  public void updateStep() {
    if (!myRootPathComponent.isPathChangedByUser()) {
      String path = null;
      final VirtualFile rootDirectory = myImportContext.getRootDirectory();
      if (rootDirectory != null) {
        path = rootDirectory.getPath();
      }
      else if (myWizardContext.getProjectFileDirectory() != null) {
        path = myWizardContext.getProjectFileDirectory();
      }
      else if (myImportContext.getUpdatedProject() != null) {
        final VirtualFile baseDir = myImportContext.getUpdatedProject().getBaseDir();
        if (baseDir != null) {
          path = baseDir.getPath();
        }
      }
      if (path != null) {
        myRootPathComponent.setPath(FileUtil.toSystemDependentName(path));
        myRootPathComponent.getPathComponent().selectAll();
      }
    }
    myImporterPreferencesForm.setData(myImporterPreferences);
  }

  public JComponent getPreferredFocusedComponent() {
    return myRootPathComponent.getPathComponent();
  }
}