// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.model.presentation;

import com.intellij.ide.presentation.PresentationProvider;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomArtifactCoordinates;

public class MavenArtifactCoordinatesPresentationProvider extends PresentationProvider<MavenDomArtifactCoordinates> {

  private static final String UNKNOWN = "<unknown>";

  @Override
  public @Nullable String getName(MavenDomArtifactCoordinates coordinates) {
    return StringUtil.notNullize(coordinates.getGroupId().getStringValue(), UNKNOWN)
      + ':'
      + StringUtil.notNullize(coordinates.getArtifactId().getStringValue(), UNKNOWN)
      + ':'
      + StringUtil.notNullize(coordinates.getVersion().getStringValue(), UNKNOWN);
  }
}
