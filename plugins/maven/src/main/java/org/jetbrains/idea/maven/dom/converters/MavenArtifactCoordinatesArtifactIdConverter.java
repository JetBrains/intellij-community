/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
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
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.Collections;
import java.util.Set;

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
  protected Set<String> doGetVariants(MavenId id, MavenProjectIndicesManager manager) {
    if (StringUtil.isEmptyOrSpaces(id.getGroupId())) return Collections.emptySet();
    return manager.getArtifactIds(id.getGroupId());
  }

  private static class MavenArtifactInsertHandler implements InsertHandler<LookupElement> {

    public static final InsertHandler<LookupElement> INSTANCE = new MavenArtifactInsertHandler();

    @Override
    public void handleInsert(final InsertionContext context, LookupElement item) {
      if (TemplateManager.getInstance(context.getProject()).getActiveTemplate(context.getEditor()) != null) {
        return; // Don't brake the template.
      }

      context.commitDocument();

      XmlFile xmlFile = (XmlFile)context.getFile();

      PsiElement element = xmlFile.findElementAt(context.getStartOffset());
      XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
      if (tag == null) return;

      XmlTag dependencyTag = tag.getParentTag();

      DomElement domElement = DomManager.getDomManager(context.getProject()).getDomElement(dependencyTag);
      if (!(domElement instanceof MavenDomDependency)) return;

      MavenDomDependency dependency = (MavenDomDependency)domElement;

      String artifactId = item.getLookupString();

      String groupId = dependency.getGroupId().getStringValue();
      if (StringUtil.isEmpty(groupId)) {
        String g = getUniqueGroupIdOrNull(context.getProject(), artifactId);
        if (g != null) {
          dependency.getGroupId().setStringValue(g);
          groupId = g;
        }
        else {
          if (groupId == null) {
            dependency.getGroupId().setStringValue("");
          }

          XmlTag groupIdTag = dependency.getGroupId().getXmlTag();
          context.getEditor().getCaretModel().moveToOffset(groupIdTag.getValue().getTextRange().getStartOffset());

          MavenDependencyCompletionUtil.invokeCompletion(context, CompletionType.SMART);

          return;
        }
      }

      MavenDependencyCompletionUtil.addTypeAndClassifierAndVersion(context, dependency, groupId, artifactId);
    }

    private static String getUniqueGroupIdOrNull(@NotNull Project project, @NotNull String artifactId) {
      MavenProjectIndicesManager manager = MavenProjectIndicesManager.getInstance(project);

      String res = null;

      for (String groupId : manager.getGroupIds()) {
        if (manager.getArtifactIds(groupId).contains(artifactId)) {
          if (res == null) {
            res = groupId;
          }
          else {
            return null; // There are more then one appropriate groupId.
          }
        }
      }

      return res;
    }
  }

}
