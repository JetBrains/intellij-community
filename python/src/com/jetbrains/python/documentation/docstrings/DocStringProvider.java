/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.documentation.docstrings;

import com.google.common.base.Preconditions;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public abstract class DocStringProvider<T extends StructuredDocString> {
  public abstract T parseDocString(@NotNull Substring content);

  @NotNull
  public T parseDocString(@NotNull PyStringLiteralExpression literalExpression) {
    return parseDocString(literalExpression.getStringNodes().get(0));
  }

  @NotNull
  public T parseDocString(@NotNull ASTNode node) {
    Preconditions.checkArgument(node.getElementType() == PyTokenTypes.DOCSTRING);
    return parseDocString(node.getText());
  }


  public T parseDocString(@NotNull String stringText) {
    return parseDocString(stripSuffixAndQuotes(stringText));
  }

  public T parseDocStringContent(@NotNull String stringContent) {
    return parseDocString(new Substring(stringContent));
  }

  @NotNull
  private static Substring stripSuffixAndQuotes(@NotNull String text) {
    final TextRange contentRange = PyStringLiteralExpressionImpl.getNodeTextRange(text);
    return new Substring(text, contentRange.getStartOffset(), contentRange.getEndOffset());
  }

  @NotNull
  public abstract DocStringUpdater updateDocString(@NotNull T docstring);

  @NotNull
  public abstract DocStringBuilder createDocString();

}
