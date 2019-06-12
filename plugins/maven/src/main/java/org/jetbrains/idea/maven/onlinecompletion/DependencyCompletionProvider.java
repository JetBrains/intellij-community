// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenCoordinate;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItemWithClass;
import org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters;

import java.io.IOException;
import java.util.List;

public interface DependencyCompletionProvider {

  @NotNull
  List<MavenDependencyCompletionItem> findGroupCandidates(MavenCoordinate template, SearchParameters searchParameters)
    throws IOException;

  @NotNull
  List<MavenDependencyCompletionItem> findArtifactCandidates(MavenCoordinate template, SearchParameters searchParameters)
    throws IOException;

  @NotNull
  List<MavenDependencyCompletionItem> findAllVersions(MavenCoordinate template, SearchParameters searchParameters)
    throws IOException;

  @NotNull
  List<MavenDependencyCompletionItemWithClass> findClassesByString(String str, SearchParameters searchParameters)
    throws IOException;
}
