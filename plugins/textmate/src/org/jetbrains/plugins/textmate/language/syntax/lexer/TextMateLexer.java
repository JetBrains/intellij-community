package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.intellij.textmate.joni.JoniRegexFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateCachingSelectorWeigherKt;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl;
import org.jetbrains.plugins.textmate.regex.CaffeineCachingRegexProvider;
import org.jetbrains.plugins.textmate.regex.RegexProvider;
import org.jetbrains.plugins.textmate.regex.RememberingLastMatchRegexFactory;

import java.util.Queue;

/**
 * @deprecated use {@link TextMateLexerCore}
 */
@Deprecated
public class TextMateLexer {
  private final TextMateLexerCore myLexerCore;

  /**
   * @deprecated use {@link TextMateLexerCore}
   */
  @Deprecated
  public TextMateLexer(@NotNull TextMateLanguageDescriptor languageDescriptor,
                       int lineLimit) {
    RegexProvider regexProvider = new CaffeineCachingRegexProvider(new RememberingLastMatchRegexFactory(new JoniRegexFactory()));
    TextMateSelectorWeigher weigher = TextMateCachingSelectorWeigherKt.caching(new TextMateSelectorWeigherImpl());
    TextMateSyntaxMatcher syntaxMatcher = TextMateCachingSyntaxMatcherCoreKt.caching(new TextMateSyntaxMatcherImpl(regexProvider, weigher));
    myLexerCore = new TextMateLexerCore(languageDescriptor, syntaxMatcher, lineLimit, false);
  }

  public void init(CharSequence text, int startOffset) {
    myLexerCore.init(text, startOffset);
  }

  /**
   * @deprecated use {@link TextMateLexerCore}
   */
  @Deprecated
  public void advanceLine(@NotNull Queue<Token> output) {
    output.addAll(ContainerUtil.map(myLexerCore.advanceLine(null),
                                    token -> new Token(token.getScope(), token.getStartOffset(), token.getEndOffset(), token.getRestartable())));
  }

  /**
   * @deprecated use {@link TextMateLexerCore}
   */
  @Deprecated
  public int getCurrentOffset() {
    return myLexerCore.getCurrentOffset();
  }

  public static final class Token {
    public final TextMateScope scope;
    public final int startOffset;
    public final int endOffset;
    public final boolean restartable;

    private Token(TextMateScope scope, int startOffset, int endOffset, boolean restartable) {
      this.scope = scope;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      this.restartable = restartable;
    }
  }
}
