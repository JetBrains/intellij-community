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
    final int myStart = getTextOffset();
    final ASTNode node = getNode();
    final List<TextRange> result = new ArrayList<>();

    boolean seenText = false;
    for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
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
  
  private static boolean isNewline(@NotNull CharSequence text, @NotNull TextRange range) {
    return range.getLength() == 1 && text.charAt(range.getStartOffset()) == '\n';
  }

  @Override
  protected List<Pair<TextRange, String>> getEncodeReplacements(@NotNull CharSequence input) throws IllegalArgumentException {
    checkForConsistency(input);

    final int indent = YAMLUtil.getIndentToThisElement(this);
    final String indentString = StringUtil.repeatSymbol(' ', indent);

    final List<Pair<TextRange, String>> result = new ArrayList<>();
    int currentLength = 0;
    for (int i = 0; i < input.length(); ++i) {
      if (input.charAt(i) == '\n') {
        result.add(Pair.create(TextRange.from(i, 1), "\n\n" + indentString));
        currentLength = 0;
        continue;
      }

      if (currentLength > MAX_SCALAR_LENGTH_PREDEFINED &&
          input.charAt(i) == ' ' && isSurroundedByNoSpace(input, i)) {
        result.add(Pair.create(TextRange.from(i, 1), "\n" + indentString));
        currentLength = 0;
        continue;
      }
      
      currentLength++;
    }
    
    return result;
  }

  private static void checkForConsistency(@NotNull CharSequence input) throws IllegalArgumentException {
    if (input.length() == 0) {
      throw new IllegalArgumentException("Cannot be empty");
    }
    if (!YAMLGrammarCharUtil.isNonSpaceChar(input.charAt(0)) || !YAMLGrammarCharUtil.isNonSpaceChar(input.charAt(input.length() - 1))) {
      throw new IllegalArgumentException("Cannot have leading or trailing whitespaces");
    }

    final char firstChar = input.charAt(0);
    //noinspection StatementWithEmptyBody
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
      //noinspection StatementWithEmptyBody
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
