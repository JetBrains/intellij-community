package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.openapi.util.text.Strings;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.jetbrains.plugins.textmate.regex.RegexFacade.regex;

public final class SyntaxMatchUtils {
  private static final Cache<MatchKey, TextMateLexerState> CACHE = Caffeine.newBuilder().maximumSize(100_000).expireAfterAccess(1, TimeUnit.MINUTES).build();
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
                                              int gosOffset,
                                              boolean matchBeginOfString,
                                              @NotNull TextMateWeigh.Priority priority,
                                              @NotNull TextMateScope currentScope) {
    return CACHE.get(new MatchKey(syntaxNodeDescriptor, string, byteOffset, gosOffset, matchBeginOfString, priority, currentScope),
                     SyntaxMatchUtils::matchFirstUncached);
  }

  private static TextMateLexerState matchFirstUncached(MatchKey key) {
    return matchFirstUncached(Objects.requireNonNull(key).descriptor, key.string, key.byteOffset, key.gosOffset, key.matchBeginOfString,
                              key.priority, key.currentScope);
  }

  @NotNull
  private static TextMateLexerState matchFirstUncached(@NotNull SyntaxNodeDescriptor syntaxNodeDescriptor,
                                                       @NotNull StringWithId string,
                                                       int byteOffset,
                                                       int gosOffset,
                                                       boolean matchBeginOfString,
                                                       @NotNull TextMateWeigh.Priority priority,
                                                       @NotNull TextMateScope currentScope) {
    TextMateLexerState resultState = TextMateLexerState.notMatched(syntaxNodeDescriptor);
    List<SyntaxNodeDescriptor> children = syntaxNodeDescriptor.getChildren();
    for (SyntaxNodeDescriptor child : children) {
      resultState = moreImportantState(resultState, matchFirstChild(child, string, byteOffset, gosOffset, matchBeginOfString, priority, currentScope));
      if (resultState.matchData.matched() && resultState.matchData.byteOffset().start == byteOffset) {
        // optimization. There cannot be anything more `important` than current state matched from the very beginning
        break;
      }
    }
    return moreImportantState(resultState, matchInjections(syntaxNodeDescriptor, string, byteOffset, gosOffset, matchBeginOfString, currentScope));
  }

  @NotNull
  private static TextMateLexerState matchInjections(@NotNull SyntaxNodeDescriptor syntaxNodeDescriptor,
                                                    @NotNull StringWithId string,
                                                    int byteOffset,
                                                    int gosOffset,
                                                    boolean matchBeginOfString,
                                                    @NotNull TextMateScope currentScope) {
    TextMateLexerState resultState = TextMateLexerState.notMatched(syntaxNodeDescriptor);
    SyntaxNodeDescriptor parent = syntaxNodeDescriptor.getParentNode();
    while (parent != null && parent.getParentNode() != null) {
      parent = parent.getParentNode();
    }
    List<InjectionNodeDescriptor> injections = new ArrayList<>();
    if (parent != null && parent != syntaxNodeDescriptor) {
      injections.addAll(parent.getInjections());
    }
    injections.addAll(syntaxNodeDescriptor.getInjections());

    for (InjectionNodeDescriptor injection : injections) {
      TextMateWeigh selectorWeigh = mySelectorWeigher.weigh(injection.getSelector(), currentScope);
      if (selectorWeigh.weigh <= 0) {
        continue;
      }
      TextMateLexerState injectionState =
        matchFirstUncached(injection.getSyntaxNodeDescriptor(), string, byteOffset, gosOffset, matchBeginOfString, selectorWeigh.priority, currentScope);
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
                                                    int gosOffset,
                                                    boolean matchBeginOfString,
                                                    @NotNull TextMateWeigh.Priority priority,
                                                    @NotNull TextMateScope currentScope) {
    CharSequence match = syntaxNodeDescriptor.getStringAttribute(Constants.StringKey.MATCH);
    if (match != null) {
      RegexFacade regex = regex(match.toString());
      MatchData matchData = regex.match(string, byteOffset, gosOffset, matchBeginOfString, ourCheckCancelledCallback);
      return new TextMateLexerState(syntaxNodeDescriptor, matchData, priority, byteOffset, string);
    }
    CharSequence begin = syntaxNodeDescriptor.getStringAttribute(Constants.StringKey.BEGIN);
    if (begin != null) {
      RegexFacade regex = regex(begin.toString());
      MatchData matchData = regex.match(string, byteOffset, gosOffset, matchBeginOfString, ourCheckCancelledCallback);
      return new TextMateLexerState(syntaxNodeDescriptor, matchData, priority, byteOffset, string);
    }
    if (syntaxNodeDescriptor.getStringAttribute(Constants.StringKey.END) != null) {
      return TextMateLexerState.notMatched(syntaxNodeDescriptor);
    }
    return matchFirstUncached(syntaxNodeDescriptor, string, byteOffset, gosOffset, matchBeginOfString, priority, currentScope);
  }

  public static List<CaptureMatchData> matchCaptures(@NotNull Int2ObjectMap<CharSequence> captures,
                                                     @NotNull MatchData matchData,
                                                     @NotNull StringWithId string,
                                                     @NotNull String s) {
    List<CaptureMatchData> result = new ArrayList<>();
    for (IntIterator iterator = captures.keySet().iterator(); iterator.hasNext(); ) {
      int index = iterator.nextInt();
      TextMateRange range = index < matchData.count() ? matchData.charRange(s, string.bytes, index) : TextMateRange.EMPTY_RANGE;
      result.add(new CaptureMatchData(range, index, captures.get(index)));
    }
    return result;
  }

  public static MatchData matchStringRegex(@NotNull Constants.StringKey keyName,
                                           @NotNull StringWithId string,
                                           int byteOffset,
                                           int anchorOffset,
                                           boolean matchBeginOfString,
                                           @NotNull TextMateLexerState lexerState) {
    String regex = getStringAttribute(keyName, lexerState.syntaxRule, lexerState.string, lexerState.matchData);
    return regex != null
           ? regex(regex).match(string, byteOffset, anchorOffset, matchBeginOfString, ourCheckCancelledCallback)
           : MatchData.NOT_MATCHED;
  }

  @Nullable
  public static String getStringAttribute(Constants.@NotNull StringKey keyName,
                                          @NotNull SyntaxNodeDescriptor syntaxRule,
                                          @Nullable StringWithId string,
                                          @NotNull MatchData matchData) {
    CharSequence stringAttribute = syntaxRule.getStringAttribute(keyName);
    if (stringAttribute == null) {
      return null;
    }
    return syntaxRule.hasBackReference(keyName)
           ? replaceGroupsWithMatchData(stringAttribute, string, matchData, keyName.backReferencePrefix)
           : stringAttribute.toString();
  }

  /**
   * Replaces parts like \1 or \20 in string parameter with group captures from matchData.
   * <p/>
   * E.g. given string "\1-\2" and matchData consists of two groups: "first" and "second",
   * then string "first-second" will be returned.
   *
   * @param string string pattern
   * @param matchingString        matched matchingString
   * @param matchData     matched data with captured groups for replacement
   * @return string with replaced group-references
   */
  public static String replaceGroupsWithMatchData(@Nullable CharSequence string,
                                                  @Nullable StringWithId matchingString,
                                                  @NotNull MatchData matchData,
                                                  char groupPrefix) {
    if (string == null) {
      return null;
    }

    if (matchingString == null || !matchData.matched()) {
      return string.toString();
    }
    StringBuilder result = new StringBuilder();
    int charIndex = 0;
    int length = string.length();
    while (charIndex < length) {
      char c = string.charAt(charIndex);
      if (c == groupPrefix) {
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

  private static final class MatchKey {
    final SyntaxNodeDescriptor descriptor;
    final StringWithId string;
    final int byteOffset;
    final int gosOffset;
    final boolean matchBeginOfString;
    private final TextMateWeigh.Priority priority;
    final TextMateScope currentScope;

    private MatchKey(SyntaxNodeDescriptor descriptor,
                     StringWithId string,
                     int byteOffset,
                     int gosOffset,
                     boolean matchBeginOfString,
                     TextMateWeigh.Priority priority,
                     TextMateScope currentScope) {
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
