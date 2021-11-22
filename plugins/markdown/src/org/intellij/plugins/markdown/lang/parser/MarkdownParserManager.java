// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.parser;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import org.intellij.markdown.MarkdownElementTypes;
import org.intellij.markdown.ast.ASTNode;
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor;
import org.intellij.markdown.parser.MarkdownParser;
import org.intellij.plugins.markdown.extensions.CodeFencePluginFlavourDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

public final class MarkdownParserManager {
  public static final Key<MarkdownFlavourDescriptor> FLAVOUR_DESCRIPTION = Key.create("Markdown.Flavour");

  public static final GFMCommentAwareFlavourDescriptor FLAVOUR = new GFMCommentAwareFlavourDescriptor();
  public static final CodeFencePluginFlavourDescriptor CODE_FENCE_PLUGIN_FLAVOUR = new CodeFencePluginFlavourDescriptor();

  private static final AtomicReference<ParsingInfo> ourLastParsingResult = new AtomicReference<>();

  static {
    //FIXME: Move to dedicated component
    ApplicationManager.getApplication().getMessageBus().connect()
      .subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
        @Override
        public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
          ourLastParsingResult.set(null);
        }
      });
  }

  public static ASTNode parseContent(@NotNull CharSequence buffer) {
    return parseContent(buffer, FLAVOUR);
  }

  public static ASTNode parseContent(@NotNull CharSequence buffer, @NotNull MarkdownFlavourDescriptor flavour) {
    final ParsingInfo info = ourLastParsingResult.get();
    if (info != null && info.myBufferHash == buffer.hashCode() && info.myBuffer.equals(buffer)) {
      return info.myParseResult;
    }

    final ASTNode parseResult = new MarkdownParser(flavour).parse(MarkdownElementTypes.MARKDOWN_FILE, buffer.toString(), false);
    ourLastParsingResult.set(new ParsingInfo(buffer, parseResult));
    return parseResult;
  }

  private static class ParsingInfo {
    @NotNull
    final CharSequence myBuffer;
    final int myBufferHash;
    @NotNull
    final ASTNode myParseResult;

    ParsingInfo(@NotNull CharSequence buffer, @NotNull ASTNode parseResult) {
      myBuffer = buffer;
      myBufferHash = myBuffer.hashCode();
      myParseResult = parseResult;
    }
  }
}
