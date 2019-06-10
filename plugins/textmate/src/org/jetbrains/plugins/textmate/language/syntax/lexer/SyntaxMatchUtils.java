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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jetbrains.plugins.textmate.regex.RegexFacade.regex;

public final class SyntaxMatchUtils {
  private static final Pattern DIGIT_GROUP_REGEX = Pattern.compile("\\\\([0-9]+)");
  private static final LoadingCache<MatchKey, TextMateLexerState> CACHE = CacheBuilder.newBuilder().maximumSize(32768)
    .build(CacheLoader.from(
      key -> matchFirstUncached(Objects.requireNonNull(key).descriptor, key.line, key.position, key.priority, key.currentScope)));
  private final static Joiner MY_OPEN_TAGS_JOINER = Joiner.on(" ").skipNulls();
  private static final TextMateSelectorWeigher mySelectorWeigher = new TextMateSelectorCachingWeigher(new TextMateSelectorWeigherImpl());


  @NotNull
  public static TextMateLexerState matchFirst(SyntaxNodeDescriptor syntaxNodeDescriptor,
                                              String line,
                                              int position,
                                              TextMateWeigh.Priority priority,
                                              String currentScope) {
    try {
      return CACHE.get(new MatchKey(syntaxNodeDescriptor, line, position, priority, currentScope));
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
                                                       @NotNull String line,
                                                       int position,
                                                       @NotNull TextMateWeigh.Priority priority,
                                                       @NotNull String currentScope) {
    TextMateLexerState resultState = TextMateLexerState.notMatched(syntaxNodeDescriptor);
    List<SyntaxNodeDescriptor> children = syntaxNodeDescriptor.getChildren();
    for (SyntaxNodeDescriptor child : children) {
      resultState = moreImportantState(resultState, matchFirstChild(child, line, position, priority, currentScope));
    }
    return moreImportantState(resultState, matchInjections(syntaxNodeDescriptor, line, position, currentScope));
  }

  @NotNull
  private static TextMateLexerState matchInjections(@NotNull SyntaxNodeDescriptor syntaxNodeDescriptor,
                                                    @NotNull String line,
                                                    int position,
                                                    @NotNull String currentScope) {
    TextMateLexerState resultState = TextMateLexerState.notMatched(syntaxNodeDescriptor);
    List<InjectionNodeDescriptor> injections = syntaxNodeDescriptor.getInjections();

    for (InjectionNodeDescriptor injection : injections) {
      TextMateWeigh selectorWeigh = mySelectorWeigher.weigh(injection.getSelector(), currentScope);
      if (selectorWeigh.weigh <= 0) {
        continue;
      }
      TextMateLexerState injectionState = matchFirst(injection.getSyntaxNodeDescriptor(), line, position, selectorWeigh.priority, currentScope);
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
    int newScore = newState.matchData.offset().getStartOffset();
    int oldScore = oldState.matchData.offset().getStartOffset();
    if (newScore < oldScore || newScore == oldScore && newState.priorityMatch.compareTo(oldState.priorityMatch) > 0) {
      if (!newState.matchData.offset().isEmpty() || oldState.matchData.offset().isEmpty() || hasBeginKey(newState)) {
        return newState;
      }
    }
    return oldState;
  }

  private static boolean hasBeginKey(@NotNull TextMateLexerState lexerState) {
    return lexerState.syntaxRule.getRegexAttribute(Constants.BEGIN_KEY) != null;
  }

  private static TextMateLexerState matchFirstChild(@NotNull SyntaxNodeDescriptor syntaxNodeDescriptor,
                                                    @NotNull String line,
                                                    int position,
                                                    @NotNull TextMateWeigh.Priority priority,
                                                    @NotNull String currentScope) {
    RegexFacade matchRegex = syntaxNodeDescriptor.getRegexAttribute(Constants.MATCH_KEY);
    if (matchRegex != null) {
      return new TextMateLexerState(syntaxNodeDescriptor, matchRegex.match(line, position), priority);
    }
    RegexFacade beginRegex = syntaxNodeDescriptor.getRegexAttribute(Constants.BEGIN_KEY);
    if (beginRegex != null) {
      return new TextMateLexerState(syntaxNodeDescriptor, beginRegex.match(line, position), priority);
    }
    if (syntaxNodeDescriptor.getStringAttribute(Constants.END_KEY) != null) {
      return TextMateLexerState.notMatched(syntaxNodeDescriptor);
    }
    return matchFirst(syntaxNodeDescriptor, line, position, priority, currentScope);
  }

  public static List<CaptureMatchData> matchCaptures(@NotNull Plist captures, @NotNull MatchData matchData) {
    List<CaptureMatchData> result = new ArrayList<>();
    for (Map.Entry<String, PListValue> capture : captures.entries()) {
      try {
        int index = Integer.parseInt(capture.getKey());
        Plist captureDict = capture.getValue().getPlist();
        String captureName = captureDict.getPlistValue(Constants.NAME_KEY, "").getString();
        final TextRange offset = index < matchData.count() ? matchData.offset(index) : TextRange.EMPTY_RANGE;
        if (!captureName.isEmpty() && offset.getLength() > 0) {
          result.add(new CaptureMatchData(offset, index, captureName));
        }
      }
      catch (NumberFormatException ignore) {
      }
    }
    return result;
  }

  public static MatchData matchStringRegex(String keyName, String line, TextMateLexerState lexerState, int linePosition) {
    String stringRegex = lexerState.syntaxRule.getStringAttribute(keyName);
    return stringRegex != null
           ? regex(replaceGroupsWithMatchData(stringRegex, lexerState.matchData)).match(line, linePosition)
           : MatchData.NOT_MATCHED;
  }

  /**
   * Replaces parts like \1 or \20 in patternString parameter with group captures from matchData.
   * <p/>
   * E.g. given patternString "\1-\2" and matchData consists of two groups: "first" and "second",
   * then patternString "first-second" will be returned.
   *
   * @param patternString source pattern
   * @param matchData     matched data with captured groups for replacement
   * @return patternString with replaced group-references
   */
  public static String replaceGroupsWithMatchData(String patternString, MatchData matchData) {
    Matcher matcher = DIGIT_GROUP_REGEX.matcher(patternString);
    StringBuilder result = new StringBuilder();
    int lastPosition = 0;
    while (matcher.find()) {
      int groupIndex = StringUtil.parseInt(matcher.group(1), -1);
      if (groupIndex >= 0 && matchData.count() > groupIndex) {
        result.append(patternString, lastPosition, matcher.start());
        StringUtil.escapeToRegexp(matchData.capture(groupIndex), result);
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
    final String line;
    final int position;
    private final TextMateWeigh.Priority priority;
    final String currentScope;

    private MatchKey(SyntaxNodeDescriptor descriptor,
                     String line,
                     int position,
                     TextMateWeigh.Priority priority,
                     String currentScope) {
      this.descriptor = descriptor;
      this.line = line;
      this.position = position;
      this.priority = priority;
      this.currentScope = currentScope;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MatchKey key = (MatchKey)o;
      return position == key.position &&
             descriptor.equals(key.descriptor) &&
             line.equals(key.line) &&
             priority == key.priority &&
             currentScope.equals(key.currentScope);
    }

    @Override
    public int hashCode() {
      return Objects.hash(descriptor, line, position, priority, currentScope);
    }
  }
}
