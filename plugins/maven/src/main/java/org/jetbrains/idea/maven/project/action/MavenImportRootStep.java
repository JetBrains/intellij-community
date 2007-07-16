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

/**
 * @author Vladislav.Kaznacheev
 */
class MavenImportRootStep extends ProjectImportWizardStep {

  private MavenImportProcessorContext myImportContext;
  private MavenImporterPreferences myImporterPreferences;

  private final JPanel myPanel;
  private NamePathComponent myRootPathComponent;
  private final ImporterPreferencesForm myImporterPreferencesForm;

  public MavenImportRootStep(WizardContext wizardContext) {
    super(wizardContext);

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
    final MavenImporterPreferences preferences = getImporterPreferences();
    myImporterPreferencesForm.getData(preferences);
    suggestProjectNameAndPath(preferences.getDedicatedModuleDir(), myRootPathComponent.getPath());
  }

  public boolean validate() {
    return getImportContext().setRootDirectory(myRootPathComponent.getPath());
  }

  public void updateStep() {
    if (!myRootPathComponent.isPathChangedByUser()) {
      final VirtualFile rootDirectory = getImportContext().getRootDirectory();
      final String path;
      if (rootDirectory != null) {
        path = rootDirectory.getPath();
      }
      else {
        path = getWizardContext().getProjectFileDirectory();
      }
      if (path != null) {
        myRootPathComponent.setPath(FileUtil.toSystemDependentName(path));
        myRootPathComponent.getPathComponent().selectAll();
      }
    }
    myImporterPreferencesForm.setData(getImporterPreferences());
  }

  public JComponent getPreferredFocusedComponent() {
    return myRootPathComponent.getPathComponent();
  }

  public MavenImportProcessorContext getImportContext() {
    if (myImportContext == null) {
      myImportContext = (MavenImportProcessorContext)getBuilder();
    }
    return myImportContext;
  }

  public MavenImporterPreferences getImporterPreferences() {
    if (myImporterPreferences == null) {
      myImporterPreferences = ((MavenImportBuilder)getBuilder()).getPreferences();
    }
    return myImporterPreferences;
  }
}