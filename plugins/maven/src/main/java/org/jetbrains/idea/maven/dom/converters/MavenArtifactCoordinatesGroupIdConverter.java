// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.onlinecompletion.OfflineSearchService;
import org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class MavenArtifactCoordinatesGroupIdConverter extends MavenArtifactCoordinatesConverter implements MavenSmartConverter<String> {
  @Override
  protected boolean doIsValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context) {
    if (StringUtil.isEmpty(id.getGroupId())) return false;

    if (manager.hasGroupId(id.getGroupId())) return true;

    // Check if artifact was found on importing.
    MavenProject mavenProject = findMavenProject(context);
    if (mavenProject != null) {
      for (MavenArtifact artifact : mavenProject.findDependencies(id.getGroupId(), id.getArtifactId())) {
        if (artifact.isResolved()) {
          return true;
        }
      }
    }

    return false;
  }

  @Override
  protected Set<String> doGetVariants(MavenId id, OfflineSearchService searchService) {
    return Collections.emptySet();
  }

  @Nullable
  @Override
  public LookupElement createLookupElement(String s) {
    return null;
  }

  @Override
  public Collection<String> getSmartVariants(ConvertContext convertContext) {
    String artifactId = MavenArtifactCoordinatesHelper.getId(convertContext).getArtifactId();
    if (!StringUtil.isEmptyOrSpaces(artifactId)) {
      OfflineSearchService searchService = MavenProjectIndicesManager.getInstance(convertContext.getProject()).getOfflineSearchService();

      return searchService.findByTemplate(artifactId, SearchParameters.DEFAULT)
        .stream().filter(p -> artifactId.equals(p.getArtifactId())).map(p -> p.getGroupId()).collect(Collectors.toSet());
    }
    return Collections.emptySet();
  }


}
