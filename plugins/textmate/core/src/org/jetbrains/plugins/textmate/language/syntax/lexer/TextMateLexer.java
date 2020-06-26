package org.jetbrains.plugins.textmate.language.syntax.lexer;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh;
import org.jetbrains.plugins.textmate.regex.MatchData;
import org.jetbrains.plugins.textmate.regex.RegexUtil;
import org.jetbrains.plugins.textmate.regex.StringWithId;
import org.jetbrains.plugins.textmate.regex.TextMateRange;

import java.util.*;
import java.util.stream.Collectors;

public class TextMateLexer {
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

  private int myCurrentOffset = 0;

  private CharSequence myText ="";
  private final Deque<TextMateLexerState> myStates = new LinkedList<>();
  private final Deque<CharSequence> openedTags = new LinkedList<>();

  private final CharSequence myLanguageScopeName;
  private final int myLineLimit;
  private final Runnable myCheckCancelledCallback;
  private final TextMateLexerState myLanguageInitialState;

  public TextMateLexer(@NotNull TextMateLanguageDescriptor languageDescriptor, int lineLimit) {
    myLanguageScopeName = languageDescriptor.getScopeName();
    myLanguageInitialState = TextMateLexerState.notMatched(languageDescriptor.getRootSyntaxNode());
    myLineLimit = lineLimit;
    myCheckCancelledCallback = SyntaxMatchUtils.getCheckCancelledCallback();
  }

  public void init(CharSequence text, int startOffset) {
    myText = text;
    myCurrentOffset = startOffset;

    myStates.clear();
    myStates.push(myLanguageInitialState);
    openedTags.clear();
    openedTags.offer(myLanguageScopeName);
    setLastSuccessState(null);
  }

  public int getCurrentOffset() {
    return myCurrentOffset;
  }

  public void advanceLine(@NotNull Queue<Token> output) {
    int startLineOffset = myCurrentOffset;
    int endLineOffset = myCurrentOffset;
    while (endLineOffset < myText.length()) {
      if (myText.charAt(endLineOffset) == '\n') {
        endLineOffset++;
        break;
      }
      endLineOffset++;
    }

    CharSequence lineCharSequence = myText.subSequence(startLineOffset, endLineOffset);
    if (myLineLimit >= 0 && lineCharSequence.length() > myLineLimit) {
      parseLine(lineCharSequence.subSequence(0, myLineLimit), output);
      addToken(output, endLineOffset);
    }
    else {
      parseLine(lineCharSequence, output);
    }
  }

  private void parseLine(@NotNull CharSequence lineCharSequence, @NotNull Queue<Token> output) {
    restoreValidStateBeforeNewLine();

    int startLineOffset = myCurrentOffset;
    int linePosition = 0;
    int lineByteOffset = 0;
    String line = lineCharSequence.length() > 0 && lineCharSequence.charAt(lineCharSequence.length() - 1) == '\n'
                  ? lineCharSequence.toString()
                  : lineCharSequence.toString() + "\n";

    StringWithId string = new StringWithId(line);
    while (true) {
      final TextMateLexerState lexerState = myStates.element();
      if (lexerState.syntaxRule.getStringAttribute(Constants.StringKey.WHILE) != null
          && !SyntaxMatchUtils.matchStringRegex(Constants.StringKey.WHILE, string, lineByteOffset, lexerState).matched()) {
        closeScopeSelector(output, linePosition + startLineOffset);
        closeScopeSelector(output, linePosition + startLineOffset);
        myStates.pop();
      }
      else {
        break;
      }
    }

    final TObjectIntHashMap<List<TextMateLexerState>> localStates = new TObjectIntHashMap<>();
    while (true) {
      TextMateLexerState lastState = myStates.element();
      SyntaxNodeDescriptor lastRule = lastState.syntaxRule;

      String currentScope = SyntaxMatchUtils.selectorsToScope(openedTags);
      TextMateLexerState currentState =
        SyntaxMatchUtils.matchFirst(lastRule, string, lineByteOffset, TextMateWeigh.Priority.NORMAL, currentScope);
      SyntaxNodeDescriptor currentRule = currentState.syntaxRule;
      MatchData currentMatch = currentState.matchData;

      int endPosition;
      MatchData endMatch = SyntaxMatchUtils.matchStringRegex(Constants.StringKey.END, string, lineByteOffset, lastState);
      if (endMatch.matched() && (!currentMatch.matched() ||
                                 currentMatch.byteOffset().start >= endMatch.byteOffset().start ||
                                 lastState.equals(currentState))) {
        myStates.pop();
        TextMateRange endRange = endMatch.charRange(line, string.bytes);
        int startPosition = endPosition = endRange.start;
        closeScopeSelector(output, startPosition + startLineOffset); // closing content scope
        if (lastRule.getCaptures(Constants.CaptureKey.END_CAPTURES) == null
            && lastRule.getCaptures(Constants.CaptureKey.CAPTURES) == null
            && lastRule.getCaptures(Constants.CaptureKey.BEGIN_CAPTURES) == null
            ||
            parseCaptures(output, Constants.CaptureKey.END_CAPTURES, lastRule, endMatch, string, line, startLineOffset)
            ||
            parseCaptures(output, Constants.CaptureKey.CAPTURES, lastRule, endMatch, string, line, startLineOffset)) {
          // move line position only if anything was captured or if there is nothing to capture at all
          endPosition = endRange.end;
        }
        closeScopeSelector(output, endPosition  + startLineOffset); // closing basic scope
      }
      else if (currentMatch.matched()) {
        TextMateRange currentRange = currentMatch.charRange(line, string.bytes);
        int startPosition = currentRange.start;
        endPosition = currentRange.end;
        if (currentRule.getStringAttribute(Constants.StringKey.BEGIN) != null) {
          openScopeSelector(output, currentRule.getStringAttribute(Constants.StringKey.NAME), startPosition + startLineOffset);
          parseCaptures(output, Constants.CaptureKey.BEGIN_CAPTURES, currentRule, currentMatch, string, line, startLineOffset);
          parseCaptures(output, Constants.CaptureKey.CAPTURES, currentRule, currentMatch, string, line, startLineOffset);
          openScopeSelector(output, currentRule.getStringAttribute(Constants.StringKey.CONTENT_NAME), endPosition + startLineOffset);
          myStates.push(currentState);
        }
        else if (currentRule.getStringAttribute(Constants.StringKey.MATCH) != null) {
          openScopeSelector(output, currentRule.getStringAttribute(Constants.StringKey.NAME), startPosition + startLineOffset);
          parseCaptures(output, Constants.CaptureKey.CAPTURES, currentRule, currentMatch, string, line, startLineOffset);
          closeScopeSelector(output, endPosition + startLineOffset);
        }
      }
      else {
        addToken(output, line.length() + startLineOffset);
        break;
      }

      // global looping protection
      ArrayList<TextMateLexerState> currentStateSnapshot = new ArrayList<>(myStates);
      if (lastSuccessState != null) {
        if (currentStateSnapshot.equals(lastSuccessState)) {
          lastSuccessStateOccursCount++;
          if (lastSuccessStateOccursCount > MAX_LOOPS_COUNT) {
            addToken(output, line.length() + startLineOffset);
            break;
          }
        }
      }

      // local looping protection
      final int currentStateLocalOccurrencesCount = localStates.get(currentStateSnapshot);
      if (currentStateLocalOccurrencesCount <= MAX_LOOPS_COUNT) {
        localStates.put(currentStateSnapshot, currentStateLocalOccurrencesCount + 1);
      }
      else {
        addToken(output, line.length() + startLineOffset);
        break;
      }

      if (linePosition != endPosition) {
        // clear local states history on position changing
        localStates.clear();
        linePosition = endPosition;
        lineByteOffset = RegexUtil.byteOffsetByCharOffset(line, linePosition);
      }

      if (myCheckCancelledCallback != null) {
        myCheckCancelledCallback.run();
      }
    }
  }

  private void setLastSuccessState(@Nullable ArrayList<TextMateLexerState> state) {
    lastSuccessState = state;
    lastSuccessStateOccursCount = 0;
  }

  private boolean parseCaptures(@NotNull Queue<Token> output,
                                Constants.CaptureKey capturesKey,
                                SyntaxNodeDescriptor rule,
                                MatchData matchData,
                                StringWithId string,
                                String line,
                                int startLineOffset) {
    TIntObjectHashMap<CharSequence> captures = rule.getCaptures(capturesKey);
    if (captures != null) {
      List<CaptureMatchData> matches = SyntaxMatchUtils.matchCaptures(captures, matchData, string, line);
      //noinspection SSBasedInspection
      List<CaptureMatchData> nonEmptyMatches = matches.stream().filter(m -> m.selectorName.length() > 0 && !m.range.isEmpty())
        .collect(Collectors.toList());
      LinkedList<CaptureMatchData> starts = new LinkedList<>(nonEmptyMatches);
      Collections.sort(starts, CaptureMatchData.START_OFFSET_ORDERING);

      LinkedList<CaptureMatchData> ends = new LinkedList<>(nonEmptyMatches);
      Collections.sort(ends, CaptureMatchData.END_OFFSET_ORDERING);

      while (!starts.isEmpty() || !ends.isEmpty()) {
        if (starts.isEmpty()) {
          CaptureMatchData end = ends.removeLast();
          closeScopeSelector(output, end.range.end + startLineOffset);
        }
        else if (ends.isEmpty()) {
          CaptureMatchData start = starts.removeLast();
          openScopeSelector(output, start.selectorName, start.range.start + startLineOffset);
        }
        else if (ends.getLast().group < starts.getLast().group) {
          CaptureMatchData end = ends.removeLast();
          closeScopeSelector(output, end.range.end + startLineOffset);
        }
        else {
          CaptureMatchData start = starts.removeLast();
          openScopeSelector(output, start.selectorName, start.range.start + startLineOffset);
        }
      }
      return !matches.isEmpty();
    }
    return false;
  }


  private void openScopeSelector(@NotNull Queue<Token> output, @Nullable CharSequence name, int position) {
    addToken(output, position);
    openedTags.offer(name);
  }

  private void closeScopeSelector(@NotNull Queue<Token> output, int position) {
    if (!openedTags.isEmpty()) {
      CharSequence lastOpened = openedTags.peekLast();
      if (lastOpened != null && lastOpened.length() > 0) {
        addToken(output, position);
      }
    }
    openedTags.pollLast();
  }


  private void addToken(@NotNull Queue<Token> output, int position) {
    if (position > myCurrentOffset) {
      /*
       * normal state is 0, openedTags stack should contains at least one state with language scopeName,
       * so we decrement count of openedTags in order to 0
       */
      final boolean newState = openedTags.size() <= 1;
      output.offer(new Token(SyntaxMatchUtils.selectorsToScope(openedTags), myCurrentOffset, position, newState));
      myCurrentOffset = position;
      setLastSuccessState(new ArrayList<>(myStates));
    }
  }

  /**
   * not really needed when lexer works fine,
   * but when lexer fails and remove language scope tag from stack,
   * we want to restore valid state in order to parse following line properly.
   */
  private void restoreValidStateBeforeNewLine() {
    if (openedTags.isEmpty()) {
      openedTags.offer(myLanguageScopeName);
    }
  }

  public static final class Token {
    public final String selector;
    public final int startOffset;
    public final int endOffset;
    public final boolean restartable;

    private Token(String selector, int startOffset, int endOffset, boolean restartable) {
      this.selector = selector;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      this.restartable = restartable;
    }
  }
}
