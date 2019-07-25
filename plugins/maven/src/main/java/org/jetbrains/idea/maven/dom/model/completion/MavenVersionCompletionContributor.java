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
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates;
import org.jetbrains.idea.maven.onlinecompletion.DependencySearchService;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;
import org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;

import java.util.function.Consumer;

public class MavenVersionCompletionContributor extends MavenCoordinateCompletionContributor {
  public MavenVersionCompletionContributor() {
    super("version");
  }

  @Override
  protected Promise<Void> find(@NotNull DependencySearchService service,
                               @NotNull MavenDomShortArtifactCoordinates coordinates,
                               @NotNull CompletionParameters parameters,
                               @NotNull Consumer<MavenRepositoryArtifactInfo> consumer) {

    SearchParameters searchParameters = createSearchParameters(parameters);
    String groupId = trimDummy(coordinates.getGroupId().getStringValue());
    String artifactId = trimDummy(coordinates.getArtifactId().getStringValue());

    if (MavenAbstractPluginExtensionCompletionContributor.isPluginOrExtension(coordinates) && StringUtil.isEmpty(groupId)) {
      return MavenAbstractPluginExtensionCompletionContributor.findPluginByArtifactId(service, artifactId, searchParameters, consumer);
    }

    return service.suggestPrefix(groupId, artifactId, searchParameters, mrai -> {
      if (StringUtil.equals(mrai.getArtifactId(), artifactId) && StringUtil.equals(mrai.getGroupId(), groupId)) {
        consumer.accept(mrai);
      }
    });
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
                            @NotNull MavenRepositoryArtifactInfo item) {
    result.addAllElements(ContainerUtil.map(item.getItems(), dci -> MavenDependencyCompletionUtil.lookupElement(dci, dci.getVersion())));
  }

  @Override
  protected boolean validate(String groupId, String artifactId) {
    return !StringUtil.isEmptyOrSpaces(artifactId);
  }

  @NotNull
  @Override
  protected CompletionResultSet amendResultSet(@NotNull CompletionResultSet result) {
    return result.withRelevanceSorter(CompletionService.getCompletionService().emptySorter().weigh(
      new MavenVersionNegatingWeigher()));
  }
}
