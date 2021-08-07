package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.lexer.YAMLGrammarCharUtil;
import org.jetbrains.yaml.psi.YAMLScalar;

import java.util.ArrayList;
import java.util.List;

public class YAMLPlainTextImpl extends YAMLScalarImpl implements YAMLScalar {
  public YAMLPlainTextImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public List<TextRange> getContentRanges() {
    final int myStart = getTextRange().getStartOffset();
    final List<TextRange> result = new ArrayList<>();

    TextRange textRange = null;
    for (ASTNode child = getFirstContentNode(); child != null; child = child.getTreeNext()) {
      TextRange childRange = child.getTextRange().shiftRight(-myStart);
      if (child.getElementType() == YAMLTokenTypes.TEXT) {
        if (textRange != null) result.add(textRange);
        textRange = childRange;
      }
      else if (child.getElementType() == YAMLTokenTypes.EOL) {
        if (textRange == null) {
          result.add(childRange);
        }
        else {
          result.add(textRange.union(childRange));
          textRange = null;
        }
      }
    }
    if (textRange != null) result.add(textRange);

    return result;
  }

  @Override
  public @NotNull YamlScalarTextEvaluator getTextEvaluator() {
    return new YamlScalarTextEvaluator<>(this) {

      @NotNull
      @Override
      public List<TextRange> getContentRanges() {
        final int myStart = getTextRange().getStartOffset();
        final List<TextRange> result = new ArrayList<>();

        boolean seenText = false;
        for (ASTNode child = getFirstContentNode(); child != null; child = child.getTreeNext()) {
          if (child.getElementType() == YAMLTokenTypes.TEXT) {
            seenText = true;
            result.add(child.getTextRange().shiftRight(-myStart));
          }
          else if (child.getElementType() == YAMLTokenTypes.EOL) {
            if (!seenText) {
              result.add(child.getTextRange().shiftRight(-myStart));
            }
            seenText = false;
          }
        }

        return result;
      }

      @NotNull
      @Override
      protected String getRangesJoiner(@NotNull CharSequence text, @NotNull List<TextRange> contentRanges, int indexBefore) {
        if (isNewline(text, contentRanges.get(indexBefore)) || isNewline(text, contentRanges.get(indexBefore + 1))) {
          return "";
        }
        else {
          return " ";
        }
      }

      private boolean isNewline(@NotNull CharSequence text, @NotNull TextRange range) {
        return range.getLength() == 1 && text.charAt(range.getStartOffset()) == '\n';
      }
    };
  }

  @Override
  protected List<Pair<TextRange, String>> getEncodeReplacements(@NotNull CharSequence input) throws IllegalArgumentException {
    // doesn't replace anything, just checks that input is supported
    checkForConsistency(input);
    return super.getDecodeReplacements(input);
  }

  private static void checkForConsistency(@NotNull CharSequence input) throws IllegalArgumentException {
    if (input.length() == 0) {
      throw new IllegalArgumentException("Cannot be empty");
    }
    if (!YAMLGrammarCharUtil.isNonSpaceChar(input.charAt(0)) || !YAMLGrammarCharUtil.isNonSpaceChar(input.charAt(input.length() - 1))) {
      throw new IllegalArgumentException("Cannot have leading or trailing whitespaces");
    }

    final char firstChar = input.charAt(0);
    if ((firstChar == '?' || firstChar == ':' || firstChar == '-') && input.length() > 1 && YAMLGrammarCharUtil.isPlainSafe(input.charAt(1))) {
      // then it's OK
    }
    else if (YAMLGrammarCharUtil.isIndicatorChar(firstChar)) {
      throw new IllegalArgumentException("Could not start with indicator chars");
    }

    for (int i = 1; i < input.length(); ++i) {
      final char c = input.charAt(i);
      if (c == '\n' && !isSurroundedByNoSpace(input, i)) {
        throw new IllegalArgumentException("Could not form line with leading/trailing whitespace");
      }
      if (YAMLGrammarCharUtil.isSpaceLike(c)
        || (YAMLGrammarCharUtil.isPlainSafe(c) && c != ':' && c != '#')
        || (c == '#' && YAMLGrammarCharUtil.isNonSpaceChar(input.charAt(i - 1)))
        || (c == ':' && i + 1 < input.length() && YAMLGrammarCharUtil.isPlainSafe(input.charAt(i + 1)))) {
        // Then it's OK
      }
      else {
        throw new IllegalArgumentException("Restricted characters appeared");
      }
    }
  }

  @Override
  public String toString() {
    return "YAML plain scalar text";
  }


  @Override
  public boolean isMultiline() {
    return getNode().findChildByType(YAMLTokenTypes.EOL) != null;
  }
}
