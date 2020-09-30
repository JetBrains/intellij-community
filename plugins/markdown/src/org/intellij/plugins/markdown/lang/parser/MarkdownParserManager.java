package org.intellij.plugins.markdown.lang.parser;

import com.intellij.openapi.util.Key;
import org.intellij.markdown.MarkdownElementTypes;
import org.intellij.markdown.ast.ASTNode;
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor;
import org.intellij.markdown.parser.MarkdownParser;
import org.intellij.plugins.markdown.extensions.CodeFencePluginFlavourDescriptor;
import org.jetbrains.annotations.NotNull;

public class MarkdownParserManager {
  public static final Key<MarkdownFlavourDescriptor> FLAVOUR_DESCRIPTION = Key.create("Markdown.Flavour");

  public static final GFMCommentAwareFlavourDescriptor FLAVOUR = new GFMCommentAwareFlavourDescriptor();
  public static final CodeFencePluginFlavourDescriptor CODE_FENCE_PLUGIN_FLAVOUR = new CodeFencePluginFlavourDescriptor();

  private static final ThreadLocal<ParsingInfo> ourLastParsingResult = new ThreadLocal<>();

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
