package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.lexer.YAMLGrammarCharUtil;
import org.jetbrains.yaml.psi.YAMLQuotedText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class YAMLQuotedTextImpl extends YAMLScalarImpl implements YAMLQuotedText {
  private final boolean myIsSingleQuoted;

  public YAMLQuotedTextImpl(@NotNull ASTNode node) {
    super(node);
    myIsSingleQuoted = getNode().getFirstChildNode().getElementType() == YAMLTokenTypes.SCALAR_STRING;
  }

  @NotNull
  @Override
  public List<TextRange> getContentRanges() {
    List<TextRange> result = new ArrayList<>();

    final List<String> lines = StringUtil.split(getText(), "\n", true, false);
    // First line has opening quote
    int cumulativeOffset = 0;
    for (int i = 0; i < lines.size(); ++i) {
      final String line = lines.get(i);

      int lineStart = 0;
      int lineEnd = line.length();
      if (i == 0) {
        lineStart++;
      }
      else {
        while (lineStart < line.length() && YAMLGrammarCharUtil.isSpaceLike(line.charAt(lineStart))) {
          lineStart++;
        }
      }
      if (i == lines.size() - 1) {
        // Last line has closing quote
        lineEnd--;
      }
      else {
        while (lineEnd > lineStart && YAMLGrammarCharUtil.isSpaceLike(line.charAt(lineEnd - 1))) {
          lineEnd--;
        }
      }

      result.add(TextRange.create(lineStart, lineEnd).shiftRight(cumulativeOffset));
      cumulativeOffset += line.length() + 1;
    }

    return result;
  }

  @NotNull
  @Override
  protected String getRangesJoiner(@NotNull CharSequence text, @NotNull List<TextRange> contentRanges, int indexBefore) {
    final TextRange leftRange = contentRanges.get(indexBefore);
    if (leftRange.isEmpty() || !isSingleQuote() && text.charAt(leftRange.getEndOffset() - 1) == '\\') {
      return "\n";
    }
    else if (contentRanges.get(indexBefore + 1).isEmpty()) {
      return "";
    }
    else {
      return " ";
    }
  }

  @SuppressWarnings("AssignmentToForLoopParameter")
  @Override
  protected List<Pair<TextRange, String>> getDecodeReplacements(@NotNull CharSequence input) {
    List<Pair<TextRange, String>> result = new ArrayList<>();
    
    for (int i = 0; i + 1 < input.length(); ++i) {
      
      if (isSingleQuote() && input.charAt(i) == '\'' && input.charAt(i + 1) == '\'') {
        result.add(Pair.create(TextRange.from(i, 2), "'"));
        i++;
      }
      else if (!isSingleQuote() && input.charAt(i) == '\\') {
        if (input.charAt(i + 1) == '\n') {
          result.add(Pair.create(TextRange.from(i, 2), ""));
          i++;
          continue;
        }
        final int length = Escaper.findEscapementLength(input, i);
        final int charCode = Escaper.toUnicodeChar(input, i, length);
        final TextRange range = TextRange.create(i, Math.min(i + length + 1, input.length()));
        result.add(Pair.create(range, Character.toString((char)charCode)));
        i += range.getLength() - 1;
      }
    }
    return result;
  }

  @Override
  protected List<Pair<TextRange, String>> getEncodeReplacements(@NotNull CharSequence input) throws IllegalArgumentException {
    // check for consistency
    if (isSingleQuote()) {
      for (int i = 0; i < input.length(); ++i) {
        if (input.charAt(i) == '\n' && !isSurroundedByNoSpace(input, i)) {
          throw new IllegalArgumentException("Newlines with spaces around are not convertible");
        }
      }
    }
    
    final int indent = YAMLUtil.getIndentToThisElement(this);
    final String indentString = StringUtil.repeatSymbol(' ', indent);
    
    final List<Pair<TextRange, String>> result = new ArrayList<>();
    int currentLength = 0;
    for (int i = 0; i < input.length(); ++i) {
      final char c = input.charAt(i);
      if (c == '\n') {
        if (!isSingleQuote() && i + 1 < input.length() && YAMLGrammarCharUtil.isSpaceLike(input.charAt(i + 1))) {
          result.add(Pair.create(TextRange.from(i, 1), "\\n\\\n" + indentString + "\\"));
        }
        else if (!isSingleQuote() && i + 1 < input.length() && input.charAt(i + 1) == '\n') {
          result.add(Pair.create(TextRange.from(i, 1), "\\\n" + indentString + "\\n"));
        }
        else {
          result.add(Pair.create(TextRange.from(i, 1), "\n\n" + indentString));
        }
        currentLength = 0;
        continue;
      }


      if (currentLength > MAX_SCALAR_LENGTH_PREDEFINED 
          && (!isSingleQuote() || (c == ' ' && isSurroundedByNoSpace(input, i)))) {
        final String replacement;
        if (isSingleQuote()) {
          replacement = "\n" + indentString;
        }
        else if (YAMLGrammarCharUtil.isSpaceLike(c)) {
          replacement = "\\\n" + indentString + "\\";
        }
        else {
          replacement = "\\\n" + indentString;
        }
        result.add(Pair.create(TextRange.from(i, isSingleQuote() ? 1 : 0), replacement));
        currentLength = 0;
      }

      currentLength++;
      
      if (isSingleQuote() && c == '\'') {
        result.add(Pair.create(TextRange.from(i, 1), "''"));
        continue;
      }
      
      if (!isSingleQuote()) {
        if (c == '"') {
          result.add(Pair.create(TextRange.from(i, 1), "\\\""));
        }
        else if (c == '\\') {
          result.add(Pair.create(TextRange.from(i, 1), "\\\\"));
        }
      }
    }
    return result;
  }

  @Override
  public boolean isMultiline() {
    return textContains('\n');
  }

  public boolean isSingleQuote() {
    return myIsSingleQuoted;
  }

  @Override
  public String toString() {
    return "YAML quoted text";
  }
  
  private static class Escaper {
    private static final int[][] ONE_LETTER_CONVERSIONS = new int[][] {
      {'0', 0},
      {'a', 7},
      {'b', 8},
      {'t', 9},
      {9, 9},
      {'n', 10},
      {'v', 11},
      {'f', 12},
      {'r', 13},
      {'e', 27},
      {' ', 32},
      {'"', 34},
      {'/', 47},
      {'\\', 92},
      {'N', 133},
      {'_', 160},
      {'L', 8232},
      {'P', 8233},
    };
    
    private static final NotNullLazyValue<Map<Integer, Integer>> ESC_TO_CODE = new NotNullLazyValue<Map<Integer, Integer>>() {
      @NotNull
      @Override
      protected Map<Integer, Integer> compute() {
        final HashMap<Integer, Integer> map = new HashMap<>(ONE_LETTER_CONVERSIONS.length);
        for (int[] conversion : ONE_LETTER_CONVERSIONS) {
          map.put(conversion[0], conversion[1]);
        }
        return map;
      }
    };

    private static final NotNullLazyValue<Map<Integer, Integer>> CODE_TO_ESC = new NotNullLazyValue<Map<Integer, Integer>>() {
      @NotNull
      @Override
      protected Map<Integer, Integer> compute() {
        final HashMap<Integer, Integer> map = new HashMap<>(ONE_LETTER_CONVERSIONS.length);
        for (int[] conversion : ONE_LETTER_CONVERSIONS) {
          map.put(conversion[1], conversion[2]);
        }
        return map;
      }
    };
    
    static int findEscapementLength(@NotNull CharSequence text, int pos) {
      if (pos + 1 >= text.length() || text.charAt(pos) != '\\') {
        throw new IllegalArgumentException("This is not an escapement start");
      }
      
      final char c = text.charAt(pos + 1);
      if (c == 'x') {
        return 3;
      }
      else if (c == 'u') {
        return 5;
      }
      else if (c == 'U') {
        return 9;
      }
      else {
        return 1;
      }
    }
    
    static int toUnicodeChar(@NotNull CharSequence text, int pos, int length) {
      if (length > 1) {
        CharSequence s = text.subSequence(pos + 2, Math.min(text.length(), pos + length + 1));
        try {
          return Integer.parseInt(s.toString(), 16);
        }
        catch (NumberFormatException e) {
          return (int)'?';
        }
      }
      else {
        final Integer result = ESC_TO_CODE.getValue().get((int)text.charAt(pos + 1));
        return ObjectUtils.notNull(result, (int)text.charAt(pos + 1));
      }
    }
  }
}
