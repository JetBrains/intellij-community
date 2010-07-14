/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.compact.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.intellij.plugins.relaxNG.compact.RncElementTypes;
import org.intellij.plugins.relaxNG.compact.RncTokenTypes;
import org.intellij.plugins.relaxNG.compact.psi.RncElementVisitor;
import org.intellij.plugins.relaxNG.compact.psi.RncGrammar;
import org.intellij.plugins.relaxNG.compact.psi.RncPattern;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 13.08.2007
 */
public class RncGrammarImpl extends RncElementImpl implements RncGrammar {
  public RncGrammarImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RncElementVisitor visitor) {
    visitor.visitGrammar(this);
  }

  public void accept(Visitor visitor) {
    visitor.visitGrammar(this);
  }

  public RncPattern getStart() {
    final ASTNode node = getNode().findChildByType(RncElementTypes.START);
    return node != null ? (RncPattern)node.getPsi() : null;
  }

  @Override
  public PsiElement add(@NotNull PsiElement psiElement) throws IncorrectOperationException {
    final PsiElement rbrace = findChildByType(RncTokenTypes.RBRACE);
    // TODO: fix block psi
    if (rbrace != null) {
      return addBefore(psiElement, rbrace);
    }
    return super.add(psiElement);
  }
}
