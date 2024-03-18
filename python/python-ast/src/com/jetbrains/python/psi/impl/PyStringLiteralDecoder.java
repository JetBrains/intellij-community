// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.ast.PyAstFile;
import com.jetbrains.python.ast.PyAstStringElement;
import com.jetbrains.python.psi.FutureFeature;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PyStringLiteralDecoder {
  public static final Pattern PATTERN_ESCAPE = Pattern.compile("(\\{\\{|}})|" +                // 1
                                                               "\\\\(" +
                                                               "\n|\\\\|'|\"|a|b|f|n|r|t|v|" + // 2
                                                               "([0-7]{1,3})|" +               // 3
                                                               "x([0-9a-fA-F]{1,2})|" +        // 4
                                                               "N(\\{.*?})|" +                 // 5
                                                               "u([0-9a-fA-F]{4})|" +          // 6 
                                                               "U([0-9a-fA-F]{8})" +           // 7
                                                               ")");

  private enum EscapeRegexGroup {
    WHOLE_MATCH,
    FSTRING_BRACE,
    ESCAPED_SUBSTRING,
    OCTAL,
    HEXADECIMAL,
    UNICODE_NAMED,
    UNICODE_16BIT,
    UNICODE_32BIT,
  }

  private static final Map<String, String> escapeMap = initializeEscapeMap();

  @NotNull
  private static Map<String, String> initializeEscapeMap() {
    Map<String, String> map = new HashMap<>();
    map.put("\n", "\n");
    map.put("\\", "\\");
    map.put("'", "'");
    map.put("\"", "\"");
    map.put("a", "\001");
    map.put("b", "\b");
    map.put("f", "\f");
    map.put("n", "\n");
    map.put("r", "\r");
    map.put("t", "\t");
    map.put("v", "\013");
    return map;
  }

  private final PyAstStringElement myNode;

  public PyStringLiteralDecoder(@NotNull PyAstStringElement node) {
    myNode = node;
  }

  @NotNull
  public List<Pair<TextRange, String>> decodeContent() {
    return decodeRange(myNode.getContentRange());
  }

  @NotNull
  public List<Pair<TextRange, String>> decodeRange(@NotNull TextRange range) {
    return decodeFragment(range.substring(myNode.getText()), range.getStartOffset());
  }

  @NotNull
  private List<Pair<TextRange, String>> decodeFragment(@NotNull String encoded, int offset) {
    final boolean raw = myNode.isRaw();
    final boolean unicode = myNode.isUnicode() || isUnicodeByDefault();
    final boolean formatted = myNode.isFormatted();

    final List<Pair<TextRange, String>> result = new ArrayList<>();
    final Matcher escMatcher = PATTERN_ESCAPE.matcher(encoded);
    int index = 0;
    while (escMatcher.find(index)) {
      if (index < escMatcher.start()) {
        final TextRange range = TextRange.create(index, escMatcher.start());
        final TextRange offsetRange = range.shiftRight(offset);
        result.add(Pair.create(offsetRange, range.substring(encoded)));
      }

      final String fStringBrace = escapeRegexGroup(escMatcher, EscapeRegexGroup.FSTRING_BRACE);
      final String octal = escapeRegexGroup(escMatcher, EscapeRegexGroup.OCTAL);
      final String hex = escapeRegexGroup(escMatcher, EscapeRegexGroup.HEXADECIMAL);
      // TODO: Implement unicode character name escapes: EscapeRegexGroup.UNICODE_NAMED
      final String unicode16 = escapeRegexGroup(escMatcher, EscapeRegexGroup.UNICODE_16BIT);
      final String unicode32 = escapeRegexGroup(escMatcher, EscapeRegexGroup.UNICODE_32BIT);
      final String wholeMatch = escapeRegexGroup(escMatcher, EscapeRegexGroup.WHOLE_MATCH);
      assert wholeMatch != null;

      final boolean escapedUnicode = !raw || unicode;

      final String str;
      if (fStringBrace != null) {
        str = formatted ? wholeMatch.substring(0, 1) : wholeMatch;
      }
      else if (!raw && octal != null) {
        str = new String(new char[]{(char)Integer.parseInt(octal, 8)});
      }
      else if (!raw && hex != null) {
        str = new String(new char[]{(char)Integer.parseInt(hex, 16)});
      }
      else if (escapedUnicode && unicode16 != null) {
        str = unicode ? new String(new char[]{(char)Integer.parseInt(unicode16, 16)}) : wholeMatch;
      }
      else if (escapedUnicode && unicode32 != null) {
        String s = wholeMatch;
        if (unicode) {
          try {
            s = new String(Character.toChars((int)Long.parseLong(unicode32, 16)));
          }
          catch (IllegalArgumentException ignored) {
          }
        }
        str = s;
      }
      else if (raw) {
        str = wholeMatch;
      }
      else {
        final String toReplace = escapeRegexGroup(escMatcher, EscapeRegexGroup.ESCAPED_SUBSTRING);
        str = escapeMap.get(toReplace);
      }

      if (str != null) {
        final TextRange wholeMatchRange = TextRange.create(escMatcher.start(), escMatcher.end());
        result.add(Pair.create(wholeMatchRange.shiftRight(offset), str));
      }

      index = escMatcher.end();
    }
    final TextRange range = TextRange.create(index, encoded.length());
    final TextRange offRange = range.shiftRight(offset);
    result.add(Pair.create(offRange, range.substring(encoded)));
    return result;
  }

  @Nullable
  private static String escapeRegexGroup(@NotNull Matcher matcher, EscapeRegexGroup group) {
    return matcher.group(group.ordinal());
  }

  private boolean isUnicodeByDefault() {
    if (!LanguageLevel.forElement(myNode).isPython2()) {
      return true;
    }
    final PsiFile file = myNode.getContainingFile();
    if (file instanceof PyAstFile pyFile) {
      return pyFile.hasImportFromFuture(FutureFeature.UNICODE_LITERALS);
    }
    return false;
  }
}
