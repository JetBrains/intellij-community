// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.model.presentation;

import com.intellij.ide.presentation.PresentationProvider;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin;

public class MavenDomPluginPresentationProvider extends PresentationProvider<MavenDomPlugin> {

  @Override
  public @Nullable String getName(MavenDomPlugin plugin) {
    String artifactId = plugin.getArtifactId().getStringValue();
    String version = plugin.getVersion().getStringValue();

    return version == null ? artifactId : artifactId + ':' + version;
  }
}
