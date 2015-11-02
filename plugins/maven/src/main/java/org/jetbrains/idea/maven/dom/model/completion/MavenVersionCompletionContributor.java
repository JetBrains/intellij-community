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

import com.google.common.collect.Sets;
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
import org.jetbrains.idea.maven.dom.model.MavenDomArtifactCoordinates;
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.library.RepositoryUtils;

import java.util.Set;

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

      MavenProjectIndicesManager indicesManager = MavenProjectIndicesManager.getInstance(project);

      Set<String> versions;

      if (StringUtil.isEmptyOrSpaces(groupId)) {
        if (!(coordinates instanceof MavenDomPlugin)) return;

        versions = indicesManager.getVersions(MavenArtifactUtil.DEFAULT_GROUPS[0], artifactId);
        for (int i = 0; i < MavenArtifactUtil.DEFAULT_GROUPS.length; i++) {
          versions = Sets.union(versions, indicesManager.getVersions(MavenArtifactUtil.DEFAULT_GROUPS[i], artifactId));
        }
      }
      else {
        versions = indicesManager.getVersions(groupId, artifactId);
      }

      for (String version : versions) {
        newResultSet.addElement(LookupElementBuilder.create(version));
      }
      newResultSet.addElement(LookupElementBuilder.create(RepositoryUtils.ReleaseVersionId));
      newResultSet.addElement(LookupElementBuilder.create(RepositoryUtils.LatestVersionId));
    }
  }

}
