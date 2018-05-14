package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.lexer.YAMLGrammarCharUtil;
import org.jetbrains.yaml.psi.YAMLScalarText;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

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
  protected String getRangesJoiner(@NotNull CharSequence text, @NotNull List<TextRange> contentRanges, int indexBefore) {
    final TextRange leftRange = contentRanges.get(indexBefore);
    final TextRange rightRange = contentRanges.get(indexBefore + 1);
    if (leftRange.isEmpty()) {
      return "\n";
    }
    if (startsWithWhitespace(text, leftRange) || startsWithWhitespace(text, rightRange)) {
      return "\n";
    }
    if (rightRange.isEmpty()) {
      int i = indexBefore + 2;
      // Unfortunately we need to scan to the nearest non-empty line to understand
      // whether we should add a line here 
      while (i < contentRanges.size() && contentRanges.get(i).isEmpty()) {
        i++;
      }
      if (i >= contentRanges.size()) {
        // empty lines until the end
        if (getChompingIndicator() == ChompingIndicator.KEEP) {
          return "\n";
        }
      }
      else if (startsWithWhitespace(text, contentRanges.get(i))) {
        return "\n";
      }
      return "";
    }
    return " ";
  }
  
  private static boolean startsWithWhitespace(@NotNull CharSequence text, @NotNull TextRange range) {
    if (range.isEmpty()) {
      return false;
    }
    final char c = text.charAt(range.getStartOffset());
    return c == ' ' || c == '\t';
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
    boolean currentLineIsIndented = input.length() > 0 && input.charAt(0) == ' ';
    for (int i = 0; i < input.length(); ++i) {
      if (input.charAt(i) == '\n') {
        final String replacement;
        if (i + 1 >= input.length() ||
            YAMLGrammarCharUtil.isSpaceLike(input.charAt(i + 1)) ||
            input.charAt(i + 1) == '\n' ||
            currentLineIsIndented) {
          replacement = "\n" + indentString;
        }
        else {
          replacement = "\n\n" + indentString;
        }
        
        result.add(Pair.create(TextRange.from(i, 1), replacement));
        currentLength = 0;
        currentLineIsIndented = i + 1 < input.length() && input.charAt(i + 1) == ' ';
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
  
  @Override
  public String toString() {
    return "YAML scalar text";
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof YamlPsiElementVisitor) {
      ((YamlPsiElementVisitor)visitor).visitScalarText(this);
    }
    else {
      super.accept(visitor);
    }
  }
}