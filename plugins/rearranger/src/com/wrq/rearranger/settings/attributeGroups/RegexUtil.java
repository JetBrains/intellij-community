/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger.settings.attributeGroups;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.PatternSyntaxException;

/** Supplies several utilities for regular expression handling and global comment generation. */
public class RegexUtil {
  /**
   * Handles escaping of regular expression syntax in an ordinary string so that the resulting string may be used as
   * a pattern to match the original string.
   *
   * @param in original string
   * @return pattern match string, which matches original string
   */
  public static String escape(String in) {
    StringBuffer sb = new StringBuffer(in.length() * 2);
    for (int i = 0; i < in.length(); i++) {
      char c = in.charAt(i);
      switch (c) {
        case '\t':
          sb.append("\\t");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\\':
        case '[':
        case ']':
        case '(':
        case ')':
        case '.':
        case '^':
        case '$':
        case '?':
        case '*':
        case '+':
          sb.append('\\');
          sb.append(c);
          break;
        default:
          sb.append(c);
      }
    }
    // now inspect for occurrences of the same character 4 times or more in a row; replace with
    // <char>{N} where N is the number of occurrences.
    final int MIN_REPEAT = 4;
    for (int i = 0; i < sb.length() - MIN_REPEAT; i++) {
      /** Count the number of identical characters in sequence at this location. */
      boolean escaped = sb.charAt(i) == '\\';
      int escapeBias = (escaped ? 1 : 0);
      char match = sb.charAt(i + escapeBias);
      int sequenceCount = 1; // a character always begins a sequence of at least one character
      for (int j = i + 1 + escapeBias;
           j < sb.length() - escapeBias;
           j += 1 + escapeBias)
      {
        if (escaped) {
          if (sb.charAt(j) == '\\' &&
              sb.charAt(j + 1) == match)
          {
            sequenceCount++;
            continue;
          }
        }
        else if (sb.charAt(j) == match) {
          sequenceCount++;
          continue;
        }
        break;
      }
      if (sequenceCount >= MIN_REPEAT) {
        // we've seen a sequence of MIN_REPEAT identical characters.
        // Substitute <char>{sequenceCount}
        int startOffset = i;
        int endOffset = i + sequenceCount * (1 + escapeBias);
        sb.delete(startOffset, endOffset);
        final String s = (escaped ? "\\" : "") + match + "{" + sequenceCount + "}";
        sb.insert(startOffset, s);
        i += s.length() - 1; // subtract one; the for-loop will increment it
      }
    }
    return sb.toString();
  }

  /**
   * Performs a literal substitution in commentText, replacing all occurrences of "%FS%" with the
   * fill string.
   *
   * @param commentText
   * @param fillString
   * @return
   */
  public static String replaceAllFS(String commentText, String fillString) {
    StringBuffer sb = new StringBuffer(commentText.length());
    int i;
    int lastOffset = 0;
    while ((i = commentText.indexOf("%FS%", lastOffset)) >= 0) {
      // copy everything from last offset to index.
      sb.append(commentText.substring(lastOffset, i));
      // copy the replacement string.
      sb.append(fillString);
      // bump index past %FS%.
      i += 4;
      lastOffset = i;
    }
    // now copy everything from lastOffset to end of string.
    sb.append(commentText.substring(lastOffset));
    return sb.toString();
  }

  /**
   * Extracts common starting and ending characters from a list of expression patterns and creates a new
   * combined expression pattern.
   *
   * @param expressionList list of individual regular expression patterns
   * @return combined expression pattern
   */
  public static String combineExpressions(List<String> expressionList) {
    final String firstString = expressionList.get(0);
    if (expressionList.size() == 1) {
      return firstString;
    }
    int commonStart = 0, commonEnd = 0;
    commonStart = calculateCommonStart(expressionList);
    commonEnd = calculateCommonEnd(commonStart, expressionList);

    if (commonStart == 0 && commonEnd == 0) {
      return combineSubgroups(expressionList);
    }
    String result = "";
    /**
     * now check validity of resulting pattern.  If commonStart or commonEnd broke apart some regular expression
     * syntax, e.g. // \*{(10}|20}) the pattern is invalid.  Try all combinations of commonStart/commonEnd
     * until we find one that works.
     */
    boolean valid = false;
    for (int start = commonStart; start >= 0; start--) {
      for (int end = commonEnd; end >= 0; end--) {
        valid = true;
        int totalLength = 0;
        for (Object aExpressionList : expressionList) {
          String s = ((String)aExpressionList);
          totalLength += s.length() + 1;
        }
        StringBuffer sb = new StringBuffer(totalLength + 2);
        sb.append(firstString.substring(0, start));
        List<String> subgroups = new ArrayList<String>(expressionList.size());
        for (Object aExpressionList1 : expressionList) {
          String s = ((String)aExpressionList1);
          s = s.substring(start, s.length() - end);
          subgroups.add(s);
        }
        String combined = combineSubgroups(subgroups);
        sb.append(combined);
        sb.append(firstString.substring(firstString.length() - end, firstString.length()));
        result = sb.toString();
        try {
          if (valid && " ".matches(result)) ;
        }
        catch (PatternSyntaxException e) {
          valid = false;
        }
        if (valid) {
          return result;
        }
      }
    }
    return result;
  }

  private static String combineSubgroups(List<String> expressionList) {
    int totalSize = 0;
    List<List<String>> groups = new ArrayList<List<String>>();
    List<String> strings = new ArrayList<String>(expressionList);
    while (strings.size() > 0) {
      List<String> subgroup = new ArrayList<String>();
      String firstString = strings.remove(0);
      totalSize += firstString.length();
      subgroup.add(firstString);


      ListIterator<String> otherStrings = strings.listIterator();
      while (otherStrings.hasNext()) {
        String otherString = otherStrings.next();
        List<String> testGroup = new ArrayList<String>();
        testGroup.add(firstString);
        testGroup.add(otherString);
        int commonStart = calculateCommonStart(testGroup);
        int commonEnd = calculateCommonEnd(commonStart, testGroup);
        if (commonStart >= 2 || commonEnd >= 2) {
          subgroup.add(otherString);
          totalSize += otherString.length();
          otherStrings.remove();
        }
      }
      groups.add(subgroup);
    }
    // now groups contains a list of subgroups.  Each subgroup consists of one or more strings.
    // strings in a subgroup share at least two leading and/or trailing characters.
    // return a parenthesized list of the subgroups.
    StringBuffer sb = new StringBuffer(totalSize);
    ListIterator<List<String>> groupLI = groups.listIterator();
    if (groups.size() > 1) {
      sb.append('(');
    }
    boolean first = true;
    while (groupLI.hasNext()) {
      if (first) {
        first = false;
      }
      else {
        sb.append('|');
      }
      List<String> nextGroup = groupLI.next();
      final String s = combineExpressions(nextGroup);
      sb.append(s);
    }
    if (groups.size() > 1) {
      sb.append(')');
    }
    return sb.toString();
  }

  private static int calculateCommonEnd(int commonStart, List<String> expressionList) {
    final String firstString = expressionList.get(0);
    return calculateCommonEnd(commonStart, expressionList, firstString.length() - commonStart);
  }

  private static int calculateCommonEnd(int commonStart, List<String> expressionList, int maxCommonEnd) {
    final String firstString = (expressionList.get(0));
    int commonEnd = 0;
    boolean matching = true;
    while (matching && commonEnd < maxCommonEnd) {
      if (commonEnd == firstString.length() - commonStart) {
        break;
      }
      commonEnd++;
      String match = firstString.substring(firstString.length() - commonEnd,
                                           firstString.length());
      for (int i = 1; i < expressionList.size(); i++) {
        if (!((expressionList.get(i))).endsWith(match)) {
          matching = false;
          commonEnd--;
          break;
        }
      }
    }
    String match = firstString.substring(firstString.length() - commonEnd,
                                         firstString.length());
    if (commonEnd > 0 &&
        !checkSubstringPatternValidity(match))
    {
      return calculateCommonEnd(commonStart - 1, expressionList, commonEnd - 1);
    }
    return commonEnd;
  }

  private static int calculateCommonStart(List expressionList) {
    final String firstString = ((java.lang.String)expressionList.get(0));
    return calculateCommonStart(expressionList, firstString.length());
  }

  private static int calculateCommonStart(List expressionList, int maxCommonStart) {
    final String firstString = ((java.lang.String)expressionList.get(0));
    int commonStart = 0;
    boolean matching = true;
    while (matching && commonStart < maxCommonStart) {
      if (commonStart == firstString.length()) {
        break;
      }
      commonStart++;
      String match = firstString.substring(0, commonStart);
      for (int i = 1; i < expressionList.size(); i++) {
        if (!(((java.lang.String)expressionList.get(i))).startsWith(match)) {
          matching = false;
          commonStart--;
          break;
        }
      }
    }
    if (commonStart > 0) {
      if (!checkSubstringPatternValidity(firstString.substring(0, commonStart)) ||
          !checkRemainderPatternValidity(firstString.substring(commonStart)))
      {
        return calculateCommonStart(expressionList, commonStart - 1);
      }
    }
    return commonStart;
  }

  private static boolean checkRemainderPatternValidity(String s) {
    char c = s.charAt(0);
    return (c != '{' && c != '}');
  }

  private static boolean checkSubstringPatternValidity(String s) {
    char c = s.charAt(0);
    if (c == '+' || c == '*' || c == '?') return false;
    if (s.length() >= 2) {
      if (s.charAt(s.length() - 1) == '\\' && s.charAt(s.length() - 2) != '\\') {
        return false;
      }
    }
    return (count(s, '[') == count(s, ']') &&
            count(s, '{') == count(s, '}'));
  }

  private static int count(String s, char c) {
    char[] chars = s.toCharArray();
    int result = 0;
    for (int i = 0; i < chars.length; i++) {
      if (chars[i] == c) {
        if (i == 0 || chars[i - 1] != '\\') {
          // character is not escaped, count it
          result++;
        }
      }
    }
    return result;
  }
}
