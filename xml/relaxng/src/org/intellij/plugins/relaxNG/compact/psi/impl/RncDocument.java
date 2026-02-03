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
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlProlog;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlNSDescriptor;
import org.intellij.plugins.relaxNG.compact.psi.RncElementVisitor;
import org.intellij.plugins.relaxNG.compact.psi.RncGrammar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RncDocument extends RncElementImpl implements XmlDocument {
  public RncDocument(ASTNode node) {
    super(node);
  }

  @Override
  public XmlNSDescriptor getDefaultNSDescriptor(String namespace, boolean strict) {
    return null;
  }

  @Override
  public XmlProlog getProlog() {
    return null;
  }

  @Override
  public @Nullable XmlTag getRootTag() {
    return null;
  }

  @Override
  public XmlNSDescriptor getRootTagNSDescriptor() {
    return null;
  }

  @Override
  public boolean processElements(PsiElementProcessor processor, PsiElement place) {
    return false;
  }

  @Override
  public @Nullable PsiMetaData getMetaData() {
    return MetaRegistry.getMeta(this);
  }

  public boolean isMetaEnough() {
    return true;
  }

  public RncGrammar getGrammar() {
    return findChildByClass(RncGrammar.class);
  }

  @Override
  protected <T> T @NotNull [] findChildrenByClass(Class<T> aClass) {
    return super.findChildrenByClass(aClass);
  }

  @Override
  public void accept(@NotNull RncElementVisitor visitor) {
    visitor.visitElement(this);
  }
}
