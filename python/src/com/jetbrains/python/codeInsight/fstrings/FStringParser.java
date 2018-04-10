/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight.fstrings;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class FStringParser {
  private final String myNodeText;
  private final TextRange myNodeContentRange;
  private final List<Fragment> myFragments = new ArrayList<>();
  private final List<Integer> mySingleRightBraces = new ArrayList<>();

  @NotNull
  public static ParseResult parse(@NotNull String nodeText) {
    final FStringParser parser = new FStringParser(nodeText);
    parser.parseTopLevel();
    return new ParseResult(parser.mySingleRightBraces, parser.myFragments);
  }

  private FStringParser(@NotNull String nodeText) {
    myNodeText = nodeText;
    myNodeContentRange = PyStringLiteralExpressionImpl.getNodeTextRange(myNodeText);
  }

  private void parseTopLevel() {
    int offset = myNodeContentRange.getStartOffset();
    while (offset < myNodeContentRange.getEndOffset()) {
      // First, skip named unicode escapes like "\N{LATIN SMALL LETTER A}" wherever they are
      final int nextOffset = skipNamedUnicodeEscape(offset);
      if (offset != nextOffset) {
        offset = nextOffset;
        continue;
      }
      
      final char c1 = myNodeText.charAt(offset);
      final char c2 = offset + 1 < myNodeContentRange.getEndOffset() ? myNodeText.charAt(offset + 1) : '\0';
      
      if ((c1 == '{' && c2 == '{') || (c1 == '}' && c2 == '}')) {
        offset += 2;
        continue;
      }
      else if (c1 == '{') {
        offset = parseFragment(offset, 1);
        continue;
      }
      // Will be marked as errors
      else if (c1 == '}') {
        mySingleRightBraces.add(offset);
      }
      offset++;
    }
  }

  private int parseFragment(int leftBraceOffset, int depth) {
    assert myNodeText.charAt(leftBraceOffset) == '{';

    int contentEndOffset = -1;
    int rightBraceOffset = -1;

    int bracesBalance = 0;
    char stringLiteralQuote = '\0';
    int quotesNum = 0;
    
    // Used for f-strings validation
    boolean containsNamedUnicodeEscape = false;
    int firstHashOffset = -1;

    int offset = leftBraceOffset + 1;
    while (offset < myNodeContentRange.getEndOffset()) {
      // Actually they aren't allowed inside expression fragments, but we skip them anyway to prevent injection errors
      final int nextOffset = skipNamedUnicodeEscape(offset);
      if (offset != nextOffset) {
        containsNamedUnicodeEscape = true;
        offset = nextOffset;
        continue;
      }
      
      final char c1 = myNodeText.charAt(offset);
      final char c2 = offset + 1 < myNodeContentRange.getEndOffset() ? myNodeText.charAt(offset + 1) : '\0';
      final char c3 = offset + 2 < myNodeContentRange.getEndOffset() ? myNodeText.charAt(offset + 2) : '\0';
      if (contentEndOffset == -1) {
        if (stringLiteralQuote != '\0') {
          if (c1 == '\'' || c1 == '"') {
            final int size = c2 == c1 && c3 == c1 ? 3 : 1;
            if (stringLiteralQuote == c1 && size == quotesNum) {
              stringLiteralQuote = '\0';
              offset += size;
              continue;
            }
          }
          else if (c1 == '\\') {
            offset += 2;
            continue;
          }
        }
        else if (c1 == '\'' || c1 == '"') {
          quotesNum = c2 == c1 && c3 == c1 ? 3 : 1; 
          stringLiteralQuote = c1;
          offset += quotesNum;
          continue;
        }
        else if (c1 == '#' && firstHashOffset == -1) {
          firstHashOffset = offset;
        }
        else if (c1 == '{' || c1 == '[' || c1 == '(') {
          bracesBalance++;
        }
        else if (bracesBalance > 0 && (c1 == '}' || c1 == ']' || c1 == ')')) {
          bracesBalance--;
        }
        else if (bracesBalance == 0 && (c1 == '}' || (c1 == '!' && c2 != '=') || c1 == ':')) {
          contentEndOffset = offset;
          if (c1 == '}') {
            rightBraceOffset = offset;
            offset++;
            break;
          }
        }
      }
      else if (c1 == '{') {
        offset = parseFragment(offset, depth + 1);
        continue;
      }
      else if (c1 == '}') {
        rightBraceOffset = offset;
        offset++;
        break;
      }
      offset++;
    }
    if (contentEndOffset == -1) {
      contentEndOffset = offset;
    }
    myFragments.add(new Fragment(leftBraceOffset, 
                                 contentEndOffset, 
                                 rightBraceOffset, 
                                 containsNamedUnicodeEscape, 
                                 firstHashOffset,
                                 depth));
    return offset;
  }

  private int skipNamedUnicodeEscape(int offset) {
    if (StringUtil.startsWith(myNodeText, offset, "\\N{")) {
      final int rightBraceOffset = myNodeText.indexOf('}', offset + 3);
      return rightBraceOffset < 0 ? myNodeContentRange.getEndOffset() : rightBraceOffset + 1;
    }
    return offset;
  }

  public static class Fragment {
    private final int myLeftBraceOffset;
    private final int myRightBraceOffset;
    private final int myContentEndOffset;
    private final boolean myContainsNamedUnicodeEscape;
    private final int myFirstHashOffset;
    private final int myDepth;

    private Fragment(int leftBraceOffset,
                     int contentEndOffset,
                     int rightBraceOffset,
                     boolean containsUnicodeEscape,
                     int firstHashOffset, 
                     int depth) {
      assert leftBraceOffset < contentEndOffset;
      assert rightBraceOffset < 0 || contentEndOffset <= rightBraceOffset;
      assert firstHashOffset < 0 || leftBraceOffset < firstHashOffset && firstHashOffset < contentEndOffset;

      myLeftBraceOffset = leftBraceOffset;
      myRightBraceOffset = rightBraceOffset;
      myContentEndOffset = contentEndOffset;
      myContainsNamedUnicodeEscape = containsUnicodeEscape;
      myFirstHashOffset = firstHashOffset;
      myDepth = depth;
    }

    public int getLeftBraceOffset() {
      return myLeftBraceOffset;
    }

    public int getRightBraceOffset() {
      return myRightBraceOffset;
    }

    public int getContentEndOffset() {
      return myContentEndOffset;
    }

    public boolean containsNamedUnicodeEscape() {
      return myContainsNamedUnicodeEscape;
    }

    public int getFirstHashOffset() {
      return myFirstHashOffset;
    }

    public int getDepth() {
      return myDepth;
    }

    @NotNull
    public TextRange getContentRange() {
      return TextRange.create(myLeftBraceOffset + 1, myContentEndOffset);
    }
  }
  
  public static class ParseResult {
    private final List<Integer> mySingleRightBraces;
    private final List<Fragment> myFragments;

    private ParseResult(@NotNull List<Integer> singleRightBraces, @NotNull List<Fragment> fragments) {
      mySingleRightBraces = singleRightBraces;
      myFragments = ContainerUtil.sorted(fragments, (f1, f2) -> f1.getLeftBraceOffset() - f2.getLeftBraceOffset());
    }

    @NotNull
    public List<Integer> getSingleRightBraces() {
      return Collections.unmodifiableList(mySingleRightBraces);
    }

    @NotNull
    public List<Fragment> getFragments() {
      return Collections.unmodifiableList(myFragments);
    }
  }
}
