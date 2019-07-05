package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.google.common.base.Joiner;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.language.syntax.InjectionNodeDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorCachingWeigher;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateWeigh;
import org.jetbrains.plugins.textmate.plist.PListValue;
import org.jetbrains.plugins.textmate.plist.Plist;
import org.jetbrains.plugins.textmate.regex.MatchData;
import org.jetbrains.plugins.textmate.regex.RegexFacade;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jetbrains.plugins.textmate.regex.RegexFacade.regex;

public final class SyntaxMatchUtils {
  private static final Pattern DIGIT_GROUP_REGEX = Pattern.compile("\\\\([0-9]+)");
  private static final LoadingCache<MatchKey, TextMateLexerState> CACHE = CacheBuilder.newBuilder().maximumSize(32768)
    .build(CacheLoader.from(
      key -> matchFirstUncached(Objects.requireNonNull(key).descriptor, key.stringBytes, key.byteOffset, key.priority, key.currentScope)));
  private final static Joiner MY_OPEN_TAGS_JOINER = Joiner.on(" ").skipNulls();
  private static final TextMateSelectorWeigher mySelectorWeigher = new TextMateSelectorCachingWeigher(new TextMateSelectorWeigherImpl());


  @NotNull
  public static TextMateLexerState matchFirst(@NotNull SyntaxNodeDescriptor syntaxNodeDescriptor,
                                              @NotNull byte[] stringBytes,
                                              int byteOffset,
                                              @NotNull TextMateWeigh.Priority priority,
                                              @NotNull String currentScope) {
    try {
      return CACHE.get(new MatchKey(syntaxNodeDescriptor, stringBytes, byteOffset, priority, currentScope));
    }
    catch (ExecutionException | UncheckedExecutionException e) {
      Throwable cause = e.getCause();
      while (cause != null) {
        if (cause instanceof ProcessCanceledException) {
          throw (ProcessCanceledException)cause;
        }
        cause = cause.getCause();
      }

      throw new RuntimeException(e);
    }
  }

  @NotNull
  private static TextMateLexerState matchFirstUncached(@NotNull SyntaxNodeDescriptor syntaxNodeDescriptor,
                                                       @NotNull byte[] stringBytes,
                                                       int byteOffset,
                                                       @NotNull TextMateWeigh.Priority priority,
                                                       @NotNull String currentScope) {
    TextMateLexerState resultState = TextMateLexerState.notMatched(syntaxNodeDescriptor);
    List<SyntaxNodeDescriptor> children = syntaxNodeDescriptor.getChildren();
    for (SyntaxNodeDescriptor child : children) {
      resultState = moreImportantState(resultState, matchFirstChild(child, stringBytes, byteOffset, priority, currentScope));
      if (resultState.matchData.matched() && resultState.matchData.byteOffset().getStartOffset() == byteOffset) {
        // optimization. There cannot be anything more `important` than current state matched from the very beginning
        break;
      }
    }
    return moreImportantState(resultState, matchInjections(syntaxNodeDescriptor, stringBytes, byteOffset, currentScope));
  }

  @NotNull
  private static TextMateLexerState matchInjections(@NotNull SyntaxNodeDescriptor syntaxNodeDescriptor,
                                                    @NotNull byte[] stringBytes,
                                                    int byteOffset,
                                                    @NotNull String currentScope) {
    TextMateLexerState resultState = TextMateLexerState.notMatched(syntaxNodeDescriptor);
    List<InjectionNodeDescriptor> injections = syntaxNodeDescriptor.getInjections();

    for (InjectionNodeDescriptor injection : injections) {
      TextMateWeigh selectorWeigh = mySelectorWeigher.weigh(injection.getSelector(), currentScope);
      if (selectorWeigh.weigh <= 0) {
        continue;
      }
      TextMateLexerState injectionState = matchFirst(injection.getSyntaxNodeDescriptor(), stringBytes, byteOffset, selectorWeigh.priority, currentScope);
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
    int newScore = newState.matchData.byteOffset().getStartOffset();
    int oldScore = oldState.matchData.byteOffset().getStartOffset();
    if (newScore < oldScore || newScore == oldScore && newState.priorityMatch.compareTo(oldState.priorityMatch) > 0) {
      if (!newState.matchData.byteOffset().isEmpty() || oldState.matchData.byteOffset().isEmpty() || hasBeginKey(newState)) {
        return newState;
      }
    }
    return oldState;
  }

  private static boolean hasBeginKey(@NotNull TextMateLexerState lexerState) {
    return lexerState.syntaxRule.getRegexAttribute(Constants.BEGIN_KEY) != null;
  }

  private static TextMateLexerState matchFirstChild(@NotNull SyntaxNodeDescriptor syntaxNodeDescriptor,
                                                    @NotNull byte[] stringBytes,
                                                    int byteOffset,
                                                    @NotNull TextMateWeigh.Priority priority,
                                                    @NotNull String currentScope) {
    RegexFacade matchRegex = syntaxNodeDescriptor.getRegexAttribute(Constants.MATCH_KEY);
    if (matchRegex != null) {
      return new TextMateLexerState(syntaxNodeDescriptor, matchRegex.match(stringBytes, byteOffset), priority, stringBytes);
    }
    RegexFacade beginRegex = syntaxNodeDescriptor.getRegexAttribute(Constants.BEGIN_KEY);
    if (beginRegex != null) {
      return new TextMateLexerState(syntaxNodeDescriptor, beginRegex.match(stringBytes, byteOffset), priority, stringBytes);
    }
    if (syntaxNodeDescriptor.getStringAttribute(Constants.END_KEY) != null) {
      return TextMateLexerState.notMatched(syntaxNodeDescriptor);
    }
    return matchFirst(syntaxNodeDescriptor, stringBytes, byteOffset, priority, currentScope);
  }

  public static List<CaptureMatchData> matchCaptures(@NotNull Plist captures, @NotNull MatchData matchData, @NotNull byte[] stringBytes) {
    List<CaptureMatchData> result = new ArrayList<>();
    for (Map.Entry<String, PListValue> capture : captures.entries()) {
      try {
        int index = Integer.parseInt(capture.getKey());
        Plist captureDict = capture.getValue().getPlist();
        String captureName = captureDict.getPlistValue(Constants.NAME_KEY, "").getString();
        TextRange offset = index < matchData.count() ? matchData.charOffset(stringBytes, index) : TextRange.EMPTY_RANGE;
        if (!captureName.isEmpty() && !offset.isEmpty()) {
          result.add(new CaptureMatchData(offset, index, captureName));
        }
      }
      catch (NumberFormatException ignore) {
      }
    }
    return result;
  }

  public static MatchData matchStringRegex(@NotNull String keyName,
                                           @NotNull byte[] stringBytes,
                                           int byteOffset,
                                           @NotNull TextMateLexerState lexerState) {
    String stringRegex = lexerState.syntaxRule.getStringAttribute(keyName);
    return stringRegex != null ? regex(replaceGroupsWithMatchData(stringRegex, lexerState.stringBytes, lexerState.matchData)).match(stringBytes, byteOffset)
                               : MatchData.NOT_MATCHED;
  }

  /**
   * Replaces parts like \1 or \20 in patternString parameter with group captures from matchData.
   * <p/>
   * E.g. given patternString "\1-\2" and matchData consists of two groups: "first" and "second",
   * then patternString "first-second" will be returned.
   *
   * @param patternString source pattern
   * @param stringBytes   matched string bytes
   * @param matchData     matched data with captured groups for replacement
   * @return patternString with replaced group-references
   */
  public static String replaceGroupsWithMatchData(@NotNull String patternString,
                                                  @Nullable byte[] stringBytes,
                                                  @NotNull MatchData matchData) {
    if (stringBytes == null || !matchData.matched()) {
      return patternString;
    }
    Matcher matcher = DIGIT_GROUP_REGEX.matcher(patternString);
    StringBuilder result = new StringBuilder();
    int lastPosition = 0;
    while (matcher.find()) {
      int groupIndex = StringUtil.parseInt(matcher.group(1), -1);
      if (groupIndex >= 0 && matchData.count() > groupIndex) {
        result.append(patternString, lastPosition, matcher.start());
        TextRange range = matchData.byteOffset(groupIndex);
        String text = new String(stringBytes, range.getStartOffset(), range.getLength(), StandardCharsets.UTF_8);
        StringUtil.escapeToRegexp(text, result);
        lastPosition = matcher.end();
      }
    }
    if (lastPosition < patternString.length()) {
      result.append(patternString.substring(lastPosition));
    }
    return result.toString();
  }

  @NotNull
  public static String selectorsToScope(@NotNull List<String> selectors) {
    return MY_OPEN_TAGS_JOINER.join(selectors);
  }

  private static class MatchKey {
    final SyntaxNodeDescriptor descriptor;
    final byte[] stringBytes;
    final int byteOffset;
    private final TextMateWeigh.Priority priority;
    final String currentScope;

    private MatchKey(SyntaxNodeDescriptor descriptor,
                     byte[] stringBytes,
                     int byteOffset,
                     TextMateWeigh.Priority priority,
                     String currentScope) {
      this.descriptor = descriptor;
      this.stringBytes = stringBytes;
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
             Arrays.equals(stringBytes, key.stringBytes) &&
             priority == key.priority &&
             currentScope.equals(key.currentScope);
    }

    @Override
    public int hashCode() {
      return Objects.hash(descriptor, stringBytes, byteOffset, priority, currentScope);
    }
  }
}
