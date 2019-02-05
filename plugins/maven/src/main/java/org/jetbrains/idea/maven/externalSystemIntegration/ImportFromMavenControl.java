// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.settings.AbstractImportFromExternalSystemControl;
import com.intellij.openapi.externalSystem.util.ExternalSystemSettingsControl;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.settings.*;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.utils.MavenLog;

public class ImportFromMavenControl
  extends AbstractImportFromExternalSystemControl<MavenProjectSettings, MavenSettingsListener, MavenSettings> {
  public ImportFromMavenControl() {
    super(MavenConstants.SYSTEM_ID, MavenSettings.getInstance(ProjectManager.getInstance().getDefaultProject()), MavenProjectSettings.getInitial(),
          true);
  }

  protected ImportFromMavenControl(@NotNull ProjectSystemId externalSystemId,
                                   @NotNull MavenSettings systemSettings,
                                   @NotNull MavenProjectSettings projectSettings, boolean showProjectFormatPanel) {
    super(externalSystemId, systemSettings, projectSettings, showProjectFormatPanel);
  }

  @Override
  protected void onLinkedProjectPathChange(@NotNull String path) {
    MavenLog.LOG.warn("onLinkedProjectPathChange " + path);
  }

  @NotNull
  @Override
  protected ExternalSystemSettingsControl<MavenProjectSettings> createProjectSettingsControl(@NotNull MavenProjectSettings settings) {
    return new MavenProjectSettingsControl(settings);
  }


  @Nullable
  @Override
  protected ExternalSystemSettingsControl<MavenSettings> createSystemSettingsControl(@NotNull MavenSettings settings) {
    return new MavenSystemSettingsControl(settings);
  }
}
