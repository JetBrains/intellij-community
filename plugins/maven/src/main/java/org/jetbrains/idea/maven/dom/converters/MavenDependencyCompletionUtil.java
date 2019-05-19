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
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.model.*;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.idea.maven.onlinecompletion.DependencySearchService;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;

import javax.swing.*;
import java.util.List;

import static com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER;
import static com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER_TRIMMED;

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

    DependencySearchService service = MavenProjectIndicesManager.getInstance(project).getSearchService();

    List<MavenDependencyCompletionItem> versions = service.findAllVersions(new MavenDependencyCompletionItem(groupId, artifactId, null));
    if (versions.size() == 1) {
      dependency.getVersion().setStringValue(ContainerUtil.getFirstItem(versions).getVersion());
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

  public static LookupElementBuilder lookupElement(MavenDependencyCompletionItem item, String lookup) {
    return LookupElementBuilder.create(item, lookup)
      .withIcon(getIcon(item.getType()));
  }

  public static LookupElementBuilder lookupElement(MavenDependencyCompletionItem item) {
    return lookupElement(item, getLookupString(item));
  }

  @Nullable
  public static Icon getIcon(@Nullable MavenDependencyCompletionItem.Type type) {
    if (type == null) {
      return null;
    }
    switch (type) {
      case REMOTE:
        return AllIcons.Nodes.PpWeb;
      case LOCAL:
        return AllIcons.Nodes.PpLibFolder;
      case CACHED_ERROR:
        return AllIcons.Nodes.PpInvalid;
      case PROJECT:
        return AllIcons.Nodes.Module;
    }

    return null;
  }

  public static String getLookupString(MavenDependencyCompletionItem description) {
    StringBuilder builder = new StringBuilder(description.getGroupId());
    if (description.getArtifactId() == null) {
      builder.append(":...");
    }
    else {
      builder.append(":").append(description.getArtifactId());
      if (description.getPackaging() != null) {
        builder.append(":").append(description.getPackaging());
      }
      if (description.getVersion() != null) {
        builder.append(":").append(description.getVersion());
      }
      else {
        builder.append(":...");
      }
    }
    return builder.toString();
  }

  public static @NotNull
  String removeDummy(@Nullable String str) {
    if (str == null) {
      return "";
    }
    return StringUtil.trim(str.replace(DUMMY_IDENTIFIER, "").replace(DUMMY_IDENTIFIER_TRIMMED, ""));
  }
}
