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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.util.IncorrectOperationException;
import org.intellij.plugins.relaxNG.RelaxngBundle;
import org.intellij.plugins.relaxNG.compact.RncTokenTypes;
import org.intellij.plugins.relaxNG.compact.psi.*;
import org.intellij.plugins.relaxNG.compact.psi.util.EscapeUtil;
import org.intellij.plugins.relaxNG.compact.psi.util.RenameUtil;
import org.intellij.plugins.relaxNG.model.Define;
import org.intellij.plugins.relaxNG.model.resolve.DefinitionResolver;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;
import java.util.Set;

public class RncDefineImpl extends RncElementImpl implements RncDefine, PsiMetaOwner {
  public RncDefineImpl(ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull RncElementVisitor visitor) {
    visitor.visitDefine(this);
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visitDefine(this);
  }

  @Override
  public String getName() {
    final ASTNode node = getNameNode();
    return EscapeUtil.unescapeText(node);
  }

  @Override
  public PsiElement getNameElement() {
    return getNameNode().getPsi();
  }

  public @NotNull ASTNode getNameNode() {
    final ASTNode node = getNode().findChildByType(RncTokenTypes.IDENTIFIERS);
    assert node != null;
    return node;
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    final ASTNode node = getNameNode();
    node.getTreeParent().replaceChild(node, RenameUtil.createIdentifierNode(getManager(), name));
    return this;
  }

  @Override
  public @Nullable RncPattern getPattern() {
    return findChildByClass(RncPattern.class);
  }

  @Override
  public PsiReference getReference() {
    if (getParent() instanceof RncInclude) {
      final TextRange range = TextRange.from(0, getNameNode().getTextLength());
      return new PsiReferenceBase<RncDefine>(this, range, true) {
        @Override
        public PsiElement resolve() {
          return RncDefineImpl.this;
        }

        @Override
        public Object @NotNull [] getVariants() {
          final RncInclude parent = (RncInclude)getParent();
          final RncFile referencedFile = parent.getReferencedFile();
          if (referencedFile == null) {
            return EMPTY_ARRAY;
          }
          final RncGrammar grammar = referencedFile.getGrammar();
          if (grammar == null) {
            return EMPTY_ARRAY;
          }

          final Map<String, Set<Define>> map = DefinitionResolver.getAllVariants(grammar);
          if (map != null) {
            return map.keySet().toArray();
          }
          return EMPTY_ARRAY;
        }
      };
    }
    return super.getReference();
  }

  @Override
  public @Nullable Icon getIcon(int flags) {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.Property);
  }

  public boolean isMetaEnough() {
    return true;
  }

  @Override
  public @Nullable PsiMetaData getMetaData() {
    return new MyMetaData();
  }

  private class MyMetaData implements PsiMetaData, PsiPresentableMetaData {
    /*public boolean processDeclarations(PsiElement context, PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastElement, PsiElement place) {
      return false;
    }*/

    @Override
    public @Nullable Icon getIcon() {
      return RncDefineImpl.this.getIcon(0);
    }

    @Override
    public String getTypeName() {
      return RelaxngBundle.message("relaxng.symbol.pattern-definition");
    }

    @Override
    public PsiElement getDeclaration() {
      return RncDefineImpl.this;
    }

    @Override
    public @NonNls String getName(PsiElement context) {
      return RncDefineImpl.this.getName();
    }

    @Override
    public @NonNls String getName() {
      return RncDefineImpl.this.getName();
    }

    @Override
    public void init(PsiElement element) {
    }
  }
}
