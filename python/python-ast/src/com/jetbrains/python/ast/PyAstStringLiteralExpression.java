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
package com.jetbrains.python.ast;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.StringLiteralExpression;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
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
 * Both plain and formatted literals forming a whole string literal expression implement {@link PyAstStringElement}
 * interface containing methods for retrieving general information about them such as quote types, prefixes,
 * decoded value, etc.
 *
 * @see <a href="https://docs.python.org/3/reference/lexical_analysis.html#string-literal-concatenation">
 *   https://docs.python.org/3/reference/lexical_analysis.html#string-literal-concatenation</a>
 * @see #getStringElements()
 * @see PyAstStringElement
 * @see PyAstPlainStringElement
 * @see PyAstFormattedStringElement
 */
@ApiStatus.Experimental
public interface PyAstStringLiteralExpression extends PyAstLiteralExpression, StringLiteralExpression, PsiLanguageInjectionHost {
  @Override
  default boolean isValidHost() {
    return true;
  }

  @Override
  default PsiLanguageInjectionHost updateText(@NotNull String text) {
    return ElementManipulators.handleContentChange(this, text);
  }

  @Override
  default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
    pyVisitor.visitPyStringLiteralExpression(this);
  }

  @NotNull
  default List<ASTNode> getStringNodes() {
    final TokenSet stringNodeTypes = TokenSet.orSet(PyTokenTypes.STRING_NODES, TokenSet.create(PyElementTypes.FSTRING_NODE));
    return Arrays.asList(getNode().getChildren(stringNodeTypes));
  }

  /**
   * Returns a list of implicitly concatenated string elements composing this literal expression.
   */
  @NotNull
  default List<? extends PyAstStringElement> getStringElements() {
    return StreamEx.of(getStringNodes())
      .map(ASTNode::getPsi)
      .select(PyAstStringElement.class)
      .toList();
  }

  /**
   * Returns value ranges for all nodes that form this string literal expression <i>relative to its start offset</i>.
   * Such range doesn't include neither node's prefix like "ur", nor its quotes.
   * <p>
   * For example, for the next "glued" string literal:
   * <pre>{@code
   * u"\u0066\x6F\157" ur'' '''\t'''
   * }</pre>
   * <p>
   * this method returns:
   * <p>
   * <code><pre>
   * [
   *   [2,16),
   *   [21,21),
   *   [26,28),
   * ]
   * </code></pre>
   */
  @NotNull
  default List<TextRange> getStringValueTextRanges() {
    final int elementStart = getTextRange().getStartOffset();
    return ContainerUtil.map(getStringElements(), node -> {
      final int nodeRelativeOffset = node.getTextRange().getStartOffset() - elementStart;
      return node.getContentRange().shiftRight(nodeRelativeOffset);
    });
  }

  @NotNull
  @Override
  default String getStringValue() {
    final StringBuilder out = new StringBuilder();
    for (Pair<TextRange, String> fragment : getDecodedFragments()) {
      out.append(fragment.getSecond());
    }
    return out.toString();
  }

  /**
   * Returns unescaped fragments of string's value together with their respective text ranges <i>relative to the element's start offset</i>.
   * For most escape sequences the decoded character is returned and the text range that spans the sequence itself.
   * Other "literal" fragments of the string are returned as is so that {@code pair.getFirst().length() == pair.getSecond().getLength()}.
   * <p>
   * For example, for the next "glued" string literal:
   * <p>
   * <pre>{@code
   * u"\u0066\x6F\157" '\bar' r'\baz'
   * }</pre>
   * <p>
   * this method returns:
   * <p>
   * <code><pre>
   * [
   *   ([2,8),"f"),
   *   ([8,12),"o"),
   *   ([12,16),"o"),
   *   ([16,16),""),
   *   ([19,21),"\b"),
   *   ([21,23),"ar"),
   *   ([27,29),"\\b"),
   *   ([29,31),"az"),
   * ]
   * </code></pre>
   */
  @NotNull
  default List<Pair<TextRange, String>> getDecodedFragments() {
    final int elementStart = getTextRange().getStartOffset();
    return StreamEx.of(getStringElements())
      .flatMap(node -> StreamEx.of(node.getDecodedFragments())
        .map(pair -> {
          final int nodeRelativeOffset = node.getTextRange().getStartOffset() - elementStart;
          return Pair.create(pair.getFirst().shiftRight(nodeRelativeOffset), pair.getSecond());
        }))
      .toList();
  }

  @Override
  default TextRange getStringValueTextRange() {
    List<TextRange> allRanges = getStringValueTextRanges();
    if (allRanges.size() == 1) {
      return allRanges.get(0);
    }
    if (allRanges.size() > 1) {
      return allRanges.get(0).union(allRanges.get(allRanges.size() - 1));
    }
    return new TextRange(0, getTextLength());
  }
}
