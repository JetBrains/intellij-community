// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.settings;

import com.intellij.openapi.externalSystem.settings.DelegatingExternalSystemSettingsListener;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener;
import org.jetbrains.annotations.NotNull;

public class DelegatingMavenSettingsListenerAdapter extends DelegatingExternalSystemSettingsListener<MavenProjectSettings>
  implements MavenSettingsListener {
  public DelegatingMavenSettingsListenerAdapter(@NotNull ExternalSystemSettingsListener<MavenProjectSettings> delegate) {
    super(delegate);
  }
}
