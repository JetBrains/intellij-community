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
import com.intellij.psi.tree.IElementType;
import org.intellij.plugins.relaxNG.compact.psi.RncAnnotation;
import org.intellij.plugins.relaxNG.compact.psi.RncElementVisitor;
import org.intellij.plugins.relaxNG.compact.psi.RncName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RncAnnotationImpl extends RncElementImpl implements RncAnnotation {
  public RncAnnotationImpl(ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull RncElementVisitor visitor) {
    visitor.visitAnnotation(this);
  }

  @NotNull
  @Override
  public IElementType getTokenType() {
    return getNode().getElementType();
  }

  @Override
  @Nullable
  public RncName getNameElement() {
    return findChildByClass(RncName.class);
  }
}