/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.intellij.plugins.markdown.lang;

import com.intellij.psi.tree.IElementType;
import org.intellij.markdown.MarkdownElementTypes;
import org.intellij.markdown.MarkdownTokenTypes;
import org.intellij.markdown.flavours.gfm.GFMTokenTypes;
import org.intellij.plugins.markdown.lang.stubs.impl.MarkdownHeaderStubElementType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MarkdownElementType extends IElementType {

  @NotNull
  private static final Map<org.intellij.markdown.IElementType, IElementType> markdownToPlatformTypeMap =
    new ConcurrentHashMap<>();
  @NotNull
  private static final Map<IElementType, org.intellij.markdown.IElementType> platformToMarkdownTypeMap =
    new ConcurrentHashMap<>();

  public MarkdownElementType(@NotNull @NonNls String debugName) {
    super(debugName, MarkdownLanguage.INSTANCE);
  }

  @Override
  public String toString() {
    return MessageFormat.format("Markdown:{0}", super.toString());
  }

  @Contract("null -> null; !null -> !null")
  public static IElementType platformType(@Nullable org.intellij.markdown.IElementType markdownType) {
    if (markdownType == null) {
      return null;
    }

    if (markdownToPlatformTypeMap.containsKey(markdownType)) {
      return markdownToPlatformTypeMap.get(markdownType);
    }

    synchronized (platformToMarkdownTypeMap) {
      return markdownToPlatformTypeMap.computeIfAbsent(markdownType, type -> {
        final IElementType result;
        if (type == MarkdownElementTypes.PARAGRAPH
            || type == MarkdownTokenTypes.ATX_CONTENT
            || type == MarkdownTokenTypes.SETEXT_CONTENT
            || type == GFMTokenTypes.CELL) {
          result = new MarkdownLazyElementType(type.toString());
        }
        else if (isHeaderElementType(type)) {
          result = new MarkdownHeaderStubElementType(type.toString());
        }
        else {
          result = new MarkdownElementType(type.toString());
        }
        platformToMarkdownTypeMap.put(result, type);
        return result;
      });
    }
  }

  private static boolean isHeaderElementType(@NotNull org.intellij.markdown.IElementType markdownType) {
    return markdownType == MarkdownElementTypes.ATX_1 ||
           markdownType == MarkdownElementTypes.ATX_2 ||
           markdownType == MarkdownElementTypes.ATX_3 ||
           markdownType == MarkdownElementTypes.ATX_4 ||
           markdownType == MarkdownElementTypes.ATX_5 ||
           markdownType == MarkdownElementTypes.ATX_6 ||
           markdownType == MarkdownElementTypes.SETEXT_1 ||
           markdownType == MarkdownElementTypes.SETEXT_2;
  }

  @Contract("!null -> !null")
  public static org.intellij.markdown.IElementType markdownType(@Nullable IElementType platformType) {
    if (platformType == null) {
      return null;
    }
    var result = platformToMarkdownTypeMap.get(platformType);
    if (result != null) {
      return result;
    }
    synchronized (platformToMarkdownTypeMap) {
      return platformToMarkdownTypeMap.get(platformType);
    }
  }
}
