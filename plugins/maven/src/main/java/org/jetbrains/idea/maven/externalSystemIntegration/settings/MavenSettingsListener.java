// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.settings;

import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

public interface MavenSettingsListener extends ExternalSystemSettingsListener<MavenProjectSettings> {
  Topic<MavenSettingsListener> TOPIC = Topic.create("Maven-specific settings", MavenSettingsListener.class);

}
