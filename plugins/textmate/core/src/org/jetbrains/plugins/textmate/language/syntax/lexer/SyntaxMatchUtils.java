package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.language.syntax.InjectionNodeDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorCachingWeigher;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh;
import org.jetbrains.plugins.textmate.regex.MatchData;
import org.jetbrains.plugins.textmate.regex.RegexFacade;
import org.jetbrains.plugins.textmate.regex.StringWithId;
import org.jetbrains.plugins.textmate.regex.TextMateRange;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

import static org.jetbrains.plugins.textmate.regex.RegexFacade.regex;

public final class SyntaxMatchUtils {
  private static final ConcurrentMap<MatchKey, TextMateLexerState> CACHE = ContainerUtil.createConcurrentSoftKeySoftValueMap();
  private static final Map<List<CharSequence>, String> MY_SCOPES_INTERNER = ContainerUtil.createConcurrentWeakKeyWeakValueMap();
  private static final TextMateSelectorWeigher mySelectorWeigher = new TextMateSelectorCachingWeigher(new TextMateSelectorWeigherImpl());

  private static Runnable ourCheckCancelledCallback = null;

  public static void setCheckCancelledCallback(@Nullable Runnable runnable) {
    ourCheckCancelledCallback = runnable;
  }

  public static Runnable getCheckCancelledCallback() {
    return ourCheckCancelledCallback;
  }

  @NotNull
  public static TextMateLexerState matchFirst(@NotNull SyntaxNodeDescriptor syntaxNodeDescriptor,
                                              @NotNull StringWithId string,
                                              int byteOffset,
                                              @NotNull TextMateWeigh.Priority priority,
                                              @NotNull String currentScope) {
    return CACHE.computeIfAbsent(new MatchKey(syntaxNodeDescriptor, string, byteOffset, priority, currentScope),
                                 SyntaxMatchUtils::matchFirstUncached);
  }

  private static TextMateLexerState matchFirstUncached(MatchKey key) {
    return matchFirstUncached(Objects.requireNonNull(key).descriptor, key.string, key.byteOffset, key.priority, key.currentScope);
  }

  @NotNull
  private static TextMateLexerState matchFirstUncached(@NotNull SyntaxNodeDescriptor syntaxNodeDescriptor,
                                                       @NotNull StringWithId string,
                                                       int byteOffset,
                                                       @NotNull TextMateWeigh.Priority priority,
                                                       @NotNull String currentScope) {
    TextMateLexerState resultState = TextMateLexerState.notMatched(syntaxNodeDescriptor);
    List<SyntaxNodeDescriptor> children = syntaxNodeDescriptor.getChildren();
    for (SyntaxNodeDescriptor child : children) {
      resultState = moreImportantState(resultState, matchFirstChild(child, string, byteOffset, priority, currentScope));
      if (resultState.matchData.matched() && resultState.matchData.byteOffset().start == byteOffset) {
        // optimization. There cannot be anything more `important` than current state matched from the very beginning
        break;
      }
    }
    return moreImportantState(resultState, matchInjections(syntaxNodeDescriptor, string, byteOffset, currentScope));
  }

  @NotNull
  private static TextMateLexerState matchInjections(@NotNull SyntaxNodeDescriptor syntaxNodeDescriptor,
                                                    @NotNull StringWithId string,
                                                    int byteOffset,
                                                    @NotNull String currentScope) {
    TextMateLexerState resultState = TextMateLexerState.notMatched(syntaxNodeDescriptor);
    List<InjectionNodeDescriptor> injections = syntaxNodeDescriptor.getInjections();

    for (InjectionNodeDescriptor injection : injections) {
      TextMateWeigh selectorWeigh = mySelectorWeigher.weigh(injection.getSelector(), currentScope);
      if (selectorWeigh.weigh <= 0) {
        continue;
      }
      TextMateLexerState injectionState =
        matchFirstUncached(injection.getSyntaxNodeDescriptor(), string, byteOffset, selectorWeigh.priority, currentScope);
      resultState = moreImportantState(resultState, injectionState);
    }
    return resultState;
  }

  @NotNull
  private static TextMateLexerState moreImportantState(@NotNull TextMateLexerState oldState, @NotNull TextMateLexerState newState) {
    if (!newState.matchData.matched()) {
      return oldState;
    }
    else if (!oldState.matchData.matched()) {
      return newState;
    }
    int newScore = newState.matchData.byteOffset().start;
    int oldScore = oldState.matchData.byteOffset().start;
    if (newScore < oldScore || newScore == oldScore && newState.priorityMatch.compareTo(oldState.priorityMatch) > 0) {
      if (!newState.matchData.byteOffset().isEmpty() || oldState.matchData.byteOffset().isEmpty() || hasBeginKey(newState)) {
        return newState;
      }
    }
    return oldState;
  }

  private static boolean hasBeginKey(@NotNull TextMateLexerState lexerState) {
    return lexerState.syntaxRule.getStringAttribute(Constants.StringKey.BEGIN) != null;
  }

  private static TextMateLexerState matchFirstChild(@NotNull SyntaxNodeDescriptor syntaxNodeDescriptor,
                                                    @NotNull StringWithId string,
                                                    int byteOffset,
                                                    @NotNull TextMateWeigh.Priority priority,
                                                    @NotNull String currentScope) {
    CharSequence match = syntaxNodeDescriptor.getStringAttribute(Constants.StringKey.MATCH);
    if (match != null) {
      RegexFacade regex = regex(match.toString());
      return new TextMateLexerState(syntaxNodeDescriptor, regex.match(string, byteOffset, ourCheckCancelledCallback), priority, string);
    }
    CharSequence begin = syntaxNodeDescriptor.getStringAttribute(Constants.StringKey.BEGIN);
    if (begin != null) {
      RegexFacade regex = regex(begin.toString());
      return new TextMateLexerState(syntaxNodeDescriptor, regex.match(string, byteOffset, ourCheckCancelledCallback), priority, string);
    }
    if (syntaxNodeDescriptor.getStringAttribute(Constants.StringKey.END) != null) {
      return TextMateLexerState.notMatched(syntaxNodeDescriptor);
    }
    return matchFirstUncached(syntaxNodeDescriptor, string, byteOffset, priority, currentScope);
  }

  public static List<CaptureMatchData> matchCaptures(@NotNull TIntObjectHashMap<CharSequence> captures,
                                                     @NotNull MatchData matchData,
                                                     @NotNull StringWithId string,
                                                     @NotNull String s) {
    List<CaptureMatchData> result = new ArrayList<>();
    for (int index : captures.keys()) {
      TextMateRange range = index < matchData.count() ? matchData.charRange(s, string.bytes, index) : TextMateRange.EMPTY_RANGE;
      result.add(new CaptureMatchData(range, index, captures.get(index)));
    }
    return result;
  }

  public static MatchData matchStringRegex(@NotNull Constants.StringKey keyName,
                                           @NotNull StringWithId string,
                                           int byteOffset,
                                           @NotNull TextMateLexerState lexerState) {
    CharSequence stringRegex = lexerState.syntaxRule.getStringAttribute(keyName);
    if (stringRegex != null) {
      return regex(replaceGroupsWithMatchData(stringRegex, lexerState.string, lexerState.matchData))
        .match(string, byteOffset, ourCheckCancelledCallback);
    }
    return MatchData.NOT_MATCHED;
  }

  /**
   * Replaces parts like \1 or \20 in patternString parameter with group captures from matchData.
   * <p/>
   * E.g. given patternString "\1-\2" and matchData consists of two groups: "first" and "second",
   * then patternString "first-second" will be returned.
   *
   * @param patternString source pattern
   * @param string        matched string
   * @param matchData     matched data with captured groups for replacement
   * @return patternString with replaced group-references
   */
  public static String replaceGroupsWithMatchData(@NotNull CharSequence patternString,
                                                  @Nullable StringWithId string,
                                                  @NotNull MatchData matchData) {
    if (string == null || !matchData.matched()) {
      return patternString.toString();
    }
    StringBuilder result = new StringBuilder();
    int charIndex = 0;
    int length = patternString.length();
    while (charIndex < length) {
      char c = patternString.charAt(charIndex);
      if (c == '\\') {
        boolean hasGroupIndex = false;
        int groupIndex = 0;
        int digitIndex = charIndex + 1;
        while (digitIndex < length) {
          int digit = Character.digit(patternString.charAt(digitIndex), 10);
          if (digit == -1) {
            break;
          }
          hasGroupIndex = true;
          groupIndex = groupIndex * 10 + digit;
          digitIndex++;
        }
        if (hasGroupIndex && matchData.count() > groupIndex) {
          TextMateRange range = matchData.byteOffset(groupIndex);
          StringUtil.escapeToRegexp(new String(string.bytes, range.start, range.getLength(), StandardCharsets.UTF_8), result);
          charIndex = digitIndex;
          continue;
        }
      }
      result.append(c);
      charIndex++;
    }
    return result.toString();
  }

  @NotNull
  public static String selectorsToScope(@NotNull Collection<CharSequence> selectors) {
    return MY_SCOPES_INTERNER.computeIfAbsent(new ArrayList<>(selectors), SyntaxMatchUtils::joinSelectors);
  }

  private static String joinSelectors(@NotNull List<CharSequence> selectors) {
    StringBuilder result = new StringBuilder();
    for (CharSequence selector : selectors) {
      if (selector != null) {
        result.append(selector).append(" ");
      }
    }
    if (result.length() > 0) {
      result.setLength(result.length() - 1);
    }
    return result.toString();
  }

  private static final class MatchKey {
    final SyntaxNodeDescriptor descriptor;
    final StringWithId string;
    final int byteOffset;
    private final TextMateWeigh.Priority priority;
    final String currentScope;

    private MatchKey(SyntaxNodeDescriptor descriptor,
                     StringWithId string,
                     int byteOffset,
                     TextMateWeigh.Priority priority,
                     String currentScope) {
      this.descriptor = descriptor;
      this.string = string;
      this.byteOffset = byteOffset;
      this.priority = priority;
      this.currentScope = currentScope;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MatchKey key = (MatchKey)o;
      return byteOffset == key.byteOffset &&
             descriptor.equals(key.descriptor) &&
             Objects.equals(string, key.string) &&
             priority == key.priority &&
             currentScope.equals(key.currentScope);
    }

    @Override
    public int hashCode() {
      return Objects.hash(descriptor, string, byteOffset, priority, currentScope);
    }
  }
}
