// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.OffsetKey;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.TemplateManager;
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

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;


public class MavenArtifactCoordinatesArtifactIdConverter extends MavenArtifactCoordinatesConverter {
  @Override
  protected boolean doIsValid(MavenId id, MavenProjectIndicesManager manager, ConvertContext context) {
    if (StringUtil.isEmpty(id.getGroupId()) || StringUtil.isEmpty(id.getArtifactId())) return false;
    if (manager.hasArtifactId(id.getGroupId(), id.getArtifactId())) {
      return true;
    }

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

  @Nullable
  @Override
  public LookupElement createLookupElement(String s) {
    LookupElementBuilder res = LookupElementBuilder.create(s);
    res = res.withInsertHandler(MavenArtifactInsertHandler.INSTANCE);
    return res;
  }

  @Override
  protected Set<String> doGetVariants(MavenId id, DependencySearchService searchService) {
    if (StringUtil.isEmptyOrSpaces(id.getGroupId())) return Collections.emptySet();
    return searchService.findArtifactCandidates(id, SearchParameters.DEFAULT).stream().map(s -> s.getArtifactId())
      .collect(Collectors.toSet());
  }

  private static class MavenArtifactInsertHandler implements InsertHandler<LookupElement> {

    public static final InsertHandler<LookupElement> INSTANCE = new MavenArtifactInsertHandler();

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

      String artifactId = item.getLookupString();

      String groupId = dependency.getGroupId().getStringValue();
      OffsetKey startRef = context.trackOffset(context.getStartOffset(), false);
      int len = context.getTailOffset() - context.getStartOffset();
      if (StringUtil.isEmpty(groupId)) {
        XmlTag groupIdTag = dependency.getGroupId().getXmlTag();
        if (groupIdTag == null) {
          dependency.getGroupId().setStringValue("");
        }
        context.getEditor().getCaretModel().moveToOffset(groupIdTag.getValue().getTextRange().getStartOffset());
        MavenDependencyCompletionUtil.invokeCompletion(context, CompletionType.SMART);
        return;
      }
      int offset = context.getOffset(startRef);
      context.getDocument().replaceString(offset, offset + len, artifactId);
      context.commitDocument();

      MavenDependencyCompletionUtil.addTypeAndClassifierAndVersion(context, dependency, groupId, artifactId);
    }
  }
}
