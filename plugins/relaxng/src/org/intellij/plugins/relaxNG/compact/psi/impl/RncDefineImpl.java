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
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
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

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 13.08.2007
 */
public class RncDefineImpl extends RncElementImpl implements RncDefine, PsiMetaOwner {
  public RncDefineImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RncElementVisitor visitor) {
    visitor.visitDefine(this);
  }

  public void accept(Visitor visitor) {
    visitor.visitDefine(this);
  }

  @Override
  public String getName() {
    final ASTNode node = getNameNode();
    return EscapeUtil.unescapeText(node);
  }

  public PsiElement getNameElement() {
    return getNameNode().getPsi();
  }

  @NotNull
  public ASTNode getNameNode() {
    final ASTNode node = getNode().findChildByType(RncTokenTypes.IDENTIFIERS);
    assert node != null;
    return node;
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    final ASTNode node = getNameNode();
    node.getTreeParent().replaceChild(node, RenameUtil.createIdentifierNode(getManager(), name));
    return this;
  }

  @Nullable
  public RncPattern getPattern() {
    return findChildByClass(RncPattern.class);
  }

  public PsiReference getReference() {
    if (getParent() instanceof RncInclude) {
      final TextRange range = TextRange.from(0, getNameNode().getTextLength());
      return new PsiReferenceBase<RncDefine>(this, range, true) {
        public PsiElement resolve() {
          return RncDefineImpl.this;
        }

        @NotNull
        public Object[] getVariants() {
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

  @Nullable
  public Icon getIcon(int flags) {
    return IconLoader.findIcon("/nodes/property.png");
  }

  public boolean isMetaEnough() {
    return true;
  }

  @Nullable
  public PsiMetaData getMetaData() {
    return new MyMetaData();
  }

  private class MyMetaData implements PsiMetaData, PsiPresentableMetaData {
    /*public boolean processDeclarations(PsiElement context, PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastElement, PsiElement place) {
      return false;
    }*/

    @Nullable
    public Icon getIcon() {
      return RncDefineImpl.this.getIcon(Iconable.ICON_FLAG_CLOSED);
    }

    public String getTypeName() {
      return "Pattern Definition";
    }

    public PsiElement getDeclaration() {
      return RncDefineImpl.this;
    }

    @NonNls
    public String getName(PsiElement context) {
      return RncDefineImpl.this.getName();
    }

    @NonNls
    public String getName() {
      return RncDefineImpl.this.getName();
    }

    public void init(PsiElement element) {
    }

    public Object[] getDependences() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
  }
}
