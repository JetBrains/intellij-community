// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.ConvertContext;
import org.jetbrains.idea.maven.indices.MavenIndicesManager;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenArtifactUtilKt;
import org.jetbrains.idea.reposearch.DependencySearchService;

import java.util.Collections;
import java.util.Set;


public class MavenArtifactCoordinatesArtifactIdConverter extends MavenArtifactCoordinatesConverter {
  @Override
  protected boolean doIsValid(MavenId id, MavenIndicesManager manager, ConvertContext context) {
    if (StringUtil.isEmpty(id.getGroupId()) || StringUtil.isEmpty(id.getArtifactId())) return false;

    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(context.getProject());
    if (StringUtil.isNotEmpty(id.getVersion())) {
      if (projectsManager.findProject(id) != null) return true;
    } else {
      for (MavenProject project : projectsManager.getProjects()) {
        MavenId mavenId = project.getMavenId();
        if (id.getGroupId().equals(mavenId.getGroupId()) && id.getArtifactId().equals(mavenId.getArtifactId())) return true;
      }
    }

    // Check if artifact was found on importing.
    VirtualFile projectFile = getMavenProjectFile(context);
    MavenProject mavenProject = projectFile == null ? null : projectsManager.findProject(projectFile);
    if (mavenProject != null) {
      for (MavenArtifact artifact : mavenProject.findDependencies(id.getGroupId(), id.getArtifactId())) {
        if (MavenArtifactUtilKt.resolved(artifact)) {
          return true;
        }
      }
    }

    return manager.hasLocalArtifactId(id.getGroupId(), id.getArtifactId());
  }

  @Override
  protected Set<String> doGetVariants(MavenId id, DependencySearchService searchService) {
    return Collections.emptySet();
  }

}
