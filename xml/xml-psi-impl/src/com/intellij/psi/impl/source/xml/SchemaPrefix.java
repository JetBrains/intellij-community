/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.RenameableFakePsiElement;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.PlatformIcons;
import com.intellij.xml.XmlExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class SchemaPrefix extends RenameableFakePsiElement {
  private final TextRange myRange;
  private final String myName;

  public SchemaPrefix(final XmlAttribute parent, TextRange range, String name) {
    super(parent);
    myRange = range;
    myName = name;
  }

  public static SchemaPrefix createJspPrefix(XmlAttributeValue element, String prefix) {
    TextRange range = ElementManipulators.getValueTextRange(element).shiftRight(element.getStartOffsetInParent());
    return new SchemaPrefix((XmlAttribute)element.getParent(), range, prefix) {
      @Override
      protected String getNamespace() {
        return ((XmlAttribute)getParent()).getParent().getAttributeValue("uri");
      }
    };
  }

  @Override
  public String getTypeName() {
    return "XML Namespace Prefix";
  }

  @Override
  public Icon getIcon() {
    return PlatformIcons.VARIABLE_ICON;
  }

  @Override
  public int getTextOffset() {
    return getParent().getTextRange().getStartOffset() + myRange.getStartOffset();
  }

  @Override
  public int getTextLength() {
    return myName.length();
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public XmlAttribute getDeclaration() {
    return (XmlAttribute)getParent();
  }

  @Override
  public TextRange getTextRange() {
    return TextRange.from(getTextOffset(), getTextLength());
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    return XmlExtension.getExtension(getContainingFile()).getNsPrefixScope(getDeclaration());
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return another instanceof SchemaPrefix && ((SchemaPrefix)another).getDeclaration() == getDeclaration();
  }

  public String getQuickNavigateInfo() {
    String ns = getNamespace();
    StringBuilder builder = new StringBuilder().append(getTypeName()).append(" \"").append(getName()).append("\"");
    if (ns != null) {
      builder.append(" (").append(ns).append(")");
    }
    return builder.toString();
  }

  @Nullable
  protected String getNamespace() {
    XmlAttribute parent = (XmlAttribute)getParent();
    return parent == null ? null : parent.getValue();
  }
}
