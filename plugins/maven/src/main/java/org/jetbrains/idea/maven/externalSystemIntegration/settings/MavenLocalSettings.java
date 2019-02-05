// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.settings;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenConstants;


@State(name = "MavenLocalSettings", storages = {
  @Storage(StoragePathMacros.CACHE_FILE),
  @Storage(value = StoragePathMacros.WORKSPACE_FILE, deprecated = true)
})
public class MavenLocalSettings extends AbstractExternalSystemLocalSettings<AbstractExternalSystemLocalSettings.State> {
  public MavenLocalSettings(@NotNull Project project) {
    super(MavenConstants.SYSTEM_ID, project, new MyState());
  }

  @NotNull
  public static MavenLocalSettings getInstance(@NotNull Project project) {
    return new MavenLocalSettings(project);
    //return ServiceManager.getService(project, MavenLocalSettings.class);
  }

  public static class MyState extends AbstractExternalSystemLocalSettings.State {
  }
}
