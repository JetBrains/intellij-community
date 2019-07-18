package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.google.common.collect.HashMultiset;
import com.intellij.lexer.LexerBase;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.util.DataStorage;
import com.intellij.openapi.editor.ex.util.DataStorageFactory;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh;
import org.jetbrains.plugins.textmate.plist.Plist;
import org.jetbrains.plugins.textmate.regex.MatchData;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class TextMateHighlightingLexer extends LexerBase implements DataStorageFactory {
  private static final Logger LOG = Logger.getInstance(TextMateHighlightingLexer.class);
  private final Stack<TextMateLexerState> myStates = new Stack<>();
  private final Queue<Token> currentLineTokens = new LinkedList<>();
  private final String languageScopeName;

  private CharSequence myBuffer;
  private int myEndOffset;
  private int myCurrentOffset;
  private Token myCurrentToken;
  private final Stack<String> openedTags = new Stack<>();
  private final TextMateLexerState myLanguageInitialState;

  /**
   * Count of {@link this#lastSuccessState} that can be occurred again without offset changing.
   * If {@link this#lastSuccessStateOccursCount} reaches {@link this#MAX_LOOPS_COUNT}
   * then lexing of current line stops and lexer moved to the EOL.
   */
  private static final int MAX_LOOPS_COUNT = 10;

  /**
   * State of the moment when currentOffset had been changed last time.
   * Becomes null on each new line.
   */
  private ArrayList<TextMateLexerState> lastSuccessState;

  /**
   * How many times the {@link this#lastSuccessState} repeated since last offset changing.
   */
  private int lastSuccessStateOccursCount;

  public TextMateHighlightingLexer(String scopeName, SyntaxNodeDescriptor languageRootSyntaxNode) {
    languageScopeName = scopeName;
    myLanguageInitialState = TextMateLexerState.notMatched(languageRootSyntaxNode);
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myCurrentOffset = startOffset;
    myEndOffset = endOffset;
    currentLineTokens.clear();
    initState();
    advance();
  }

  private void initState() {
    myStates.clear();
    myStates.push(myLanguageInitialState);
    openedTags.clear();
    openedTags.push(languageScopeName);
    setLastSuccessState(null);
  }

  @Override
  public int getState() {
    return myCurrentToken != null ? myCurrentToken.state : 0;
  }

  @Nullable
  @Override
  public IElementType getTokenType() {
    return myCurrentToken != null ? myCurrentToken.type : null;
  }

  @Override
  public int getTokenStart() {
    return myCurrentToken != null ? myCurrentToken.startOffset : myEndOffset;
  }

  @Override
  public int getTokenEnd() {
    return myCurrentToken != null ? Math.min(myCurrentToken.endOffset, myEndOffset) : myEndOffset;
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
      myCurrentToken = null;
      return;
    }

    int endLineOffset = myCurrentOffset;
    if (currentLineTokens.isEmpty()) {
      restoreValidStateBeforeNewLine();
      int startLineOffset = myCurrentOffset;
      while (endLineOffset < myEndOffset) {
        if (myBuffer.charAt(endLineOffset) == '\n') {
          endLineOffset++;
          break;
        }
        endLineOffset++;
      }
      parseLine(startLineOffset, endLineOffset);
    }
    myCurrentToken = currentLineTokens.poll();
    myCurrentOffset = myCurrentToken != null ? myCurrentToken.endOffset : endLineOffset;
  }

  /**
   * not really needed when lexer works fine,
   * but when lexer fails and remove language scope tag from stack,
   * we want to restore valid state in order to parse following line properly.
   */
  private void restoreValidStateBeforeNewLine() {
    if (openedTags.empty()) {
      openedTags.push(languageScopeName);
    }
  }

  private void parseLine(int startLineOffset, int endLineOffset) {
    int linePosition = 0;
    String line = myBuffer.subSequence(startLineOffset, endLineOffset).toString();
    if (!line.endsWith("\n")) {
      line += "\n";
    }
    LOG.debug("Highlighting line: " + line);
    final HashMultiset<List<TextMateLexerState>> localStates = HashMultiset.create();
    while (true) {
      final TextMateLexerState lexerState = myStates.peek();
      if (lexerState.syntaxRule.getStringAttribute(Constants.WHILE_KEY) != null
          && !SyntaxMatchUtils.matchStringRegex(Constants.WHILE_KEY, line, lexerState, linePosition).matched()) {
        closeScopeSelector(linePosition + startLineOffset);
        closeScopeSelector(linePosition + startLineOffset);
        myStates.pop();
      }
      else {
        break;
      }
    }

    while (true) {
      TextMateLexerState lastState = myStates.peek();
      SyntaxNodeDescriptor lastRule = lastState.syntaxRule;

      String currentScope = SyntaxMatchUtils.selectorsToScope(openedTags);
      TextMateLexerState currentState =
        SyntaxMatchUtils.matchFirst(lastRule, line, linePosition, TextMateWeigh.Priority.NORMAL, currentScope);
      SyntaxNodeDescriptor currentRule = currentState.syntaxRule;
      MatchData currentMatch = currentState.matchData;

      int endPosition;
      MatchData endMatch = SyntaxMatchUtils.matchStringRegex(Constants.END_KEY, line, lastState, linePosition);
      if (endMatch.matched() && (!currentMatch.matched() || currentMatch.offset().getStartOffset() >= endMatch.offset().getStartOffset())) {
        myStates.pop();
        int startPosition = endPosition = endMatch.offset().getStartOffset();
        closeScopeSelector(startPosition + startLineOffset); // closing content scope
        if (lastRule.getPlistAttribute(Constants.END_CAPTURES_KEY) == null
            && lastRule.getPlistAttribute(Constants.CAPTURES_KEY) == null
            && lastRule.getPlistAttribute(Constants.BEGIN_CAPTURES_KEY) == null
            ||
            parseCaptures(Constants.END_CAPTURES_KEY, lastRule, endMatch, startLineOffset)
            ||
            parseCaptures(Constants.CAPTURES_KEY, lastRule, endMatch, startLineOffset)) {
          // move line position only if anything was captured or if there is nothing to capture at all
          endPosition = endMatch.offset().getEndOffset();
        }
        closeScopeSelector(endPosition + startLineOffset); // closing basic scope
      }
      else if (currentMatch.matched()) {
        int startPosition = currentMatch.offset().getStartOffset();
        endPosition = currentMatch.offset().getEndOffset();
        if (currentRule.getRegexAttribute(Constants.BEGIN_KEY) != null) {
          openScopeSelector(currentRule.getStringAttribute(Constants.NAME_KEY), startPosition + startLineOffset);
          parseCaptures(Constants.BEGIN_CAPTURES_KEY, currentRule, currentMatch, startLineOffset);
          parseCaptures(Constants.CAPTURES_KEY, currentRule, currentMatch, startLineOffset);
          openScopeSelector(currentRule.getStringAttribute(Constants.CONTENT_NAME_KEY), endPosition + startLineOffset);
          myStates.push(currentState);
        }
        else if (currentRule.getRegexAttribute(Constants.MATCH_KEY) != null) {
          openScopeSelector(currentRule.getStringAttribute(Constants.NAME_KEY), startPosition + startLineOffset);
          parseCaptures(Constants.CAPTURES_KEY, currentRule, currentMatch, startLineOffset);
          closeScopeSelector(endPosition + startLineOffset);
        }
      }
      else {
        addToken(endLineOffset);
        break;
      }

      // global looping protection
      if (lastSuccessState != null) {
        if (new ArrayList<>(myStates).equals(lastSuccessState)) {
          lastSuccessStateOccursCount++;
          if (lastSuccessStateOccursCount > MAX_LOOPS_COUNT) {
            addToken(endLineOffset);
            break;
          }
        }
      }

      // local looping protection
      final int currentStateLocalOccurrencesCount = localStates.count(myStates);
      if (currentStateLocalOccurrencesCount <= MAX_LOOPS_COUNT) {
        localStates.setCount(myStates, currentStateLocalOccurrencesCount + 1);
      }
      else {
        addToken(endLineOffset);
        break;
      }

      if (linePosition != endPosition) {
        // clear local states history on position changing
        localStates.clear();
        linePosition = endPosition;
      }

      final Application application = ApplicationManager.getApplication();
      if (application != null && !application.isUnitTestMode()) {
        ProgressManager.checkCanceled();
      }
    }
  }

  private void openScopeSelector(@Nullable String name, int position) {
    addToken(position);
    openedTags.push(name);
  }

  private void closeScopeSelector(int position) {
    if (!openedTags.isEmpty()) {
      if (StringUtil.isNotEmpty(openedTags.peek())) {
        addToken(position);
      }
    }
    if (!openedTags.empty()) {
      openedTags.pop();
    }
  }

  private void addToken(int position) {
    if (position > myCurrentOffset) {
      /*
       * normal state is 0, openedTags stack should contains at least one state with language scopeName,
       * so we decrement count of openedTags in order to 0
       */
      final int newState = openedTags.size() - 1;
      currentLineTokens.offer(new Token(new TextMateElementType(SyntaxMatchUtils.selectorsToScope(openedTags)),
                                        myCurrentOffset,
                                        position,
                                        newState));
      myCurrentOffset = position;
      setLastSuccessState(new ArrayList<>(myStates));
    }
  }

  private boolean parseCaptures(String capturesKey, SyntaxNodeDescriptor rule, MatchData matchData, int startLineOffset) {
    Plist captures = rule.getPlistAttribute(capturesKey);
    if (captures != null) {
      List<CaptureMatchData> matches = SyntaxMatchUtils.matchCaptures(captures, matchData);
      Stack<CaptureMatchData> starts = new Stack<>(ContainerUtil.sorted(matches, CaptureMatchData.START_OFFSET_ORDERING));
      Stack<CaptureMatchData> ends = new Stack<>(ContainerUtil.sorted(matches, CaptureMatchData.END_OFFSET_ORDERING));
      while (!starts.isEmpty() || !ends.isEmpty()) {
        if (starts.isEmpty()) {
          CaptureMatchData end = ends.pop();
          closeScopeSelector(startLineOffset + end.offset.getEndOffset());
        }
        else if (ends.isEmpty()) {
          CaptureMatchData start = starts.pop();
          openScopeSelector(start.selectorName, startLineOffset + start.offset.getStartOffset());
        }
        else if (ends.peek().group < starts.peek().group) {
          CaptureMatchData end = ends.pop();
          closeScopeSelector(startLineOffset + end.offset.getEndOffset());
        }
        else {
          CaptureMatchData start = starts.pop();
          openScopeSelector(start.selectorName, startLineOffset + start.offset.getStartOffset());
        }
      }
      return !matches.isEmpty();
    }
    return false;
  }

  private void setLastSuccessState(@Nullable ArrayList<TextMateLexerState> state) {
    lastSuccessState = state;
    lastSuccessStateOccursCount = 0;
  }

  @NotNull
  @Override
  public DataStorage createDataStorage() {
    return new TextMateLexerDataStorage();
  }

  private static class Token {
    public final IElementType type;
    public final int startOffset;
    public final int endOffset;
    public final int state;

    private Token(IElementType type, int startOffset, int endOffset, int state) {
      this.type = type;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      this.state = state;
    }
  }
}
