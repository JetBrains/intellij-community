package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.textmate.joni.JoniRegexFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateCachingSelectorWeigherKt;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl;
import org.jetbrains.plugins.textmate.regex.CaffeineCachingRegexProvider;
import org.jetbrains.plugins.textmate.regex.RegexProvider;
import org.jetbrains.plugins.textmate.regex.RememberingLastMatchRegexFactory;

import java.util.LinkedList;
import java.util.Queue;

public class TextMateHighlightingLexer extends LexerBase {
  private final TextMateLexerCore myLexer;
  private final Queue<TextmateToken> currentLineTokens = new LinkedList<>();

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
    RegexProvider regexProvider = new CaffeineCachingRegexProvider(new RememberingLastMatchRegexFactory(new JoniRegexFactory()));
    TextMateSelectorWeigher weigher = TextMateCachingSelectorWeigherKt.caching(new TextMateSelectorWeigherImpl());
    TextMateSyntaxMatcher syntaxMatcher = TextMateCachingSyntaxMatcherCoreKt.caching(new TextMateSyntaxMatcherImpl(regexProvider, weigher));
    myLexer = new TextMateLexerCore(languageDescriptor, syntaxMatcher, lineLimit, false);
  }
  
  public TextMateHighlightingLexer(@NotNull TextMateLanguageDescriptor languageDescriptor,
                                   @NotNull TextMateSyntaxMatcher syntaxMatcher,
                                   int lineLimit) {
    myLexer = new TextMateLexerCore(languageDescriptor, syntaxMatcher, lineLimit, false);
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
      currentLineTokens.addAll(myLexer.advanceLine(checkCancelledCallback));
    }
    updateState(currentLineTokens.poll(), myLexer.getCurrentOffset());
  }

  protected void updateState(@Nullable TextmateToken token, int fallbackOffset) {
    if (token != null) {
      myTokenType = token.getScope() == TextMateScope.WHITESPACE ? TokenType.WHITE_SPACE : new TextMateElementType(token.getScope());
      myTokenStart = token.getStartOffset();
      myTokenEnd = Math.min(token.getEndOffset(), myEndOffset);
      myCurrentOffset = token.getEndOffset();
      myRestartable = token.getRestartable();
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
