// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents an f-string, a special kind of interpolated string literal introduced in Python 3.6.
 * 
 * Unlike {@link PyPlainStringElement} these elements are composite and consist of several kinds of tokens
 * and normal PSI trees for embedded expressions.
 * 
 * @see com.jetbrains.python.PyTokenTypes#FSTRING_START
 * @see com.jetbrains.python.PyTokenTypes#FSTRING_TEXT
 * @see PyFStringFragment
 * @see com.jetbrains.python.PyTokenTypes#FSTRING_END
 */
public interface PyFormattedStringElement extends PyStringElement, PyElement {
  /**
   * Returns a list of replacement fields containing expressions which values should be embedded into this literal content.
   * <p>
   * Note that only top-level fields are included in the result. To access optional fragments that might appear in
   * format specifier of a another fragment one should use {@link PyFStringFragment#getFormatPart()} and
   * {@link PyFStringFragmentFormatPart#getFragments()}.
   */
  @NotNull
  List<PyFStringFragment> getFragments();

  /**
   * Returns ranges of literal parts of an f-string, e.g. all other parts of an f-string content that don't belong to
   * expression fragments.
   * <p>
   * These ranges don't include literal format specifier parts of fragments and should be completely
   * covered by {@link PyStringElement#getContentRange()}.
   * <p>
   * For instance, for the following f-string:
   * <p>
   * <pre>{@code
   *   f'foo{bar:format}baz'
   * }</pre>
   * this method will return ranges {@code (2, 5)} and {@code (17, 20)}.
   */
  @NotNull
  List<TextRange> getLiteralPartRanges();
}
