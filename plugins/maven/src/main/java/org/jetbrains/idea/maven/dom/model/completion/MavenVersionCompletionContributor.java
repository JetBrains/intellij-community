/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.dom.model.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;
import org.jetbrains.idea.reposearch.DependencySearchService;
import org.jetbrains.idea.reposearch.RepositoryArtifactData;
import org.jetbrains.idea.reposearch.SearchParameters;

import java.util.function.Consumer;

public class MavenVersionCompletionContributor extends MavenCoordinateCompletionContributor {
  public MavenVersionCompletionContributor() {
    super("version");
  }

  @Override
  protected Promise<Integer> find(@NotNull DependencySearchService service,
                                  @NotNull MavenDomShortArtifactCoordinates coordinates,
                                  @NotNull CompletionParameters parameters,
                                  @NotNull Consumer<RepositoryArtifactData> consumer) {

    SearchParameters searchParameters = createSearchParameters(parameters);
    String groupId = trimDummy(coordinates.getGroupId().getStringValue());
    String artifactId = trimDummy(coordinates.getArtifactId().getStringValue());

    if (MavenAbstractPluginExtensionCompletionContributor.isPluginOrExtension(coordinates) && StringUtil.isEmpty(groupId)) {
      return MavenAbstractPluginExtensionCompletionContributor
        .findPluginByArtifactId(service, artifactId, searchParameters, new RepositoryArtifactDataConsumer(artifactId, groupId, consumer));
    }


    return service.suggestPrefix(groupId, artifactId, searchParameters, new RepositoryArtifactDataConsumer(artifactId, groupId, consumer));
  }

  @Override
  protected void fillAfter(CompletionResultSet result) {
    if (MavenServerManager.getInstance().isUseMaven2()) {
      result.addElement(LookupElementBuilder.create(RepositoryLibraryDescription.ReleaseVersionId).withStrikeoutness(true));
      result.addElement(LookupElementBuilder.create(RepositoryLibraryDescription.LatestVersionId).withStrikeoutness(true));
    }
  }

  @Override
  protected void fillResult(@NotNull MavenDomShortArtifactCoordinates coordinates,
                            @NotNull CompletionResultSet result,
                            @NotNull MavenRepositoryArtifactInfo item,
                            @NotNull String completionPrefix) {
    result.addAllElements(
      ContainerUtil.map(
        item.getItems(),
        dci -> {
          final LookupElement lookup = MavenDependencyCompletionUtil.lookupElement(dci, dci.getVersion());
          lookup.putUserData(MAVEN_COORDINATE_COMPLETION_PREFIX_KEY, completionPrefix);
          return lookup;
        }
      )
    );
  }

  @NotNull
  @Override
  protected CompletionResultSet amendResultSet(@NotNull CompletionResultSet result) {
    return result.withRelevanceSorter(CompletionService.getCompletionService().emptySorter().weigh(
      new MavenVersionNegatingWeigher()));
  }

  private static class RepositoryArtifactDataConsumer implements Consumer<RepositoryArtifactData> {
    private final String myArtifactId;
    private final String myGroupId;
    private @NotNull final Consumer<RepositoryArtifactData> myConsumer;

    public RepositoryArtifactDataConsumer(String artifactId, String groupId, @NotNull Consumer<RepositoryArtifactData> consumer) {
      myArtifactId = artifactId;
      myGroupId = groupId;
      myConsumer = consumer;
    }

    @Override
    public void accept(RepositoryArtifactData rad) {
      if (rad instanceof MavenRepositoryArtifactInfo mrai) {
        if (StringUtil.equals(mrai.getArtifactId(), myArtifactId) &&
            (StringUtil.isEmpty(myGroupId) || StringUtil.equals(mrai.getGroupId(), myGroupId))) {
          myConsumer.accept(mrai);
        }
      }
    }
  }
}
