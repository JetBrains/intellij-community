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
package com.wrq.rearranger.util;

import com.intellij.openapi.util.text.StringUtil;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.settings.attributeGroups.Rule;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommentUtil {
  private final RearrangerSettings mySettings;
  static        CommentUtil        singleton;
  private final List<String>  myCommentStrings  = new ArrayList<String>();
  private final List<Matcher> myCommentMatchers = new ArrayList<Matcher>();

  public CommentUtil(@NotNull RearrangerSettings settings) {
    mySettings = settings;
    createCommentPatternList();
    createCommentMatcherList();
    singleton = this;
  }

  public static List<String> getCommentStrings() {
    return singleton.myCommentStrings;
  }

  public static List<Matcher> getCommentMatchers() {
    return singleton.myCommentMatchers;
  }

  private void createCommentMatcherList() {
    if (StringUtil.isEmpty(mySettings.getGlobalCommentPattern())) {
      myCommentMatchers.clear();
      for (String comment : myCommentStrings) {
        Matcher matcher = getMatcher(comment);
        myCommentMatchers.add(matcher);
      }
    }
    else {
      // create only one Matcher for the global comment pattern.
      myCommentMatchers.clear();
      myCommentMatchers.add((getMatcher(mySettings.getGlobalCommentPattern())));
    }
  }

  /**
   * Strips any "\n" patterns off the front and end of the comment; inserts "\n*" patterns at front and end
   * of the comment; and creates a Matcher for the comment.  This will ultimately match any sequence of
   * blank lines before/after the separator comment.
   * Also ignores leading blanks after any newline character.
   *
   * @param comment comment string, already 'escaped' so it is safe for literal matching
   * @return matcher which will match that comment, ignoring any leading or trailing newline characters and
   *         any leading space.
   */
  private Matcher getMatcher(String comment) {
    StringBuilder sb = new StringBuilder(comment);
    /**
     * remove any literal newline characters or escaped equivalent ('\n') at beginning of comment.
     */
    while (true) {
      if (sb.length() >= 2 && sb.charAt(0) == '\\' && sb.charAt(1) == 'n') {
        sb.delete(0, 2);
        continue;
      }
      if (sb.length() >= 1 && sb.charAt(0) == '\n') {
        sb.delete(0, 1);
        continue;
      }
      break;
    }
    /**
     * similarly, remove any literal newline characters or escaped equivalents at end of comment.
     */
    while (true) {
      if (sb.length() >= 2 && sb.charAt(sb.length() - 2) == '\\' && sb.charAt(sb.length() - 1) == 'n') {
        sb.delete(sb.length() - 2, sb.length());
        continue;
      }
      if (sb.length() >= 1 && sb.charAt(sb.length() - 1) == '\n') {
        sb.delete(sb.length() - 1, sb.length());
        continue;
      }
      break;
    }
    if (sb.length() > 0) {
      // now replace all intermediate newline characters with an expression to match leading spaces as well.
      String intermediate = sb.toString().replaceAll("\\\\n", "\\\\n\\\\s*");
      sb.replace(0, sb.length(), intermediate);
      sb.insert(0, "([\\n\\s]*");
      sb.append(")+[\\n\\s]*?\\n+");
    }
    else {
      sb.replace(0, sb.length(), "[\\n\\s]*?\\n+");
    }
    Matcher matcher = Pattern.compile(sb.toString()).matcher("");
    return matcher;
  }

  private void createCommentPatternList() {
    for (Rule rule : mySettings.getClassOrderAttributeList()) {
      rule.addCommentPatternsToList(myCommentStrings);
    }
    for (Rule rule : mySettings.getItemOrderAttributeList()) {
      rule.addCommentPatternsToList(myCommentStrings);
    }
    mySettings.getExtractedMethodsSettings().addCommentPatternsToList(myCommentStrings);
  }

  /**
   * Calculate the apparent length of a string if tabs are expanded.
   * Leading tabs are no problem; embedded tabs would be, if the %FS% fill string expansions are not multiples of
   * "%FS%".length() == 4, because after expansion the embedded tabs would mis-align.  However, I'm not going to
   * address that yet.  (This is just to fix Thomas Singer's bug with leading tab.)
   *
   * @param s
   * @param tabSize
   * @return
   */
  private static int logicalLength(String s, int tabSize) {
    int result = 0;
    boolean sawNonTab = false;
    for (char c : s.toCharArray()) {
      if (c == '\t' && !sawNonTab) {
        result += tabSize;
      }
      else {
        sawNonTab = true;
        result++;
      }
    }
    return result;
  }

  /**
   * Utility to take a comment string which may contain %FS% keywords and replace them with equal number of
   * fill characters to cause the comment to fill an entire line.
   *
   * @param comment    String containing comment; may contain zero or more %FS% keywords; may be multiline.
   * @param width      desired column width of line.
   * @param tabSize
   * @param fillString string containing characters to replicate as needed for fill. @return
   */
  public static String expandFill(String comment,
                                  int width,
                                  int tabSize,
                                  String fillString)
  {
    StringBuilder result = new StringBuilder(comment.length() * 2);
    int EOLindex = 0;
    StringBuilder fillChars = new StringBuilder();
    if (fillString.length() == 0) {
      fillString = " "; // fill with spaces if no pattern supplied
    }
    do {
      // strip next line from previous end of line index to next new line character (or end of string if none)
      int index = comment.indexOf('\n', EOLindex);
      if (index < 0) {
        index = comment.length();
      }
      else {
        index++; // include newline character
      }
      String str = comment.substring(EOLindex, index);
      if (str.length() > 0) {
        // count number of %FS% occurrences in the string.
        int nFS = 0;
        int offset = 0;
        while ((offset = str.indexOf("%FS%", offset)) >= 0) {
          nFS++;
          offset += 4; // bump past %FS%
        }
        // determine line length excluding %FS% strings.  Don't count final newline character, if present.
        int fixedLength = logicalLength(str, tabSize) - nFS * 4;
        if (str.charAt(str.length() - 1) == '\n') {
          fixedLength--;
        }
        // determine number of characters to fill
        int fillLength = width - fixedLength;
        int[] fillWidths = new int[nFS];
        int maxFillWidth = 0;
        // do no filling if the length of the comment is already the desired width (or greater).
        if (fillLength > 0 && nFS > 0) {
          int eachFill = fillLength / nFS;
          int remainder = fillLength % nFS;
          for (int i = 0; i < nFS; i++) {
            // calculate fill widths for each %FS%.
            // any remainder (fillLength % nFS) is distributed among the first %FS% fills.
            fillWidths[i] = eachFill + (i < remainder ? 1 : 0);
            maxFillWidth = Math.max(maxFillWidth, fillWidths[i]);
          }
        }
        // get enough fill chars
        fillChars.ensureCapacity(maxFillWidth);
        while (fillChars.length() < maxFillWidth) {
          fillChars.append(fillString);
        }
        // now do the expansion
        offset = 0;
        int previousOffset = 0;
        int fillWidthIndex = 0;
        while ((offset = str.indexOf("%FS%", offset)) >= 0) {
          // copy everything from previousOffset to offset.
          result.append(str.substring(previousOffset, offset));
          offset += 4; // bump past %FS%
          previousOffset = offset;
          // then expand the fill string to fillWidths[fillWidthIndex] characters and append it.
          result.append(fillChars.toString().substring(0, fillWidths[fillWidthIndex++]));
          // todo - JDK 1.5, .append(fillChars, 0, width)
        }
        // copy everything from previousOffset to end of line, and append a new line.
        result.append(str.substring(previousOffset));
      }
      EOLindex = index;
    }
    while (EOLindex < comment.length());
    return result.toString();
  }
}
