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
import com.intellij.psi.PsiReference;
import org.intellij.plugins.relaxNG.compact.RncTokenTypes;
import org.intellij.plugins.relaxNG.compact.psi.RncDefine;
import org.intellij.plugins.relaxNG.compact.psi.RncElementVisitor;
import org.intellij.plugins.relaxNG.compact.psi.RncRef;
import org.intellij.plugins.relaxNG.compact.psi.util.EscapeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 11.08.2007
 */
public class RncRefImpl extends RncElementImpl implements RncRef {
  public RncRefImpl(ASTNode node) {
    super(node);
  }

  @Nullable
  public RncDefine getPattern() {
    final PsiReference ref = getReference();
    // TODO: honor combine & return virtual element if multiResolve().length > 0
    return ref instanceof PatternReference ? (RncDefine)ref.resolve() : null;
  }

  public String getReferencedName() {
    final ASTNode node = findNameNode();
    assert node != null;
    return EscapeUtil.unescapeText(node);
  }

  protected ASTNode findNameNode() {
    return getNode().findChildByType(RncTokenTypes.IDENTIFIERS);
  }

  @Override
  public void accept(@NotNull RncElementVisitor visitor) {
    visitor.visitRef(this);
  }

  public void accept(Visitor visitor) {
    visitor.visitRef(this);
  }

  @Override
  public PsiReference getReference() {
    return new PatternReference(this);
  }
}
