// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.idea.maven.dom.converters.MavenArtifactCoordinatesArtifactIdConverter;

public class MavenArtifactIdCompletionContributor
  extends MavenCoordinateCompletionContributor<MavenArtifactCoordinatesArtifactIdConverter> {

  public MavenArtifactIdCompletionContributor() {
    super("artifactId", MavenArtifactCoordinatesArtifactIdConverter.class);
  }

  @Override
  protected boolean validate(String groupId, String artifactId) {
    return StringUtil.isNotEmpty(groupId);
  }
}