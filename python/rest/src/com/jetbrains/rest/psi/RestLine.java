/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.rest.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.LeafElement;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */
public class RestLine extends RestElement implements PsiLanguageInjectionHost {
  public RestLine(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "RestLine:" + getNode().getElementType().toString();
  }


  @Override
  public boolean isValidHost() {
    return true;
  }

  @Override
  public PsiLanguageInjectionHost updateText(@NotNull String text) {
    ASTNode valueNode = getNode().getFirstChildNode();
    assert valueNode instanceof LeafElement;
    ((LeafElement)valueNode).replaceWithText(text);
    return this;
  }

  @NotNull
  @Override
  public LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
    return new LiteralTextEscaper<PsiLanguageInjectionHost>(this) {
      @Override
      public boolean decode(@NotNull TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
        outChars.append(rangeInsideHost.substring(myHost.getText()));
        return true;
      }

      @Override
      public int getOffsetInHost(int offsetInDecoded, @NotNull TextRange rangeInsideHost) {
        int offset = offsetInDecoded + rangeInsideHost.getStartOffset();
        if (offset < rangeInsideHost.getStartOffset()) offset = rangeInsideHost.getStartOffset();
        if (offset > rangeInsideHost.getEndOffset()) offset = rangeInsideHost.getEndOffset();
        return offset;
      }

      @Override
      public boolean isOneLine() {
        return false;
      }
    };
  }
}
