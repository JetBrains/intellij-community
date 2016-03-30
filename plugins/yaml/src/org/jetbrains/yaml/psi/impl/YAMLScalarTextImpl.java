package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.lexer.YAMLGrammarCharUtil;
import org.jetbrains.yaml.psi.YAMLScalarText;

import java.util.ArrayList;
import java.util.List;

/**
 * @author oleg
 */
public class YAMLScalarTextImpl extends YAMLBlockScalarImpl implements YAMLScalarText {
  public YAMLScalarTextImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  protected IElementType getContentType() {
    return YAMLTokenTypes.SCALAR_TEXT;
  }

  @NotNull
  @Override
  protected String getRangesJoiner(@NotNull CharSequence leftString, @NotNull CharSequence rightString) {
    if (StringUtil.isEmptyOrSpaces(leftString)) {
      return "\n";
    }
    if (StringUtil.startsWithChar(leftString, ' ') || StringUtil.startsWithChar(leftString, '\t')
      || StringUtil.startsWithChar(rightString, ' ') || StringUtil.startsWithChar(rightString, '\t')) {
      return "\n";
    }
    if (StringUtil.isEmptyOrSpaces(rightString)) {
      return "";
    }
    return " ";
  }

  @Override
  protected List<Pair<TextRange, String>> getEncodeReplacements(@NotNull CharSequence input) throws IllegalArgumentException {
    if (!StringUtil.endsWithChar(input, '\n')) {
      throw new IllegalArgumentException("Should end with a line break");
    }

    int indent = locateIndent();
    if (indent == 0) {
      indent = YAMLUtil.getIndentToThisElement(this) + DEFAULT_CONTENT_INDENT;
    }
    final String indentString = StringUtil.repeatSymbol(' ', indent);

    final List<Pair<TextRange, String>> result = new ArrayList<>();
    int currentLength = 0;
    for (int i = 0; i < input.length(); ++i) {
      if (input.charAt(i) == '\n') {
        final String replacement;
        if (i + 1 >= input.length() || YAMLGrammarCharUtil.isSpaceLike(input.charAt(i + 1))) {
          replacement = "\n" + indentString;
        }
        else {
          replacement = "\n\n" + indentString;
        }
        
        result.add(Pair.create(TextRange.from(i, 1), replacement));
        currentLength = 0;
        continue;
      }

      if (currentLength > MAX_SCALAR_LENGTH_PREDEFINED &&
          input.charAt(i) == ' ' && i + 1 < input.length() && YAMLGrammarCharUtil.isNonSpaceChar(input.charAt(i + 1))) {
        result.add(Pair.create(TextRange.from(i, 1), "\n" + indentString));
        currentLength = 0;
        continue;
      }
      
      currentLength++;
    }

    return result;
  }
  
  @NotNull
  @Override
  public String getTextValue() {
    return super.getTextValue() + "\n";
  }

  @Override
  public String toString() {
    return "YAML scalar text";
  }

}