// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.RenameableFakePsiElement;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.psi.XmlPsiBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class SchemaPrefix extends RenameableFakePsiElement {
  private final TextRange myRange;
  private final String myName;

  public SchemaPrefix(XmlAttribute parent, TextRange range, String name) {
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
    return XmlPsiBundle.message("xml.namespace.prefix");
  }

  @Override
  public Icon getIcon() {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.Variable);
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

  @NlsSafe
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
