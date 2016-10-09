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
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface PyStringLiteralExpression extends PyLiteralExpression, StringLiteralExpression, PsiLanguageInjectionHost {
  @NotNull
  List<ASTNode> getStringNodes();

  int valueOffsetToTextOffset(int valueOffset);

  /**
   * Returns unescaped fragments of string's value together with their respective text ranges <i>relative to the element's start offset</i>.
   * For most escape sequences the decoded character is returned and the text range that spans the sequence itself.
   * Other "literal" fragments of the string are returned as is so that {@code pair.getFirst().length() == pair.getSecond().getLength()}.
   * <p>
   * For example, for the next "glued" string literal:
   * <p>
   * <pre><code>
   * u"\u0066\x6F\157" '\bar' r'\baz'
   * </code></pre>
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
  List<Pair<TextRange, String>> getDecodedFragments();

  /**
   * Returns value ranges for all nodes that form this string literal expression <i>relative to its start offset</i>.
   * Such range doesn't include neither node's prefix like "ur", nor its quotes.
   * <p>
   * For example, for the next "glued" string literal:
   * <pre><code>
   * u"\u0066\x6F\157" ur'' '''\t'''
   * </code></pre>
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
  List<TextRange> getStringValueTextRanges();
  
  /**
   * @return true if this element has single string node and its type is {@link com.jetbrains.python.PyTokenTypes#DOCSTRING}
   */
  boolean isDocString();
}
