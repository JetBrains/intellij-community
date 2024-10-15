/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.jetbrains.python.ast.PyAstStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;


/**
 * A string literal expression can consist of a number of implicitly concatenated string literals
 * each with its own set of quotes and prefixes. For instance, the following string literal expression 
 * in the right hand side of the assignment:
 * <p>
 * <code><pre>
 * s = """foo""" + rb'\bar' + f'{baz}'
 * </pre></code>
 * <p>
 * includes three distinct parts: a triple quoted string literal {@code """foo"""} without a prefix,
 * a "raw" bytes literal {@code rb'\bar'} using apostrophes as quotes and a formatted string literal
 * {@code f'{baz}'} containing the value of the expression {@code baz}.
 * <p>
 * PSI elements representing each of these parts can be acquired using {@link #getStringElements()} method.
 * Both plain and formatted literals forming a whole string literal expression implement {@link PyStringElement}
 * interface containing methods for retrieving general information about them such as quote types, prefixes,
 * decoded value, etc.
 *
 * @see <a href="https://docs.python.org/3/reference/lexical_analysis.html#string-literal-concatenation">
 *   https://docs.python.org/3/reference/lexical_analysis.html#string-literal-concatenation</a>
 * @see #getStringElements()
 * @see PyStringElement
 * @see PyPlainStringElement
 * @see PyFormattedStringElement
 */
public interface PyStringLiteralExpression extends PyAstStringLiteralExpression, PyLiteralExpression, StringLiteralExpression, PsiLanguageInjectionHost {
  /**
   * Returns a list of implicitly concatenated string elements composing this literal expression.
   */
  @NotNull
  @Override
  default List<PyStringElement> getStringElements() {
    //noinspection unchecked
    return (List<PyStringElement>)PyAstStringLiteralExpression.super.getStringElements();
  }

  int valueOffsetToTextOffset(int valueOffset);

  /**
   * @return true if this element has single string node and its type is {@link com.jetbrains.python.PyTokenTypes#DOCSTRING}
   */
  boolean isDocString();

  /**
   * Checks whether this literal contains expression fragments. Formatted "f" nodes without any fragments are not considered interpolated.
   * <p>
   * For example, the "glued" literal
   * <pre>{@code f'foo{expr}' "bar"}</pre> is considered interpolated, but {@code f'foo'} and {@code """bar"""} are not.
   *
   * @see PyFormattedStringElement#getFragments()
   * @see PyStringElement#isFormatted()
   */
  boolean isInterpolated();
}
