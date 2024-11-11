package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.util.text.Strings;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.ExecutorsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.language.syntax.InjectionNodeDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorCachingWeigher;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh;
import org.jetbrains.plugins.textmate.regex.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class SyntaxMatchUtils {
  private static final Cache<MatchKey, TextMateLexerState> CACHE = Caffeine.newBuilder()
    .maximumSize(100_000)
    .expireAfterAccess(1, TimeUnit.MINUTES)
    .executor(ExecutorsKt.asExecutor(Dispatchers.getDefault()))
    .build();
  private static final TextMateSelectorWeigher mySelectorWeigher = new TextMateSelectorCachingWeigher(new TextMateSelectorWeigherImpl());

  private static Runnable ourCheckCancelledCallback = null;

  public static void setCheckCancelledCallback(@Nullable Runnable runnable) {
    ourCheckCancelledCallback = runnable;
  }

  public static Runnable getCheckCancelledCallback() {
    return ourCheckCancelledCallback;
  }

  @NotNull
  public static TextMateLexerState matchFirst(@NotNull RegexFactory regexFactory,
                                              @NotNull SyntaxNodeDescriptor syntaxNodeDescriptor,
                                              @NotNull TextMateString string,
                                              int byteOffset,
                                              int gosOffset,
                                              boolean matchBeginOfString,
                                              @NotNull TextMateWeigh.Priority priority,
                                              @NotNull TextMateScope currentScope) {
    return CACHE.get(new MatchKey(regexFactory, syntaxNodeDescriptor, string, byteOffset, gosOffset, matchBeginOfString, priority, currentScope),
                     SyntaxMatchUtils::matchFirstUncached);
  }

  private static TextMateLexerState matchFirstUncached(MatchKey key) {
    return matchFirstUncached(Objects.requireNonNull(key).regexFactory,
                              key.descriptor,
                              key.string,
                              key.byteOffset,
                              key.gosOffset,
                              key.matchBeginOfString,
                              key.priority, key.currentScope);
  }

  @NotNull
  private static TextMateLexerState matchFirstUncached(@NotNull RegexFactory regexFactory,
                                                       @NotNull SyntaxNodeDescriptor syntaxNodeDescriptor,
                                                       @NotNull TextMateString string,
                                                       int byteOffset,
                                                       int gosOffset,
                                                       boolean matchBeginOfString,
                                                       @NotNull TextMateWeigh.Priority priority,
                                                       @NotNull TextMateScope currentScope) {
    TextMateLexerState resultState = TextMateLexerState.notMatched(syntaxNodeDescriptor);
    List<SyntaxNodeDescriptor> children = syntaxNodeDescriptor.getChildren();
    for (SyntaxNodeDescriptor child : children) {
      resultState =
        moreImportantState(resultState,
                           matchFirstChild(regexFactory, child, string, byteOffset, gosOffset, matchBeginOfString, priority, currentScope));
      if (resultState.matchData.matched() && resultState.matchData.byteOffset().start == byteOffset) {
        // optimization. There cannot be anything more `important` than current state matched from the very beginning
        break;
      }
    }
    return moreImportantState(resultState,
                              matchInjections(regexFactory, syntaxNodeDescriptor, string, byteOffset, gosOffset, matchBeginOfString, currentScope));
  }

  @NotNull
  private static TextMateLexerState matchInjections(@NotNull RegexFactory regexFactory,
                                                    @NotNull SyntaxNodeDescriptor syntaxNodeDescriptor,
                                                    @NotNull TextMateString string,
                                                    int byteOffset,
                                                    int gosOffset,
                                                    boolean matchBeginOfString,
                                                    @NotNull TextMateScope currentScope) {
    TextMateLexerState resultState = TextMateLexerState.notMatched(syntaxNodeDescriptor);
    List<InjectionNodeDescriptor> injections = syntaxNodeDescriptor.getInjections();

    for (InjectionNodeDescriptor injection : injections) {
      TextMateWeigh selectorWeigh = mySelectorWeigher.weigh(injection.getSelector(), currentScope);
      if (selectorWeigh.weigh <= 0) {
        continue;
      }
      TextMateLexerState injectionState =
        matchFirstUncached(regexFactory,
                           injection.getSyntaxNodeDescriptor(), string, byteOffset, gosOffset, matchBeginOfString, selectorWeigh.priority,
                           currentScope);
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

  private static TextMateLexerState matchFirstChild(@NotNull RegexFactory regexFactory,
                                                    @NotNull SyntaxNodeDescriptor syntaxNodeDescriptor,
                                                    @NotNull TextMateString string,
                                                    int byteOffset,
                                                    int gosOffset,
                                                    boolean matchBeginOfString,
                                                    @NotNull TextMateWeigh.Priority priority,
                                                    @NotNull TextMateScope currentScope) {
    CharSequence match = syntaxNodeDescriptor.getStringAttribute(Constants.StringKey.MATCH);
    if (match != null) {
      RegexFacade regex = regexFactory.regex(match.toString());
      MatchData matchData = regex.match(string, byteOffset, gosOffset, matchBeginOfString, ourCheckCancelledCallback);
      return new TextMateLexerState(syntaxNodeDescriptor, matchData, priority, byteOffset, string);
    }
    CharSequence begin = syntaxNodeDescriptor.getStringAttribute(Constants.StringKey.BEGIN);
    if (begin != null) {
      RegexFacade regex = regexFactory.regex(begin.toString());
      MatchData matchData = regex.match(string, byteOffset, gosOffset, matchBeginOfString, ourCheckCancelledCallback);
      return new TextMateLexerState(syntaxNodeDescriptor, matchData, priority, byteOffset, string);
    }
    if (syntaxNodeDescriptor.getStringAttribute(Constants.StringKey.END) != null) {
      return TextMateLexerState.notMatched(syntaxNodeDescriptor);
    }
    return matchFirstUncached(regexFactory, syntaxNodeDescriptor, string, byteOffset, gosOffset, matchBeginOfString, priority, currentScope);
  }

  public static MatchData matchStringRegex(@NotNull RegexFactory regexFactory,
                                           @NotNull Constants.StringKey keyName,
                                           @NotNull TextMateString string,
                                           int byteOffset,
                                           int anchorOffset,
                                           boolean matchBeginOfString,
                                           @NotNull TextMateLexerState lexerState) {
    CharSequence regex = lexerState.syntaxRule.getStringAttribute(keyName);
    if (regex == null) return MatchData.NOT_MATCHED;
    String regexString = lexerState.syntaxRule.hasBackReference(keyName)
                         ? replaceGroupsWithMatchDataInRegex(regex, lexerState.string, lexerState.matchData)
                         : regex.toString();
    return regexFactory.regex(regexString).match(string, byteOffset, anchorOffset, matchBeginOfString, ourCheckCancelledCallback);
  }

  @Nullable
  public static CharSequence getStringAttribute(Constants.@NotNull StringKey keyName,
                                                @NotNull SyntaxNodeDescriptor syntaxRule,
                                                @NotNull TextMateString string,
                                                @NotNull MatchData matchData) {
    CharSequence stringAttribute = syntaxRule.getStringAttribute(keyName);
    if (stringAttribute == null) {
      return null;
    }
    return syntaxRule.hasBackReference(keyName)
           ? replaceGroupsWithMatchDataInCaptures(stringAttribute, string, matchData)
           : stringAttribute;
  }

  /**
   * Replaces parts like \1 or \20 in string parameter with group captures from matchData.
   * <p/>
   * E.g. given string "\1-\2" and matchData consists of two groups: "first" and "second"
   * then string "first-second" will be returned.
   *
   * @param string         string pattern
   * @param matchingString matched matchingString
   * @param matchData      matched data with captured groups for replacement
   * @return string with replaced group-references
   */
  public static String replaceGroupsWithMatchDataInRegex(@NotNull CharSequence string,
                                                         @Nullable TextMateString matchingString,
                                                         @NotNull MatchData matchData) {
    if (matchingString == null || !matchData.matched()) {
      return string.toString();
    }
    StringBuilder result = new StringBuilder();
    int charIndex = 0;
    int length = string.length();
    while (charIndex < length) {
      char c = string.charAt(charIndex);
      if (c == '\\') {
        boolean hasGroupIndex = false;
        int groupIndex = 0;
        int digitIndex = charIndex + 1;
        while (digitIndex < length) {
          int digit = Character.digit(string.charAt(digitIndex), 10);
          if (digit == -1) {
            break;
          }
          hasGroupIndex = true;
          groupIndex = groupIndex * 10 + digit;
          digitIndex++;
        }
        if (hasGroupIndex && matchData.count() > groupIndex) {
          TextMateRange range = matchData.byteOffset(groupIndex);
          Strings.escapeToRegexp(new String(matchingString.bytes, range.start, range.getLength(), StandardCharsets.UTF_8), result);
          charIndex = digitIndex;
          continue;
        }
      }
      result.append(c);
      charIndex++;
    }
    return result.toString();
  }

  private static final Pattern CAPTURE_GROUP_REGEX = Pattern.compile("\\$([0-9]+)|\\$\\{([0-9]+):/(downcase|upcase)}");

  /**
   * Replaces parts like $1 or $20 in string parameter with group captures from matchData,
   * specifically for {@link org.jetbrains.plugins.textmate.language.syntax.TextMateCapture}.
   * <p>
   * Unlike {@link #replaceGroupsWithMatchDataInRegex(CharSequence, TextMateString, MatchData)},
   * this method also supports `upcase` and `downcase` command for the replacement.
   *
   * @param string         string pattern
   * @param matchingString matched matchingString
   * @param matchData      matched data with captured groups for replacement
   * @return string with replaced group-references
   */
  public static CharSequence replaceGroupsWithMatchDataInCaptures(@NotNull CharSequence string,
                                                                  @NotNull TextMateString matchingString,
                                                                  @NotNull MatchData matchData) {
    if (!matchData.matched()) {
      return string;
    }
    Matcher matcher = CAPTURE_GROUP_REGEX.matcher(string);
    StringBuilder result = new StringBuilder();
    int lastPosition = 0;
    while (matcher.find()) {
      int groupIndex = StringUtilRt.parseInt(matcher.group(1) != null ? matcher.group(1) : matcher.group(2), -1);
      if (groupIndex >= 0 && matchData.count() > groupIndex) {
        result.append(string, lastPosition, matcher.start());
        TextMateRange range = matchData.byteOffset(groupIndex);
        String capturedText = new String(matchingString.bytes, range.start, range.getLength(), StandardCharsets.UTF_8);
        int numberOfDotsAtTheBeginning = Strings.countChars(capturedText, '.', 0, true);
        String replacement = capturedText.substring(numberOfDotsAtTheBeginning);
        String command = matcher.group(3);
        if ("downcase".equals(command)) {
          result.append(replacement.toLowerCase(Locale.ROOT));
        }
        else if ("upcase".equals(command)) {
          result.append(replacement.toUpperCase(Locale.ROOT));
        }
        else {
          result.append(replacement);
        }
        lastPosition = matcher.end();
      }
    }
    if (lastPosition < string.length()) {
      result.append(string.subSequence(lastPosition, string.length()));
    }
    return result.toString();
  }

  private static final class MatchKey {
    final RegexFactory regexFactory;
    final SyntaxNodeDescriptor descriptor;
    final TextMateString string;
    final int byteOffset;
    final int gosOffset;
    final boolean matchBeginOfString;
    private final TextMateWeigh.Priority priority;
    final TextMateScope currentScope;

    private MatchKey(RegexFactory regexFactory,
                     SyntaxNodeDescriptor descriptor,
                     TextMateString string,
                     int byteOffset,
                     int gosOffset,
                     boolean matchBeginOfString,
                     TextMateWeigh.Priority priority,
                     TextMateScope currentScope) {
      this.regexFactory = regexFactory;
      this.descriptor = descriptor;
      this.string = string;
      this.byteOffset = byteOffset;
      this.gosOffset = gosOffset;
      this.matchBeginOfString = matchBeginOfString;
      this.priority = priority;
      this.currentScope = currentScope;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MatchKey key = (MatchKey)o;
      return byteOffset == key.byteOffset &&
             gosOffset == key.gosOffset &&
             matchBeginOfString == key.matchBeginOfString &&
             descriptor.equals(key.descriptor) &&
             Objects.equals(string, key.string) &&
             priority == key.priority &&
             currentScope.equals(key.currentScope);
    }

    @Override
    public int hashCode() {
      return Objects.hash(descriptor, string, byteOffset, gosOffset, matchBeginOfString, priority, currentScope);
    }
  }
}
