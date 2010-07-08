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

import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlExtension;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class SchemaPrefixReference extends PsiReferenceBase<XmlElement> {

  private final NullableLazyValue<SchemaPrefix> myPrefix = new NullableLazyValue<SchemaPrefix>() {
    @Override
    protected SchemaPrefix compute() {
      if (myElement instanceof XmlAttribute && (((XmlAttribute)myElement).isNamespaceDeclaration())) {
        return new SchemaPrefix((XmlAttribute)myElement, getRangeInElement(), myName);
      }
      else if (myElement instanceof XmlAttributeValue &&
               ((XmlAttribute)((XmlAttributeValue)myElement).getParent()).getLocalName().equals("prefix")) {
        return new SchemaPrefix((XmlAttribute)myElement.getParent(), getRangeInElement().shiftRight(myElement.getStartOffsetInParent()), myName) {
          @NotNull
          @Override
          public SearchScope getUseScope() {
            return new LocalSearchScope(((XmlFile)myElement.getContainingFile()).getDocument());
          }
        };
      }
      else {
        XmlExtension extension = XmlExtension.getExtension(myElement.getContainingFile());
        final XmlAttribute declaration = extension.getPrefixDeclaration(PsiTreeUtil.getParentOfType(myElement, XmlTag.class, false), myName);
        if (declaration != null) {
          final String prefix = declaration.getNamespacePrefix();
          final TextRange textRange = TextRange.from(prefix.length() + 1, myName.length());
          return new SchemaPrefix(declaration, textRange, myName);
        }
      }
      return null;
    }
  };

  private final String myName;

  public SchemaPrefixReference(XmlElement element, TextRange range, String name) {
    super(element, range, true);
    myName = name;
  }

  public String getNamespacePrefix() {
    return myName;
  }

  public SchemaPrefix resolve() {
    return myPrefix.getValue();
  }

  @NotNull
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof SchemaPrefix) || !myName.equals(((SchemaPrefix)element).getName())) return false;

    SchemaPrefix prefix = resolve();
    return prefix != null && ((SchemaPrefix)element).getDeclaration() == prefix.getDeclaration();
  }

  @Override
  public PsiElement handleElementRename(String name) throws IncorrectOperationException {
    if (myElement instanceof XmlAttribute) {
      final XmlAttribute attr = (XmlAttribute)myElement;
      return ("xmlns".equals(attr.getNamespacePrefix()))
             ? attr.setName(attr.getNamespacePrefix() + ":" + name)
             : attr.setName(name + ":" + attr.getLocalName());
    }
    else if (myElement instanceof XmlTag) {
      final XmlTag tag = (XmlTag)myElement;
      return tag.setName(name + ":" + tag.getLocalName());
    }
    return super.handleElementRename(name);
  }
}
