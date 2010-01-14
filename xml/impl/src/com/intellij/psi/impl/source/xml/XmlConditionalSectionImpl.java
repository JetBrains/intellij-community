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
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlConditionalSection;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlEntityDecl;
import com.intellij.psi.xml.XmlEntityRef;

/**
 * @author maxim.mossienko
 */
public class XmlConditionalSectionImpl extends XmlElementImpl implements XmlConditionalSection, XmlElementType {
  public XmlConditionalSectionImpl() {
    super(XML_CONDITIONAL_SECTION);
  }

  public boolean isIncluded(PsiFile targetFile) {
    ASTNode child = findChildByType(XML_CONDITIONAL_SECTION_START);

    if (child != null) {
      child = child.getTreeNext();

      if (child != null && child.getElementType() == TokenType.WHITE_SPACE) {
        child = child.getTreeNext();
      }

      if (child != null) {
        IElementType elementType = child.getElementType();
        if (elementType == XML_CONDITIONAL_INCLUDE) return true;
        if (elementType == XML_CONDITIONAL_IGNORE) return false;

        if (elementType == XML_ENTITY_REF) {
          XmlEntityRef xmlEntityRef = (XmlEntityRef)child.getPsi();

          final String text = xmlEntityRef.getText();
          String name = text.substring(1,text.length() - 1);

          PsiElement psiElement = targetFile != null ? XmlEntityRefImpl.getCachedEntity( targetFile, name): null;

          if (psiElement instanceof XmlEntityDecl) {
            final XmlEntityDecl decl = (XmlEntityDecl)psiElement;
            
            if(decl.isInternalReference()) {
              for (ASTNode e = decl.getNode().getFirstChildNode(); e != null; e = e.getTreeNext()) {
                if (e.getElementType() == XmlElementType.XML_ATTRIBUTE_VALUE) {
                  final boolean b = StringUtil.stripQuotesAroundValue(e.getText()).equals("INCLUDE");
                  //if (!b) System.out.println("Skipped:"+text);
                  return b;
                }
              }
            }
          }
        }
        ///System.out.println("Skipped:"+child.getText());
      }
    }
    return false;
  }

  public PsiElement getBodyStart() {
    ASTNode child = findChildByType(XML_MARKUP_START);
    if (child != null) child = child.getTreeNext();
    if (child != null) return child.getPsi();
    return null;
  }
}
