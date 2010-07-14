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
import org.intellij.plugins.relaxNG.compact.RncTokenTypes;
import org.intellij.plugins.relaxNG.compact.psi.RncDecl;
import org.intellij.plugins.relaxNG.compact.psi.RncElementVisitor;
import org.intellij.plugins.relaxNG.compact.psi.util.EscapeUtil;
import org.intellij.plugins.relaxNG.compact.psi.util.RenameUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 16.08.2007
 */
public class RncDeclImpl extends RncElementImpl implements RncDecl {
  public RncDeclImpl(ASTNode node) {
    super(node);
  }

  public String getPrefix() {
    final ASTNode ns = findIdentifierNode();
    return ns != null ? EscapeUtil.unescapeText(ns) : null;
  }

  public String getDeclaredNamespace() {
    final ASTNode ns = getNode().findChildByType(RncTokenTypes.LITERAL);
    return ns != null ? EscapeUtil.parseLiteralValue(ns) : null;
  }

  @Override
  public int getTextOffset() {
    final ASTNode ns = findIdentifierNode();
    if (ns != null) {
      return ns.getStartOffset();
    }
    return super.getTextOffset();
  }

  private ASTNode findIdentifierNode() {
    // namespace text = "..." is valid - "text" is parsed as a keyword though
    final ASTNode node = getNode().findChildByType(RncTokenTypes.IDENTIFIERS);
    if (node == null) {
      final ASTNode[] nodes = getNode().getChildren(RncTokenTypes.KEYWORDS);
      if (nodes.length > 1) {
        return nodes[1];
      }
    }
    return node;
  }

  @Override
  public String getName() {
    final String s = getPrefix();
    return s != null ? s : "";
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    final ASTNode node = findIdentifierNode();
    if (node == null) return this;
    node.getTreeParent().replaceChild(node, RenameUtil.createIdentifierNode(getManager(), name));
    return this;
  }

  public void accept(@NotNull RncElementVisitor visitor) {
    visitor.visitElement(this);
  }
}
