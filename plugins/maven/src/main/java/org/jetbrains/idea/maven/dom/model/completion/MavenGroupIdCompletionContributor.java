// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.converters.MavenArtifactCoordinatesGroupIdConverter;
import org.jetbrains.idea.maven.indices.IndicesBundle;

public class MavenGroupIdCompletionContributor extends MavenCoordinateCompletionContributor<MavenArtifactCoordinatesGroupIdConverter> {

  public MavenGroupIdCompletionContributor() {
    super("groupId", MavenArtifactCoordinatesGroupIdConverter.class);
  }

  @Nullable
  @Override
  public String handleEmptyLookup(@NotNull CompletionParameters parameters, Editor editor) {
    if (new PlaceChecker(parameters).checkPlace().isCorrectPlace()) {
      return IndicesBundle.message("maven.dependency.completion.group.empty");
    }
    return null;
  }
}
