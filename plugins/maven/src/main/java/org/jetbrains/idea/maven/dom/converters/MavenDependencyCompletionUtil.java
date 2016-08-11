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
package org.jetbrains.idea.maven.dom.converters;

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.model.*;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;

import java.util.Set;

/**
 * @author Sergey Evdokimov
 */
public class MavenDependencyCompletionUtil {

  public static MavenDomDependency findManagedDependency(MavenDomProjectModel domModel, Project project,
    @NotNull final String groupId, @NotNull final String artifactId) {

    final Ref<MavenDomDependency> ref = new Ref<>();

    MavenDomProjectProcessorUtils.processDependenciesInDependencyManagement(domModel,
                                                                            dependency -> {
                                                                              if (groupId.equals(dependency.getGroupId().getStringValue())
                                                                                  &&
                                                                                  artifactId.equals(
                                                                                    dependency.getArtifactId().getStringValue())) {
                                                                                ref.set(dependency);
                                                                                return true;
                                                                              }
                                                                              return false;
                                                                            }, project);

    return ref.get();
  }

  private static boolean isInsideManagedDependency(MavenDomArtifactCoordinates dependency) {
    DomElement parent = dependency.getParent();
    if (!(parent instanceof MavenDomDependencies)) return false;

    return parent.getParent() instanceof MavenDomDependencyManagement;
  }

  public static void addTypeAndClassifierAndVersion(@NotNull InsertionContext context,
                                                    @NotNull MavenDomDependency dependency,
                                                    @NotNull String groupId, @NotNull String artifactId) {
    if (!StringUtil.isEmpty(dependency.getVersion().getStringValue())) return;

    Project project = context.getProject();

    if (!isInsideManagedDependency(dependency)) {
      MavenDomProjectModel model = DomUtil.<MavenDomProjectModel>getFileElement(dependency).getRootElement();
      MavenDomDependency managedDependency = findManagedDependency(model, project, groupId, artifactId);
      if (managedDependency != null) {
        if (dependency.getClassifier().getXmlTag() == null && dependency.getType().getXmlTag() == null) {
          String classifier = managedDependency.getClassifier().getRawText();
          if (StringUtil.isNotEmpty(classifier)) {
            dependency.getClassifier().setStringValue(classifier);
          }
          String type = managedDependency.getType().getRawText();
          if (StringUtil.isNotEmpty(type)) {
            dependency.getType().setStringValue(type);
          }
        }
        return;
      }
    }

    MavenProjectIndicesManager manager = MavenProjectIndicesManager.getInstance(project);

    Set<String> versions = manager.getVersions(groupId, artifactId);
    if (versions.size() == 1) {
      dependency.getVersion().setStringValue(ContainerUtil.getFirstItem(versions));
      return;
    }

    dependency.getVersion().setStringValue("");

    int versionPosition = dependency.getVersion().getXmlTag().getValue().getTextRange().getStartOffset();

    context.getEditor().getCaretModel().moveToOffset(versionPosition);

    if (versions.size() > 0) {
      invokeCompletion(context, CompletionType.BASIC);
    }
  }

  public static void invokeCompletion(@NotNull final InsertionContext context, final CompletionType completionType) {
    context.setLaterRunnable(
      () -> new CodeCompletionHandlerBase(completionType).invokeCompletion(context.getProject(), context.getEditor()));
  }

}
