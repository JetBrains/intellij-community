// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalProjectImportBuilder;
import com.intellij.openapi.externalSystem.service.ui.ExternalProjectDataSelectorDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullFactory;
import icons.MavenIcons;
import icons.OpenapiIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenConstants;

import javax.swing.*;
import java.io.File;

public class MavenProjectImportBuilder extends AbstractExternalProjectImportBuilder<ImportFromMavenControl> {
  public MavenProjectImportBuilder(@NotNull ProjectDataManager projectDataManager) {
    super(projectDataManager, () -> new ImportFromMavenControl(), MavenConstants.SYSTEM_ID);
  }

  @Override
  protected void doPrepare(@NotNull WizardContext context) {

  }

  @Override
  protected void beforeCommit(@NotNull DataNode<ProjectData> dataNode, @NotNull Project project) {

  }

  @NotNull
  @Override
  protected File getExternalProjectConfigToUse(@NotNull File file) {
    return file.isDirectory() ? file : file.getParentFile();
  }

  @Override
  protected void applyExtraSettings(@NotNull WizardContext context) {
  }

  @NotNull
  @Override
  public String getName() {
    return "Maven";
  }

  @Override
  public Icon getIcon() {
    return OpenapiIcons.RepositoryLibraryLogo;
  }
}
