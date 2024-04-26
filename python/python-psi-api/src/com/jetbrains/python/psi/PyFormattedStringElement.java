// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstFormattedStringElement;
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
public interface PyFormattedStringElement extends PyAstFormattedStringElement, PyStringElement, PyElement {
  /**
   * Returns a list of replacement fields containing expressions which values should be embedded into this literal content.
   * <p>
   * Note that only top-level fields are included in the result. To access optional fragments that might appear in
   * format specifier of a another fragment one should use {@link PyFStringFragment#getFormatPart()} and
   * {@link PyFStringFragmentFormatPart#getFragments()}.
   */
  @Override
  @NotNull
  default List<PyFStringFragment> getFragments() {
    //noinspection unchecked
    return (List<PyFStringFragment>)PyAstFormattedStringElement.super.getFragments();
  }
}
