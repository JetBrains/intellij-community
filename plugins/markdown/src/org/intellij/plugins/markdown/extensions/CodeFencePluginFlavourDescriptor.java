// Copyright 2000-2018 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package org.intellij.plugins.markdown.extensions;

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.markdown.IElementType;
import org.intellij.markdown.MarkdownElementTypes;
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor;
import org.intellij.markdown.html.GeneratingProvider;
import org.intellij.plugins.markdown.extensions.plantuml.PlantUMLProvider;
import org.intellij.plugins.markdown.ui.preview.MarkdownCodeFenceGeneratingProvider;
import org.intellij.plugins.markdown.ui.preview.MarkdownCodeFencePluginCacheCollector;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class CodeFencePluginFlavourDescriptor extends CommonMarkFlavourDescriptor {
  @NotNull
  public Map<IElementType, GeneratingProvider> createHtmlGeneratingProviders(@NotNull MarkdownCodeFencePluginCacheCollector cacheCollector) {
    return ContainerUtil.newHashMap(Pair.create(MarkdownElementTypes.CODE_FENCE,
                                                new MarkdownCodeFenceGeneratingProvider(
                                                  new MarkdownCodeFencePluginGeneratingProvider[]{new PlantUMLProvider(cacheCollector)})));
  }
}