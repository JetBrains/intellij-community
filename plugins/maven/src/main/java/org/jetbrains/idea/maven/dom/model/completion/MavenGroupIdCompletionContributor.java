// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates;
import org.jetbrains.idea.maven.indices.IndicesBundle;
import org.jetbrains.idea.maven.onlinecompletion.DependencySearchService;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;
import org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters;

import java.util.function.Consumer;

public class MavenGroupIdCompletionContributor extends MavenCoordinateCompletionContributor {

  public MavenGroupIdCompletionContributor() {
    super("groupId");
  }

  @Nullable
  @Override
  public String handleEmptyLookup(@NotNull CompletionParameters parameters, Editor editor) {
    if (new PlaceChecker(parameters).checkPlace().isCorrectPlace()) {
      return IndicesBundle.message("maven.dependency.completion.group.empty");
    }
    return null;
  }

  @Override
  protected Promise<Void> find(@NotNull DependencySearchService service,
                               @NotNull MavenDomShortArtifactCoordinates coordinates,
                               @NotNull CompletionParameters parameters,
                               @NotNull Consumer<MavenRepositoryArtifactInfo> consumer) {

    SearchParameters searchParameters = createSearchParameters(parameters);
    String groupId = trimDummy(coordinates.getGroupId().getStringValue());
    return service.fulltextSearch(groupId, searchParameters, consumer);
  }
}
