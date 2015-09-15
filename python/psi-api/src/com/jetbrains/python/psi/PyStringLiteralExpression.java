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
  List<TextRange> getStringValueTextRanges();

  List<ASTNode> getStringNodes();

  int valueOffsetToTextOffset(int valueOffset);

  @NotNull
  List<Pair<TextRange, String>> getDecodedFragments();

  /**
   * @return true if this element has single string node and its type is {@link com.jetbrains.python.PyTokenTypes#DOCSTRING}
   */
  boolean isDocString();
}
