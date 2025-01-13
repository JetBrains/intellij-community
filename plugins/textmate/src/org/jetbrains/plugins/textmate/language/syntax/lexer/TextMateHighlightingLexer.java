package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorCachingWeigher;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl;
import org.jetbrains.plugins.textmate.regex.joni.JoniRegexFactory;

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
   * @deprecated pass syntax matcher to a constructor
   */
  public TextMateHighlightingLexer(@NotNull TextMateLanguageDescriptor languageDescriptor,
                                   int lineLimit) {
    myLexer = new TextMateLexer(languageDescriptor, new TextMateCachingSyntaxMatcher(new TextMateSyntaxMatcherImpl(new JoniRegexFactory(), new TextMateSelectorCachingWeigher(new TextMateSelectorWeigherImpl()))), lineLimit, false);
  }
  
  public TextMateHighlightingLexer(@NotNull TextMateLanguageDescriptor languageDescriptor,
                                   @NotNull TextMateSyntaxMatcher syntaxMatcher,
                                   int lineLimit) {
    myLexer = new TextMateLexer(languageDescriptor, syntaxMatcher, lineLimit, false);
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

  @Override
  public @Nullable IElementType getTokenType() {
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

  @Override
  public @NotNull CharSequence getBufferSequence() {
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
      Application app = ApplicationManager.getApplication();
      Runnable checkCancelledCallback = app == null || app.isUnitTestMode() ? null : () -> ProgressManager.checkCanceled();
      myLexer.advanceLine(currentLineTokens, checkCancelledCallback);
    }
    updateState(currentLineTokens.poll(), myLexer.getCurrentOffset());
  }

  protected void updateState(@Nullable TextMateLexer.Token token, int fallbackOffset) {
    if (token != null) {
      myTokenType = token.scope == TextMateScope.WHITESPACE ? TokenType.WHITE_SPACE : new TextMateElementType(token.scope);
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
