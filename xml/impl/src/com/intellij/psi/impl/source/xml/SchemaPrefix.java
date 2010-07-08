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

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.RenameableFakePsiElement;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

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

  public String getTypeName() {
    return "XML Namespace Prefix";
  }

  public Icon getIcon() {
    return Icons.VARIABLE_ICON;
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
    XmlAttribute declaration = getDeclaration();
    return new LocalSearchScope(declaration.isNamespaceDeclaration() ? declaration.getParent() : ((XmlFile)declaration.getContainingFile()).getDocument());
  }
}
