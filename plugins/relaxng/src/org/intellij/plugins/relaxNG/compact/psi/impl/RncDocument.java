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

import org.intellij.plugins.relaxNG.compact.psi.RncElementVisitor;
import org.intellij.plugins.relaxNG.compact.psi.RncGrammar;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlProlog;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RncDocument extends RncElementImpl implements XmlDocument {
  public RncDocument(ASTNode node) {
    super(node);
  }

  public XmlNSDescriptor getDefaultNSDescriptor(String namespace, boolean strict) {
    return null;
  }

  public XmlProlog getProlog() {
    return null;
  }

  @Nullable
  public XmlTag getRootTag() {
    return null;
  }

  public XmlNSDescriptor getRootTagNSDescriptor() {
    return null;
  }

  public boolean processElements(PsiElementProcessor processor, PsiElement place) {
    return false;
  }

  @Nullable
  public PsiMetaData getMetaData() {
    return MetaRegistry.getMeta(this);
  }

  public boolean isMetaEnough() {
    return true;
  }

  public RncGrammar getGrammar() {
    return findChildByClass(RncGrammar.class);
  }

  @Override
  @NotNull
  protected <T> T[] findChildrenByClass(Class<T> aClass) {
    return super.findChildrenByClass(aClass);
  }

  public void accept(@NotNull RncElementVisitor visitor) {
    visitor.visitElement(this);
  }
}
