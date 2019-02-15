// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessorBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Collections;
import java.util.List;

public class MavenProjectOpenProcessor extends ProjectOpenProcessorBase<MavenProjectBuilder> {
  public MavenProjectOpenProcessor(@NotNull MavenProjectBuilder builder) {
    super(builder);
  }

  @Override
  @Nullable
  public String[] getSupportedExtensions() {
    return MavenConstants.POM_NAMES;
  }

  @Override
  public boolean canOpenProject(@NotNull VirtualFile file) {
    return super.canOpenProject(file) || MavenUtil.isPomFile(file);
  }

  @Override
  public boolean doQuickImport(VirtualFile file, WizardContext wizardContext) {
    getBuilder().setFiles(Collections.singletonList(file));

    if (!getBuilder().setSelectedProfiles(MavenExplicitProfiles.NONE)) return false;

    List<MavenProject> projects = getBuilder().getList();
    if (projects.size() != 1) return false;

    getBuilder().setList(projects);
    wizardContext.setProjectName(getBuilder().getSuggestedProjectName());

    return true;
  }
 /*
  public boolean doQuickImportExternalSystem(VirtualFile file, WizardContext wizardContext) {
   org.jetbrains.idea.maven.externalSystemIntegration.MavenProjectImportProvider provider =
      new org.jetbrains.idea.maven.externalSystemIntegration.MavenProjectImportProvider(getBuilder());
    getBuilder().setFileToImport(file.getPath());
    getBuilder().prepare(wizardContext);

    final String pathToUse;
    if (!file.isDirectory() && file.getParent() != null) {
      pathToUse = file.getParent().getPath();
    }
    else {
      pathToUse = file.getPath();
    }
    getBuilder().getControl(null).setLinkedProjectPath(pathToUse);

    final boolean result;
    WizardContext dialogWizardContext = null;
    /*if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      result = setupGradleProjectSettingsInHeadlessMode(projectImportProvider, wizardContext);
    }

    AddModuleWizard dialog = new AddModuleWizard(null, file.getPath(), provider);
    dialogWizardContext = dialog.getWizardContext();
    dialogWizardContext.setProjectBuilder(getBuilder());
    dialog.navigateToStep(step -> step instanceof SelectExternalProjectStep);
    result = dialog.showAndGet();

    if (result && getBuilder().getExternalProjectNode() != null) {
      wizardContext.setProjectName(getBuilder().getExternalProjectNode().getData().getInternalName());
    }
    if (result && dialogWizardContext != null) {
      wizardContext.setProjectStorageFormat(dialogWizardContext.getProjectStorageFormat());
    }
    return result;
  }*/
}
