package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor;

import java.util.LinkedList;
import java.util.Queue;

public class TextMateHighlightingLexer extends LexerBase {
  private final TextMateLexer myLexer;
  private final Queue<TextMateLexer.Token> currentLineTokens = new LinkedList<>();

  private CharSequence myBuffer;
  private int myEndOffset;
  private int myCurrentOffset;

  private IElementType myTokenType;
  private int myTokenStart;
  private int myTokenEnd;
  private boolean myRestartable;

  /**
   * @deprecated use {@link TextMateHighlightingLexer#TextMateHighlightingLexer(TextMateLanguageDescriptor, int)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public TextMateHighlightingLexer(CharSequence scopeName, SyntaxNodeDescriptor languageRootSyntaxNode) {
    myLexer = new TextMateLexer(new TextMateLanguageDescriptor(scopeName, languageRootSyntaxNode),
                                Registry.get("textmate.line.highlighting.limit").asInteger());
  }

  public TextMateHighlightingLexer(@NotNull TextMateLanguageDescriptor languageDescriptor, int lineLimit) {
    myLexer = new TextMateLexer(languageDescriptor, lineLimit);
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myCurrentOffset = startOffset;
    myTokenStart = startOffset;
    myEndOffset = endOffset;
    currentLineTokens.clear();
    myLexer.init(myBuffer, startOffset);
    advance();
  }

  @Override
  public int getState() {
    return myRestartable ? 0 : 1;
  }

  @Nullable
  @Override
  public IElementType getTokenType() {
    return myTokenType;
  }

  @Override
  public int getTokenStart() {
    return myTokenStart;
  }

  @Override
  public int getTokenEnd() {
    return myTokenEnd;
  }

  @NotNull
  @Override
  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  @Override
  public int getBufferEnd() {
    return myEndOffset;
  }

  @Override
  public void advance() {
    if (myCurrentOffset >= myEndOffset) {
      updateState(null, myEndOffset);
      return;
    }

    if (currentLineTokens.isEmpty()) {
      myLexer.advanceLine(currentLineTokens);
    }
    updateState(currentLineTokens.poll(), myLexer.getCurrentOffset());
  }

  protected void updateState(@Nullable TextMateLexer.Token token, int fallbackOffset) {
    if (token != null) {
      myTokenType = new TextMateElementType(token.selector);
      myTokenStart = token.startOffset;
      myTokenEnd = Math.min(token.endOffset, myEndOffset);
      myCurrentOffset = token.endOffset;
      myRestartable = token.restartable;
    }
    else {
      myTokenType = null;
      myTokenStart = fallbackOffset;
      myTokenEnd = fallbackOffset;
      myCurrentOffset = fallbackOffset;
      myRestartable = true;
    }
  }
}
