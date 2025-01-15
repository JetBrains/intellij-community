package org.jetbrains.plugins.textmate.language.syntax.lexer;

import kotlin.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.language.syntax.SyntaxNodeDescriptor;
import org.jetbrains.plugins.textmate.regex.MatchData;
import org.jetbrains.plugins.textmate.regex.TextMateRange;
import org.jetbrains.plugins.textmate.regex.TextMateString;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class SyntaxMatchUtils {
  public static @Nullable CharSequence getStringAttribute(Constants.@NotNull StringKey keyName,
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
   * E.g., given string "\1-\2" and matchData consists of two groups: "first" and "second"
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
    if (matchingString == null || !matchData.matched) {
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
          String replacement = new String(matchingString.bytes, range.start, range.getLength(), StandardCharsets.UTF_8);
          result.append(BACK_REFERENCE_REPLACEMENT_REGEX.matcher(replacement).replaceAll("\\\\$0"));
          charIndex = digitIndex;
          continue;
        }
      }
      result.append(c);
      charIndex++;
    }
    return result.toString();
  }

  private static final Pattern BACK_REFERENCE_REPLACEMENT_REGEX = Pattern.compile("[\\-\\\\{}*+?|^$.,\\[\\]()#\\s]");
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
    if (!matchData.matched) {
      return string;
    }
    Matcher matcher = CAPTURE_GROUP_REGEX.matcher(string);
    StringBuilder result = new StringBuilder();
    int lastPosition = 0;
    while (matcher.find()) {
      int groupIndex = parseGroupIndex(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
      if (groupIndex >= 0 && matchData.count() > groupIndex) {
        result.append(string, lastPosition, matcher.start());
        TextMateRange range = matchData.byteOffset(groupIndex);
        String capturedText = new String(matchingString.bytes, range.start, range.getLength(), StandardCharsets.UTF_8);
        String replacement = StringsKt.trimStart(capturedText, '.');
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

  private static int parseGroupIndex(@Nullable String string) {
    if (string != null) {
      try { return Integer.parseInt(string); }
      catch (NumberFormatException ignored) { }
    }
    return -1;
  }


}
