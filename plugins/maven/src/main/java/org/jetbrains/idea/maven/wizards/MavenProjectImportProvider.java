// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.projectImport.ProjectImportProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenUtil;

public final class MavenProjectImportProvider extends ProjectImportProvider {
  @Override
  protected ProjectImportBuilder doGetBuilder() {
    return ProjectImportBuilder.EXTENSIONS_POINT_NAME.findExtensionOrFail(MavenProjectBuilder.class);
  }

  @Override
  public ModuleWizardStep[] createSteps(final WizardContext wizardContext) {
    return ModuleWizardStep.EMPTY_ARRAY;
  }

  @Override
  public boolean canImport(@NotNull VirtualFile fileOrDirectory, @Nullable Project project) {
    if (super.canImport(fileOrDirectory, project)) return true;

    if (!fileOrDirectory.isDirectory()) {
      return MavenUtil.isPomFileIgnoringName(project, fileOrDirectory);
    }

    return false;
  }

  @Override
  protected boolean canImportFromFile(VirtualFile file) {
    return MavenUtil.isPomFileName(file.getName());
  }

  @NotNull
  @Override
  public String getFileSample() {
    return "<b>Maven</b> project file (pom.xml)";
  }
}