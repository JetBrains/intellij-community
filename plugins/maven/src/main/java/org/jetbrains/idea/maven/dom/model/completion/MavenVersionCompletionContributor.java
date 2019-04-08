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

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.impl.NegatingComparable;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenVersionComparable;
import org.jetbrains.idea.maven.dom.converters.MavenArtifactCoordinatesVersionConverter;
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomArtifactCoordinates;
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.onlinecompletion.DependencySearchService;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class MavenVersionCompletionContributor extends CompletionContributor {

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    if (parameters.getCompletionType() != CompletionType.BASIC) return;

    PsiElement element = parameters.getPosition();

    PsiElement xmlText = element.getParent();
    if (!(xmlText instanceof XmlText)) return;

    PsiElement tagElement = xmlText.getParent();

    if (!(tagElement instanceof XmlTag)) return;

    XmlTag tag = (XmlTag)tagElement;

    if (!"version".equals(tag.getName())) {
      return;
    }

    Project project = element.getProject();

    DomElement domElement = DomManager.getDomManager(project).getDomElement(tag);

    if (!(domElement instanceof GenericDomValue)) return;

    DomElement parent = domElement.getParent();

    if (parent instanceof MavenDomArtifactCoordinates
        && ((GenericDomValue)domElement).getConverter() instanceof MavenArtifactCoordinatesVersionConverter) {
      MavenDomArtifactCoordinates coordinates = (MavenDomArtifactCoordinates)parent;

      String groupId = coordinates.getGroupId().getStringValue();
      String artifactId = coordinates.getArtifactId().getStringValue();

      if (StringUtil.isEmptyOrSpaces(artifactId)) return;


      CompletionResultSet newResultSet = result.withRelevanceSorter(CompletionService.getCompletionService().emptySorter().weigh(
        new LookupElementWeigher("mavenVersionWeigher") {
          @Nullable
          @Override
          public Comparable weigh(@NotNull LookupElement element) {
            return new NegatingComparable(new MavenVersionComparable(element.getLookupString()));
          }
        }));

      List<MavenDependencyCompletionItem> completionItems = searchVersions(groupId, artifactId, coordinates, project);

      for (MavenDependencyCompletionItem item : completionItems) {
        newResultSet.addElement(MavenDependencyCompletionUtil.lookupElement(item, item.getVersion()));
      }
      if (MavenServerManager.getInstance().isUseMaven2()) {
        newResultSet.addElement(LookupElementBuilder.create(RepositoryLibraryDescription.ReleaseVersionId).withStrikeoutness(true));
        newResultSet.addElement(LookupElementBuilder.create(RepositoryLibraryDescription.LatestVersionId).withStrikeoutness(true));
      }
    }
  }

  private List<MavenDependencyCompletionItem> searchVersions(String groupId,
                                                             String artifactId,
                                                             MavenDomArtifactCoordinates coordinates,
                                                             Project project) {

    DependencySearchService searchService = MavenProjectIndicesManager.getInstance(project).getSearchService();
    if (StringUtil.isEmptyOrSpaces(groupId)) {
      if (!(coordinates instanceof MavenDomPlugin)) return Collections.emptyList();
      List<MavenDependencyCompletionItem> result = new ArrayList<>();
      for (int i = 0; i < MavenArtifactUtil.DEFAULT_GROUPS.length; i++) {
        result
          .addAll(searchService.findAllVersions(new MavenDependencyCompletionItem(MavenArtifactUtil.DEFAULT_GROUPS[i], artifactId, null)));
      }
      return result;
    }
    return searchService
      .findAllVersions(new MavenDependencyCompletionItem(groupId, artifactId, null, null));
  }
}
