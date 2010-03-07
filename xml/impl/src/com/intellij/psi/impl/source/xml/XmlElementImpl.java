/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 26, 2002
 * Time: 6:25:08 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class XmlElementImpl extends CompositePsiElement implements XmlElement {
  public XmlElementImpl(IElementType type) {
    super(type);
  }

  public boolean processElements(PsiElementProcessor processor, PsiElement place){
    return XmlUtil.processXmlElements(this, processor, false);
  }

  public boolean processChildren(PsiElementProcessor processor){
    return XmlUtil.processXmlElementChildren(this, processor, false);
  }

  public XmlElement findElementByTokenType(final IElementType type){
    final XmlElement[] result = new XmlElement[1];
    result[0] = null;

    processElements(new PsiElementProcessor(){
      public boolean execute(PsiElement element){
        if(element instanceof TreeElement && ((ASTNode)element).getElementType() == type){
          result[0] = (XmlElement)element;
          return false;
        }
        return true;
      }
    }, this);

    return result[0];
  }

  public PsiElement getContext() {
    final XmlElement data = getUserData(INCLUDING_ELEMENT);
    if(data != null) return data;
    return super.getParent();
  }

  @NotNull
  public PsiElement getNavigationElement() {
    final PsiElement data = getUserData(ORIGINAL_ELEMENT);
    if (data != null) return data;
    if (!isPhysical()) {
      final XmlElement including = getUserData(INCLUDING_ELEMENT);
      return including != null ? including : super.getParent().getNavigationElement();
    }
    return super.getNavigationElement();
  }

  public PsiElement getParent(){
    return getContext();
  }

  @NotNull
  public Language getLanguage() {
    return getContainingFile().getLanguage();
  }

  @Nullable
  protected static String getNameFromEntityRef(final CompositeElement compositeElement, final IElementType xmlEntityDeclStart) {
    final ASTNode node = compositeElement.findChildByType(xmlEntityDeclStart);
    if (node == null) return null;
    ASTNode name = node.getTreeNext();

    if (name != null && name.getElementType() == TokenType.WHITE_SPACE) {
      name = name.getTreeNext();
    }

    if (name != null && name.getElementType() == XmlElementType.XML_ENTITY_REF) {
      final StringBuilder builder = new StringBuilder();

      ((XmlElement)name.getPsi()).processElements(new PsiElementProcessor() {
        public boolean execute(final PsiElement element) {
          builder.append(element.getText());
          return true;
        }
      }, name.getPsi());
      if (builder.length() > 0) return builder.toString();
    }
    return null;
  }

  @NotNull
  public SearchScope getUseScope() {
    return GlobalSearchScope.allScope(getProject());
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {

    if (super.isEquivalentTo(another)) return true;
    PsiElement element1 = this;
    PsiElement element2 = another;

    // TODO: seem to be only necessary for tag dirs equivalens checking.
    if (element1 instanceof XmlTag && element2 instanceof XmlTag) {
      if (!element1.isPhysical() && !element2.isPhysical()) return element1.getText().equals(element2.getText());
    }

    return false;
  }
}
