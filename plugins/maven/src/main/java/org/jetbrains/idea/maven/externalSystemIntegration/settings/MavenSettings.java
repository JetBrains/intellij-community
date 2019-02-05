// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.settings;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

@State(name = "MavenSettings", storages = @Storage("maven.xml"))
public class MavenSettings extends AbstractExternalSystemSettings<MavenSettings, MavenProjectSettings, MavenSettingsListener> {

  @NotNull private final String myJdkToUse;

  public MavenSettings(@NotNull Project project, @NotNull String jdkToUse) {
    super(MavenSettingsListener.TOPIC, project);
    myJdkToUse = jdkToUse;
  }

  @Override
  public void subscribe(@NotNull ExternalSystemSettingsListener<MavenProjectSettings> listener) {
    getProject().getMessageBus().connect(getProject())
      .subscribe(MavenSettingsListener.TOPIC, new DelegatingMavenSettingsListenerAdapter(listener));
  }

  @Override
  protected void copyExtraSettingsFrom(@NotNull MavenSettings settings) {

  }

  @Override
  protected void checkSettings(@NotNull MavenProjectSettings old, @NotNull MavenProjectSettings current) {

  }

  @NotNull
  public String getJdkToUse() {
    return myJdkToUse;
  }

  @NotNull
  public static MavenSettings getInstance(@NotNull Project project) {
    return new MavenSettings(project, ExternalSystemJdkUtil.USE_PROJECT_JDK);
  }

}
