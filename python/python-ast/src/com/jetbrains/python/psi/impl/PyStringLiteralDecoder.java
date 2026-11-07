// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.ast.PyAstFile;
import com.jetbrains.python.ast.PyAstStringElement;
import com.jetbrains.python.psi.FutureFeature;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApiStatus.Internal
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

  private static @NotNull Map<String, String> initializeEscapeMap() {
    Map<String, String> map = new HashMap<>();
    // A backslash at the end of a line continues the literal on the next line
    map.put("\n", "");
    map.put("\\", "\\");
    map.put("'", "'");
    map.put("\"", "\"");
    map.put("a", "\007");
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

  public @NotNull List<Pair<TextRange, String>> decodeContent() {
    return decodeRange(myNode.getContentRange());
  }

  public @NotNull List<Pair<TextRange, String>> decodeRange(@NotNull TextRange range) {
    return decodeFragment(range.substring(myNode.getText()), range.getStartOffset());
  }

  private @NotNull List<Pair<TextRange, String>> decodeFragment(@NotNull String encoded, int offset) {
    final boolean raw = myNode.isRaw();
    final boolean python2 = LanguageLevel.forElement(myNode).isPython2();
    final boolean unicode = !myNode.isBytes() && (myNode.isUnicode() || isUnicodeByDefault(python2));
    // Python 2 decodes the escapes of a code point in a raw unicode literal. Python 3 decodes no escape in a raw literal.
    final boolean escapedUnicode = !raw || unicode && (python2 || myNode.isUnicode());
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
      final String unicodeName = escapeRegexGroup(escMatcher, EscapeRegexGroup.UNICODE_NAMED);
      final String unicode16 = escapeRegexGroup(escMatcher, EscapeRegexGroup.UNICODE_16BIT);
      final String unicode32 = escapeRegexGroup(escMatcher, EscapeRegexGroup.UNICODE_32BIT);
      final String wholeMatch = escapeRegexGroup(escMatcher, EscapeRegexGroup.WHOLE_MATCH);
      assert wholeMatch != null;

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
      else if (!raw && unicodeName != null) {
        final String character = unicode ? decodeCharacterName(unicodeName.substring(1, unicodeName.length() - 1)) : null;
        str = character != null ? character : wholeMatch;
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

  private static @Nullable String escapeRegexGroup(@NotNull Matcher matcher, EscapeRegexGroup group) {
    return matcher.group(group.ordinal());
  }

  /**
   * Returns the character that Python gives for the escape {@code \N{name}}, or {@code null} when the name is unknown.
   * <p>
   * The JDK knows no name alias and no algorithmic name of a CJK ideograph or a Hangul syllable, so these names give {@code null}.
   */
  private static @Nullable String decodeCharacterName(@NotNull String name) {
    // Python compares the ASCII letters of a name without case.
    // String.toUpperCase also maps letters such as "ı" to ASCII letters.
    if (!name.chars().allMatch(c -> c < 0x80)) {
      return null;
    }
    final String upperName = name.toUpperCase(Locale.ROOT);
    final int codePoint;
    try {
      codePoint = Character.codePointOf(upperName);
    }
    catch (IllegalArgumentException e) {
      return null;
    }
    // Character.codePointOf also accepts names that Python rejects: a name with surrounding whitespace,
    // the Unicode 1.0 name of a control character, and the block name of a character without a name.
    if (!upperName.equals(Character.getName(codePoint)) ||
        Character.getType(codePoint) == Character.CONTROL ||
        upperName.equals(getBlockName(codePoint))) {
      return null;
    }
    return Character.toString(codePoint);
  }

  private static @Nullable String getBlockName(int codePoint) {
    final Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
    if (block == null) {
      return null;
    }
    return block.toString().replace('_', ' ') + " " + Integer.toHexString(codePoint).toUpperCase(Locale.ROOT);
  }

  private boolean isUnicodeByDefault(boolean python2) {
    if (!python2) {
      return true;
    }
    final PsiFile file = myNode.getContainingFile();
    if (file instanceof PyAstFile pyFile) {
      return pyFile.hasImportFromFuture(FutureFeature.UNICODE_LITERALS);
    }
    return false;
  }
}
