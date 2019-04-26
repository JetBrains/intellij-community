// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.onlinecompletion.DependencySearchService;
import org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

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
  protected Set<String> doGetVariants(MavenId id, DependencySearchService searchService) {
    Set<String> result =
      MavenProjectsManager.getInstance(searchService.getProject()).getProjects().stream().map(p -> p.getMavenId().getGroupId())
        .collect(Collectors.toSet());
    result.addAll(searchService.findGroupCandidates(id, SearchParameters.DEFAULT).stream()
                    .map(s -> s.getGroupId()).collect(Collectors.toSet()));
    return result;
  }

  private void addProjectGroupsToResult(Set<String> result, Project project) {
    Set<String> projectGroups =
      MavenProjectsManager.getInstance(project).getProjects().stream().map(p -> p.getMavenId().getGroupId()).collect(Collectors.toSet());
    result.addAll(projectGroups);
  }


  @Nullable
  @Override
  public LookupElement createLookupElement(String s) {
    LookupElementBuilder res = LookupElementBuilder.create(s);
    res = res.withInsertHandler(MavenGroupIdInsertHandler.INSTANCE);
    return res;
  }

  @Override
  public Collection<String> getSmartVariants(ConvertContext convertContext) {
    String artifactId = MavenArtifactCoordinatesHelper.getId(convertContext).getArtifactId();
    if (!StringUtil.isEmptyOrSpaces(artifactId)) {
      DependencySearchService searchService = MavenProjectIndicesManager.getInstance(convertContext.getProject()).getSearchService();

      return searchService.findByTemplate(artifactId, SearchParameters.DEFAULT)
        .stream().filter(p -> artifactId.equals(p.getArtifactId())).map(p -> p.getGroupId()).collect(Collectors.toSet());
    }
    return Collections.emptySet();
  }

  private static class MavenGroupIdInsertHandler implements InsertHandler<LookupElement> {

    public static final InsertHandler<LookupElement> INSTANCE = new MavenGroupIdInsertHandler();

    @Override
    public void handleInsert(@NotNull final InsertionContext context, @NotNull LookupElement item) {
      if (TemplateManager.getInstance(context.getProject()).getActiveTemplate(context.getEditor()) != null) {
        return; // Don't brake the template.
      }

      context.commitDocument();

      PsiFile contextFile = context.getFile();
      if (!(contextFile instanceof XmlFile)) return;

      XmlFile xmlFile = (XmlFile)contextFile;

      PsiElement element = xmlFile.findElementAt(context.getStartOffset());
      XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
      if (tag == null) return;

      XmlTag dependencyTag = tag.getParentTag();

      DomElement domElement = DomManager.getDomManager(context.getProject()).getDomElement(dependencyTag);
      if (!(domElement instanceof MavenDomDependency)) return;

      MavenDomDependency dependency = (MavenDomDependency)domElement;

      String artifactId = dependency.getArtifactId().getStringValue();
      if (StringUtil.isEmpty(artifactId)) return;

      MavenDependencyCompletionUtil.addTypeAndClassifierAndVersion(context, dependency, item.getLookupString(), artifactId);
    }
  }
}
