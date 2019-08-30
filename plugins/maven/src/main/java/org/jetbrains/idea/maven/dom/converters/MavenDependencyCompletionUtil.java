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
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.model.*;
import org.jetbrains.idea.maven.indices.IndicesBundle;
import org.jetbrains.idea.maven.indices.MavenArtifactSearchResult;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import static com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER;
import static com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER_TRIMMED;
import static org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem.Type.PROJECT;

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

  public static boolean isInsideManagedDependency(@NotNull MavenDomShortArtifactCoordinates dependency) {
    DomElement parent = dependency.getParent();
    if (!(parent instanceof MavenDomDependencies)) return false;

    return parent.getParent() instanceof MavenDomDependencyManagement;
  }

  public static void invokeCompletion(@NotNull final InsertionContext context, final CompletionType completionType) {
    context.setLaterRunnable(
      () -> new CodeCompletionHandlerBase(completionType).invokeCompletion(context.getProject(), context.getEditor()));
  }

  public static LookupElementBuilder lookupElement(MavenDependencyCompletionItem item, String lookup) {
    return LookupElementBuilder.create(item, lookup)
      .withIcon(getIcon(item.getType()));
  }

  public static MavenDependencyCompletionItem getMaxIcon(MavenArtifactSearchResult searchResult) {
    return Collections.max(Arrays.asList(searchResult.getSearchResults().getItems()),
                           Comparator.comparing(r -> {
                             if (r.getType() == null) {
                               return Integer.MIN_VALUE;
                             }
                             return r.getType().getWeight();
                           }));
  }

  public static LookupElementBuilder lookupElement(MavenRepositoryArtifactInfo info) {
    return lookupElement(info, getPresentableText(info));
  }

  public static LookupElementBuilder lookupElement(MavenRepositoryArtifactInfo info, String presentableText) {
    LookupElementBuilder elementBuilder = LookupElementBuilder.create(info, getLookupString(info.getItems()[0]))
      .withPresentableText(presentableText);
    if (info.getItems().length == 1) {
      return elementBuilder.withIcon(getIcon(info.getItems()[0].getType()));
    }
    return elementBuilder;
  }

  public static String getPresentableText(MavenRepositoryArtifactInfo info) {
    if (info.getItems().length == 1) {
      return getLookupString(info.getItems()[0]);
    }
    String key = "maven.dependency.completion.presentable";
    return IndicesBundle.message(key, info.getGroupId(), info.getArtifactId());
  }

  @Nullable
  public static Icon getIcon(@Nullable MavenDependencyCompletionItem.Type type) {
    if (type == PROJECT) {
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
