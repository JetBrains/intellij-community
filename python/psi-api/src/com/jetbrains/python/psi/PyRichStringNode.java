package com.jetbrains.python.psi;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * @author Mikhail Golubev
 */
public interface PyRichStringNode extends PsiElement {

  enum Modifier {
    UNICODE,
    BYTES,
    RAW,
    FORMATTED
  }

  /**
   * @return string prefix, e.g. "UR", "b" etc.
   */
  @NotNull
  String getPrefix();

  int getPrefixLength();

  /**
   * @return the same text as {@code getNode().getText().substring(getPrefixLength())}
   */
  @NotNull
  String getTextWithoutPrefix();

  /**
   * @return <em>relative</em> range of the content (excluding prefix and quotes)
   * @see #getAbsoluteContentRange()
   */
  @NotNull
  TextRange getContentRange();

  /**
   * @return <em>absolute</em> content range that accounts offset of the {@link #getNode() node} in the document
   */
  @NotNull
  TextRange getAbsoluteContentRange();

  /**
   * @return content of the string node between quotes
   */
  @NotNull
  String getContent();

  @NotNull
  List<Pair<TextRange, String>> getDecodedFragments();

  @NotNull
  String getQuote();

  /**
   * @return the first character of {@link #getQuote()}
   */
  char getQuoteChar();

  boolean isTripleQuoted();

  /**
   * @return true if string literal ends with starting quote
   */
  boolean isTerminated();

  @NotNull
  Set<Modifier> getModifiers();

  /**
   * @return true if given string node contains "u" or "U" prefix
   */
  boolean isUnicode();

  /**
   * @return true if given string node contains "r" or "R" prefix
   */
  boolean isRaw();

  /**
   * @return true if given string node contains "b" or "B" prefix
   */
  boolean isBytes();

  boolean isFormatted();
}
