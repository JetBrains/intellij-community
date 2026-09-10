package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.lexer.RestartableLexer;
import com.intellij.lexer.TokenIterator;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.textmate.joni.JoniRegexFactory;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateCachingSelectorWeigherKt;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl;
import org.jetbrains.plugins.textmate.regex.CaffeineCachingRegexProvider;
import org.jetbrains.plugins.textmate.regex.RegexProvider;
import org.jetbrains.plugins.textmate.regex.RememberingLastMatchRegexFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * A {@link RestartableLexer} over a TextMate grammar. A line start is a restartable state, so
 * {@link com.intellij.openapi.editor.ex.util.LexerEditorHighlighter} re-lexes only the edited line and the
 * lines it changes, instead of walking back to the last token at the root scope.
 * <p>
 * A state is the id of a {@link TextMateLexerContinuation} in {@link #myContinuations}. Continuations compare
 * by value across lexer runs, so the highlighter can re-synchronise its segments after an edit.
 * <p>
 * A line start inside a back-reference block, such as a heredoc, is not restartable. Its continuation holds
 * the text the lexer matched, so an edit makes a new continuation and the id of the old one is never used
 * again, see {@link TextMateLexerContinuation#isStable()}. An edit there re-lexes from the start of the block.
 */
public class TextMateHighlightingLexer extends LexerBase implements RestartableLexer {
  /**
   * The state of a token that does not start a line. The lexer cannot restart there, because
   * {@link TextMateLexerCore#advanceLine} treats its start offset as the beginning of a line.
   * It is also the state of a line start that {@link ContinuationStorage#getId} gave no id.
   */
  private static final int NOT_RESTARTABLE = 0xFFFF;
  /** A state must fit in the 16 bits {@link TextMateLexerDataStorage} gives it, and leave room for the sentinel. */
  private static final int MAX_STATES = NOT_RESTARTABLE;
  private static final int START_STATE = 0;

  private final TextMateLexerCore myLexer;
  private final Queue<TextmateToken> currentLineTokens = new LinkedList<>();
  private final ContinuationStorage myContinuations = new ContinuationStorage();

  private CharSequence myBuffer;
  private int myEndOffset;
  private int myCurrentOffset;

  private IElementType myTokenType;
  private int myTokenStart;
  private int myTokenEnd;
  private int myState;
  /** The state of the line whose tokens are queued in {@link #currentLineTokens}. */
  private int myLineState;
  /** True while the head of {@link #currentLineTokens} is the first token of its line. */
  private boolean myAtLineStart;

  /**
   * @deprecated pass syntax matcher to a constructor
   */
  @Deprecated
  public TextMateHighlightingLexer(@NotNull TextMateLanguageDescriptor languageDescriptor,
                                   int lineLimit) {
    this(languageDescriptor, defaultSyntaxMatcher(), lineLimit);
  }

  public TextMateHighlightingLexer(@NotNull TextMateLanguageDescriptor languageDescriptor,
                                   @NotNull TextMateSyntaxMatcher syntaxMatcher,
                                   int lineLimit) {
    myLexer = new TextMateLexerCore(languageDescriptor, syntaxMatcher, lineLimit, false);
    // reserve START_STATE for the root continuation, so getStartState() is stable
    myContinuations.getId(myLexer.rootContinuation());
  }

  /**
   * The application can appear after the lexer, so the callback is resolved for every line.
   */
  private static @Nullable Runnable checkCancelledCallback() {
    Application app = ApplicationManager.getApplication();
    return app == null || app.isUnitTestMode() ? null : () -> ProgressManager.checkCanceled();
  }

  private static @NotNull TextMateSyntaxMatcher defaultSyntaxMatcher() {
    RegexProvider regexProvider = new CaffeineCachingRegexProvider(new RememberingLastMatchRegexFactory(new JoniRegexFactory()));
    TextMateSelectorWeigher weigher = TextMateCachingSelectorWeigherKt.caching(new TextMateSelectorWeigherImpl());
    return TextMateCachingSyntaxMatcherCoreKt.caching(new TextMateSyntaxMatcherImpl(regexProvider, weigher));
  }

  @Override
  public int getStartState() {
    return START_STATE;
  }

  @Override
  public boolean isRestartableState(int state) {
    return state >= 0 && state < myContinuations.size();
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState, TokenIterator tokenIterator) {
    start(buffer, startOffset, endOffset, initialState);
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myCurrentOffset = startOffset;
    myTokenStart = startOffset;
    myEndOffset = endOffset;
    myState = START_STATE;
    currentLineTokens.clear();
    myLexer.init(buffer, startOffset, continuationOf(initialState));
    advance();
  }

  private @NotNull TextMateLexerContinuation continuationOf(int state) {
    return isRestartableState(state) ? myContinuations.get(state) : myLexer.rootContinuation();
  }

  @Override
  public int getState() {
    return myState;
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
      // the continuation must be read before the line is lexed: it is the state to restart this line with
      myLineState = myContinuations.getId(myLexer.getContinuation());
      myAtLineStart = true;
      currentLineTokens.addAll(myLexer.advanceLine(checkCancelledCallback()));
    }
    updateState(currentLineTokens.poll(), myLexer.getCurrentOffset());
  }

  protected void updateState(@Nullable TextmateToken token, int fallbackOffset) {
    if (token != null) {
      myTokenType = token.getScope() == TextMateScope.WHITESPACE ? TokenType.WHITE_SPACE : new TextMateElementType(token.getScope());
      myTokenStart = token.getStartOffset();
      myTokenEnd = Math.min(token.getEndOffset(), myEndOffset);
      myCurrentOffset = token.getEndOffset();
      myState = myAtLineStart ? myLineState : NOT_RESTARTABLE;
    }
    else {
      myTokenType = null;
      myTokenStart = fallbackOffset;
      myTokenEnd = fallbackOffset;
      myCurrentOffset = fallbackOffset;
      myState = START_STATE;
    }
    myAtLineStart = false;
  }

  /**
   * A two-way table between a {@link TextMateLexerContinuation} and the lexer state that stands for it.
   * The id is the index in {@link #continuations}, and {@link #continuationIds} is the reverse index.
   * <p>
   * The table only grows, because an id must keep its meaning for as long as the highlighter holds it in
   * its segment data. It therefore accepts a stable continuation only, and it holds at most
   * {@link TextMateHighlightingLexer#MAX_STATES} entries.
   */
  private static class ContinuationStorage {
    private final Object2IntMap<TextMateLexerContinuation> continuationIds = new Object2IntOpenHashMap<>();
    private final List<TextMateLexerContinuation> continuations = new ArrayList<>();

    int size() {
      return continuations.size();
    }

    TextMateLexerContinuation get(int id) {
      return continuations.get(id);
    }

    /**
     * @return the id of {@code continuation}, or {@link TextMateHighlightingLexer#NOT_RESTARTABLE} when the continuation gets no id.
     * No id only costs incrementality, so the lexer keeps working.
     * <p>
     * An unstable continuation gets no id. It is new after every keystroke, and a table of those would grow
     * with the edits until it fills up and no line is restartable.
     */
    int getId(@NotNull TextMateLexerContinuation continuation) {
      int existing = continuationIds.getOrDefault(continuation, -1);
      if (existing != -1) {
        return existing;
      }
      if (!continuation.isStable() || continuations.size() >= MAX_STATES) {
        return NOT_RESTARTABLE;
      }
      int id = continuations.size();
      continuations.add(continuation);
      continuationIds.put(continuation, id);
      return id;
    }
  }
}
