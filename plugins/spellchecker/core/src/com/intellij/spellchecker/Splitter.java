package com.intellij.spellchecker;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.spellchecker.util.Strings;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: shkate
 * Date: 07.05.2009
 * Time: 22:27:40
 * To change this template use File | Settings | File Templates.
 */
public class Splitter {

  @NonNls
  private static final Pattern NON_SPACE = Pattern.compile("\\S+");
  @NonNls
  /*private static final Pattern WORD = Pattern.compile("\\b\\p{L}+'?\\p{L}*\\b");*/
  private static final Pattern WORD = Pattern.compile("\\b\\p{Alpha}*'?\\p{Alpha}");
  @NonNls
  private static final Pattern URL = Pattern.compile("(https?|ftp|mailto)\\:\\/\\/");
  @NonNls
  private static final Pattern COMPLEX = Pattern.compile("(\\.[^\\.]+)|([@]+)");

  @NonNls
  private static final Pattern SPECIAL = Pattern.compile("^&\\p{Alnum}{4};");


  private Splitter() {
  }


  @Nullable
  public static List<CheckArea> splitText(@Nullable String text) {
    if (text == null || StringUtil.isEmpty(text)) {
      return null;
    }
    // Create a pattern to match breaks
    Matcher matcher = NON_SPACE.matcher(text);
    List<CheckArea> results = new ArrayList<CheckArea>();
    while (matcher.find()) {
      List<CheckArea> areaList = splitNonSpace(text, matcherRange(TextRange.from(0, text.length()), matcher));
      if (areaList != null) {
        results.addAll(areaList);
      }
    }
    return (results.size() == 0) ? null : results;
  }

  private static List<CheckArea> splitNonSpace(String text, TextRange range) {
    String nonSpaceArea = text.substring(range.getStartOffset(), range.getEndOffset());
    if (URL.matcher(nonSpaceArea).find() || COMPLEX.matcher(nonSpaceArea).find()) {
      return null;
    }
    return splitWord(text, range);
  }

  private static List<CheckArea> splitWord(String text, TextRange range) {
    if (range.getLength() <= 1) {
      return null;
    }

    List<CheckArea> results = new ArrayList<CheckArea>();
    String word = text.substring(range.getStartOffset(), range.getEndOffset());
    String[] words = NameUtil.splitNameIntoWords(word);
    if (words == null) {
      return results;
    }
    if (words.length == 1) {
      Matcher matcher = WORD.matcher(words[0]);
      Matcher specialMatcher = SPECIAL.matcher(words[0]);
      if (specialMatcher.find()) {
        TextRange found = matcherRange(range, specialMatcher);
        results.add(new CheckArea(text, found, true));
        return results;
      }
      else if (matcher.find()) {
        TextRange found = matcherRange(range, matcher);
        results.add(new CheckArea(text, found));
        return results;
      }
    }


    boolean isCapitalized = Strings.isCapitalized(words[0]);
    boolean containsShortWord = containsShortWord(words);

    if (isCapitalized && containsShortWord) {
      results.add(new CheckArea(text, range, true));
      return results;
    }

    boolean isAllWordsAreUpperCased = isAllWordsAreUpperCased(words);
    int index = 0;
    for (String s : words) {
      int start = word.indexOf(s, index);
      int end = start + s.length();
      boolean isUpperCase = Strings.isUpperCase(s);
      if (!(isUpperCase && !isAllWordsAreUpperCased) && !isKeyword(s)) {
        Matcher matcher = WORD.matcher(s);
        if (matcher.find()) {
          TextRange found = matcherRange(subRange(range, start, end), matcher);
          results.add(new CheckArea(text, found));
        }
      }
      else {
        Matcher matcher = WORD.matcher(s);
        if (matcher.find()) {
          TextRange found = matcherRange(subRange(range, start, end), matcher);
          results.add(new CheckArea(text, found, true));
        }
      }
      index = end;
    }
    return results;
  }

  private static boolean isKeyword(String s) {
    return false;
  }

  private static boolean isAllWordsAreUpperCased(String[] words) {
    if (words == null) return false;
    for (String word : words) {
      if (!Strings.isUpperCase(word)) {
        return false;
      }
    }
    return true;
  }

  private static boolean containsShortWord(String[] words) {
    if (words == null) return false;
    for (String word : words) {
      if (word.length() < 2) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private static TextRange matcherRange(@NotNull TextRange range, @NotNull Matcher matcher) {
    return subRange(range, matcher.start(), matcher.end());
  }

  @NotNull
  private static TextRange subRange(@NotNull TextRange range, int start, int end) {
    return TextRange.from(range.getStartOffset() + start, end - start);
  }
}
