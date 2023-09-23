package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.intellij.openapi.util.text.Strings;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.TextMateCapture;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh;
import org.jetbrains.plugins.textmate.regex.MatchData;
import org.jetbrains.plugins.textmate.regex.RegexUtil;
import org.jetbrains.plugins.textmate.regex.StringWithId;
import org.jetbrains.plugins.textmate.regex.TextMateRange;

import java.util.*;

public final class TextMateLexer {
  /**
   * Count of `lastSuccessState` that can be occurred again without offset changing.
   * If `lastSuccessStateOccursCount` reaches {@code MAX_LOOPS_COUNT}
   * then lexing of current line stops and lexer moved to the EOL.
   */
  private static final int MAX_LOOPS_COUNT = 10;

  private int myCurrentOffset = 0;

  private CharSequence myText = "";

  @NotNull
  private TextMateScope myCurrentScope = TextMateScope.EMPTY;
  private FList<TextMateLexerState> myStates = FList.emptyList();

  private final CharSequence myLanguageScopeName;
  private final int myLineLimit;
  private final boolean myStripWhitespaces;
  private final Runnable myCheckCancelledCallback;
  private final TextMateLexerState myLanguageInitialState;

  public TextMateLexer(@NotNull TextMateLanguageDescriptor languageDescriptor, int lineLimit) {
    this(languageDescriptor, lineLimit, false);
  }

  public TextMateLexer(@NotNull TextMateLanguageDescriptor languageDescriptor, int lineLimit, boolean stripWhitespaces) {
    myLanguageScopeName = languageDescriptor.getScopeName();
    myLanguageInitialState = TextMateLexerState.notMatched(languageDescriptor.getRootSyntaxNode());
    myLineLimit = lineLimit;
    myStripWhitespaces = stripWhitespaces;
    myCheckCancelledCallback = SyntaxMatchUtils.getCheckCancelledCallback();
  }

  public void init(CharSequence text, int startOffset) {
    myText = text;
    myCurrentOffset = startOffset;

    myStates = FList.<TextMateLexerState>emptyList().prepend(myLanguageInitialState);
    myCurrentScope = new TextMateScope(myLanguageScopeName, null);
  }

  public int getCurrentOffset() {
    return myCurrentOffset;
  }

  public void advanceLine(@NotNull Queue<Token> output) {
    int startLineOffset = myCurrentOffset;
    int endLineOffset = startLineOffset;
    while (endLineOffset < myText.length()) {
      if (myText.charAt(endLineOffset) == '\n') {
        endLineOffset++;
        break;
      }
      endLineOffset++;
    }

    CharSequence lineCharSequence = myText.subSequence(startLineOffset, endLineOffset);
    if (myLineLimit >= 0 && lineCharSequence.length() > myLineLimit) {
      myStates = parseLine(lineCharSequence.subSequence(0, myLineLimit), output, myStates, startLineOffset, 0, 0);
      addToken(output, endLineOffset);
    }
    else {
      myStates = parseLine(lineCharSequence, output, myStates, startLineOffset, 0, 0);
    }
  }

  private FList<TextMateLexerState> parseLine(@NotNull CharSequence line,
                                              @NotNull Queue<Token> output,
                                              @NotNull FList<TextMateLexerState> states,
                                              int lineStartOffset,
                                              int linePosition,
                                              int lineByteOffset) {
    FList<TextMateLexerState> lastSuccessState = states;
    int lastSuccessStateOccursCount = 0;
    int lastMovedOffset = lineStartOffset;

    boolean matchBeginOfString = lineStartOffset == 0;
    int anchorByteOffset = -1; // makes sense only for a line, cannot be used across lines

    StringWithId string = new StringWithId(line);
    while (true) {
      final TextMateLexerState lexerState = states.getHead();
      if (lexerState.syntaxRule.getStringAttribute(Constants.StringKey.WHILE) != null) {
        MatchData matchWhile = SyntaxMatchUtils.matchStringRegex(Constants.StringKey.WHILE, string, lineByteOffset, anchorByteOffset,
                                                                 matchBeginOfString, lexerState);
        if (!matchWhile.matched()) {
          closeScopeSelector(output, linePosition + lineStartOffset);
          closeScopeSelector(output, linePosition + lineStartOffset);
          states = states.getTail();
          // this is happening at line start, none of the previous states couldn't be run on this line, so no need to update anchorByteOffset
          continue;
        }
        else {
          anchorByteOffset = matchWhile.byteOffset().end;
        }
      }
      break;
    }

    Set<TextMateLexerState> localStates = new HashSet<>();
    while (true) {
      TextMateLexerState lastState = states.getHead();
      SyntaxNodeDescriptor lastRule = lastState.syntaxRule;

      TextMateLexerState currentState = SyntaxMatchUtils.matchFirst(lastRule, string, lineByteOffset, anchorByteOffset, matchBeginOfString,
                                                                    TextMateWeigh.Priority.NORMAL, myCurrentScope);
      SyntaxNodeDescriptor currentRule = currentState.syntaxRule;
      MatchData currentMatch = currentState.matchData;

      int endPosition;
      MatchData endMatch =
        SyntaxMatchUtils.matchStringRegex(Constants.StringKey.END, string, lineByteOffset, anchorByteOffset, matchBeginOfString, lastState);
      if (endMatch.matched() && (!currentMatch.matched() ||
                                 currentMatch.byteOffset().start >= endMatch.byteOffset().start ||
                                 lastState.equals(currentState))) {
        TextMateLexerState poppedState = states.getHead();
        if (poppedState.matchData.matched() && !poppedState.matchedEOL) {
          // if begin hasn't matched EOL, it was performed on the same line; we need to use its anchor
          anchorByteOffset = poppedState.matchData.byteOffset().end;
        }
        states = states.getTail();

        TextMateRange endRange = endMatch.charRange(line, string.bytes);
        int startPosition = endPosition = endRange.start;
        closeScopeSelector(output, startPosition + lineStartOffset); // closing content scope
        if (lastRule.getCaptureRules(Constants.CaptureKey.END_CAPTURES) == null
            && lastRule.getCaptureRules(Constants.CaptureKey.CAPTURES) == null
            && lastRule.getCaptureRules(Constants.CaptureKey.BEGIN_CAPTURES) == null
            ||
            parseCaptures(output, Constants.CaptureKey.END_CAPTURES, lastRule, endMatch, string, line, lineStartOffset, states)
            ||
            parseCaptures(output, Constants.CaptureKey.CAPTURES, lastRule, endMatch, string, line, lineStartOffset, states)) {
          // move line position only if anything was captured or if there is nothing to capture at all
          endPosition = endRange.end;
        }
        closeScopeSelector(output, endPosition + lineStartOffset); // closing basic scope

        if (linePosition == endPosition && containsLexerState(localStates, poppedState) && poppedState.enterByteOffset == lineByteOffset) {
          addToken(output, line.length() + lineStartOffset);
          break;
        }
        localStates.remove(poppedState);
      }
      else if (currentMatch.matched()) {
        anchorByteOffset = currentMatch.byteOffset().end;

        TextMateRange currentRange = currentMatch.charRange(line, string.bytes);
        int startPosition = currentRange.start;
        endPosition = currentRange.end;

        if (currentRule.getStringAttribute(Constants.StringKey.BEGIN) != null) {
          states = states.prepend(currentState);

          CharSequence name = SyntaxMatchUtils.getStringAttribute(Constants.StringKey.NAME, currentRule, string, currentMatch);
          openScopeSelector(output, name, startPosition + lineStartOffset);

          parseCaptures(output, Constants.CaptureKey.BEGIN_CAPTURES, currentRule, currentMatch, string, line, lineStartOffset, states);
          parseCaptures(output, Constants.CaptureKey.CAPTURES, currentRule, currentMatch, string, line, lineStartOffset, states);

          CharSequence contentName = SyntaxMatchUtils.getStringAttribute(Constants.StringKey.CONTENT_NAME, currentRule, string, currentMatch);
          openScopeSelector(output, contentName, endPosition + lineStartOffset);
        }
        else if (currentRule.getStringAttribute(Constants.StringKey.MATCH) != null) {
          CharSequence name = SyntaxMatchUtils.getStringAttribute(Constants.StringKey.NAME, currentRule, string, currentMatch);
          openScopeSelector(output, name, startPosition + lineStartOffset);
          parseCaptures(output, Constants.CaptureKey.CAPTURES, currentRule, currentMatch, string, line, lineStartOffset, states);
          closeScopeSelector(output, endPosition + lineStartOffset);
        }

        if (linePosition == endPosition && containsLexerState(localStates, currentState)) {
          addToken(output, line.length() + lineStartOffset);
          break;
        }
        localStates.add(currentState);
      }
      else {
        addToken(output, line.length() + lineStartOffset);
        break;
      }

      // global looping protection
      if (lastMovedOffset < myCurrentOffset) {
        lastSuccessState = states;
        lastSuccessStateOccursCount = 0;
        lastMovedOffset = myCurrentOffset;
      }
      else if (lastSuccessState.equals(states)) {
        if (lastSuccessStateOccursCount > MAX_LOOPS_COUNT) {
          addToken(output, line.length() + lineStartOffset);
          break;
        }
        lastSuccessStateOccursCount++;
      }

      if (linePosition != endPosition) {
        linePosition = endPosition;
        lineByteOffset = RegexUtil.byteOffsetByCharOffset(line, linePosition);
      }

      if (myCheckCancelledCallback != null) {
        myCheckCancelledCallback.run();
      }
    }
    return states;
  }

  private static boolean containsLexerState(Set<TextMateLexerState> states, TextMateLexerState state) {
    for (TextMateLexerState s : states) {
      if (s.enterByteOffset == state.enterByteOffset && s.syntaxRule.equals(state.syntaxRule)) {
        return true;
      }
    }
    return false;
  }

  private boolean parseCaptures(@NotNull Queue<Token> output,
                                Constants.CaptureKey captureKey,
                                SyntaxNodeDescriptor rule,
                                MatchData matchData,
                                StringWithId string,
                                CharSequence line,
                                int startLineOffset,
                                FList<TextMateLexerState> states) {
    TextMateCapture @Nullable [] captures = rule.getCaptureRules(captureKey);
    if (captures == null) {
      return false;
    }

    Deque<TextMateRange> activeCaptureRanges = new LinkedList<>();
    for (int group = 0; group < matchData.count(); group++) {
      TextMateCapture capture = group < captures.length ? captures[group] : null;
      if (capture == null) {
        continue;
      }

      TextMateRange byteRange = matchData.byteOffset(group);
      if (byteRange.isEmpty()) {
        continue;
      }

      TextMateRange captureRange = matchData.charRange(line, string.bytes, group);

      while (!activeCaptureRanges.isEmpty() && activeCaptureRanges.peek().end <= captureRange.start) {
        closeScopeSelector(output, startLineOffset + activeCaptureRanges.pop().end);
      }

      if (capture instanceof TextMateCapture.Name) {
        CharSequence captureName = ((TextMateCapture.Name)capture).getName();
        CharSequence scopeName = rule.hasBackReference(captureKey, group)
                                    ? SyntaxMatchUtils.replaceGroupsWithMatchDataInCaptures(captureName, string, matchData)
                                    : captureName;
        int selectorStartOffset = 0;
        int indexOfSpace = Strings.indexOf(scopeName, ' ', selectorStartOffset);
        if (indexOfSpace == -1) {
          openScopeSelector(output, scopeName, startLineOffset + captureRange.start);
          activeCaptureRanges.push(captureRange);
        }
        else {
          while (indexOfSpace >= 0) {
            openScopeSelector(output, scopeName.subSequence(selectorStartOffset, indexOfSpace), startLineOffset + captureRange.start);
            selectorStartOffset = indexOfSpace + 1;
            indexOfSpace = Strings.indexOf(scopeName, ' ', selectorStartOffset);
          }
          openScopeSelector(output, scopeName.subSequence(selectorStartOffset, scopeName.length()), startLineOffset + captureRange.start);
          activeCaptureRanges.push(captureRange);
        }
      }
      else if (capture instanceof TextMateCapture.Rule) {
        CharSequence capturedString = line.subSequence(0, captureRange.end);
        StringWithId capturedStringWithId = new StringWithId(capturedString);
        TextMateLexerState captureState = new TextMateLexerState(((TextMateCapture.Rule)capture).getNode(),
                                                                 matchData,
                                                                 TextMateWeigh.Priority.NORMAL,
                                                                 byteRange.start,
                                                                 capturedStringWithId);
        parseLine(capturedString, output, states.prepend(captureState), startLineOffset, captureRange.start, byteRange.start);
      }
      else {
        throw new IllegalStateException("unknown capture type: " + capture);
      }
    }
    while (!activeCaptureRanges.isEmpty()) {
      closeScopeSelector(output, startLineOffset + activeCaptureRanges.pop().end);
    }
    return true;
  }

  private void openScopeSelector(@NotNull Queue<Token> output, @Nullable CharSequence name, int position) {
    addToken(output, position);
    myCurrentScope = myCurrentScope.add(name);
  }

  private void closeScopeSelector(@NotNull Queue<Token> output, int position) {
    CharSequence lastOpenedName = myCurrentScope.getScopeName();
    if (lastOpenedName != null && !lastOpenedName.isEmpty()) {
      addToken(output, position);
    }
    myCurrentScope = myCurrentScope.getParentOrSelf();
  }


  private void addToken(@NotNull Queue<Token> output, int position) {
    position = Math.min(position, myText.length());
    if (position > myCurrentOffset) {
      boolean newState = myCurrentScope.getParent() == null;
      int wsStart = myCurrentOffset;
      while (myStripWhitespaces && position > myCurrentOffset && Character.isWhitespace(myText.charAt(myCurrentOffset))) {
        myCurrentOffset++;
      }

      if (wsStart < myCurrentOffset) {
        output.offer(new Token(TextMateScope.WHITESPACE, wsStart, myCurrentOffset, newState));
        newState = false;
      }

      int wsEnd = position;
      while (myStripWhitespaces && wsEnd > myCurrentOffset && Character.isWhitespace(myText.charAt(wsEnd - 1))) {
        wsEnd--;
      }

      if (myCurrentOffset < wsEnd) {
        output.offer(new Token(myCurrentScope, myCurrentOffset, wsEnd, newState));
      }

      if (wsEnd < position) {
        output.offer(new Token(TextMateScope.WHITESPACE, wsEnd, position, newState));
      }

      myCurrentOffset = position;
    }
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
