// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstFStringFragment;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a replacement field that can appear either inside an f-string literal itself or inside
 * a format specifier of another replacement field, e.g. {@code {  expr  !s:{width}}} and {@code {width}}
 * parts of the f-string {@code f'{  expr  !s:{width}}'}.
 * <p>
 * Note that opening and closing braces are represented with dedicated
 * {@link com.jetbrains.python.PyTokenTypes#FSTRING_FRAGMENT_START} and {@link com.jetbrains.python.PyTokenTypes#FSTRING_FRAGMENT_END}
 * tokens instead of regular types for curly braces.
 */
public interface PyFStringFragment extends PyAstFStringFragment, PyElement {

  /**
   * Returns the primary expression of this fragment, i.e. the one that appears after the opening brace and
   * before a type conversion, a format specifier or a closing curly brace. Nested expressions inside
   * fragments of format specifier can be accessed as primary expressions of nested fragments retrieved with 
   * {@code getFormatPart().getFragments()}.
   */
  @Override
  @Nullable
  default PyExpression getExpression() {
    return (PyExpression)PyAstFStringFragment.super.getExpression();
  }

  /**
   * Returns an optional format specifier part of a replacement field. It always starts with a colon and spans up to
   * the closing brace of the fragment itself.
   * <p>
   * For instance, for the fragment {@code {  expr  !s:{width}}} it's {@code !s:{width}}.
   */
  @Override
  @Nullable
  default PyFStringFragmentFormatPart getFormatPart() {
    return (PyFStringFragmentFormatPart)PyAstFStringFragment.super.getFormatPart();
  }
}
