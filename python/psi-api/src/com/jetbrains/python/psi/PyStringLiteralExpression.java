package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiLanguageInjectionHost;

import java.util.List;

public interface PyStringLiteralExpression extends PyLiteralExpression, StringLiteralExpression, PsiLanguageInjectionHost {
  List<TextRange> getStringValueTextRanges();

  List<ASTNode> getStringNodes();

  int valueOffsetToTextOffset(int valueOffset);

  void iterateCharacterRanges(TextRangeConsumer consumer);

  /**
   * Iterator over decoded string characters.
   */
  interface TextRangeConsumer {
    /**
     * Process a decoded character.
     *
     * @param startOffset start offset in the un-decoded string
     * @param endOffset end offset in the un-decoded string
     * @param value decoded character value
     * @return false in order to stop iteration
     */
    boolean process(int startOffset, int endOffset, String value);
  }
}
