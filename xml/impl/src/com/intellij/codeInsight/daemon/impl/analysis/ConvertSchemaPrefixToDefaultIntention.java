/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.impl.source.xml.SchemaPrefixReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author Konstantin Bulenkov
 */
public class ConvertSchemaPrefixToDefaultIntention extends PsiElementBaseIntentionAction {
  public static final String NAME = "Set Namespace Prefix to Empty";

  public ConvertSchemaPrefixToDefaultIntention() {
    setText(NAME);
  }

  @Override
  public void invoke(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
    final XmlAttribute xmlns = getXmlnsDeclaration(element);
    if (xmlns == null) return;
    SchemaPrefixReference prefixRef = null;
    for (PsiReference ref : xmlns.getReferences()) {
      if (ref instanceof SchemaPrefixReference) {
        prefixRef = (SchemaPrefixReference)ref;
        break;
      }
    }
    if (prefixRef == null) return;

    final SchemaPrefix prefix = prefixRef.resolve();
    final String ns = prefixRef.getNamespacePrefix();
    final ArrayList<XmlTag> tags = new ArrayList<XmlTag>();
    final ArrayList<XmlAttribute> attrs = new ArrayList<XmlAttribute>();
    xmlns.getParent().accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlTag(XmlTag tag) {
        if (ns.equals(tag.getNamespacePrefix())) {
          tags.add(tag);
        }
        super.visitXmlTag(tag);
      }

      @Override
      public void visitXmlAttributeValue(XmlAttributeValue value) {
        if (value.getValue().startsWith(ns + ":")) {
          for (PsiReference ref : value.getReferences()) {
            if (ref instanceof SchemaPrefixReference && ref.isReferenceTo(prefix)) {
              attrs.add((XmlAttribute)value.getParent());
            }
          }
        }
      }
    });
    new WriteCommandAction(project, "Convert namespace prefix to default", xmlns.getContainingFile()) {
      @Override
      protected void run(Result result) throws Throwable {
        final int index = ns.length() + 1;
        for (XmlTag tag : tags) {
          tag.setName(tag.getName().substring(index));
        }
        for (XmlAttribute attr : attrs) {
          attr.setValue(attr.getValue().substring(index));
        }
        xmlns.setName("xmlns");
      }
    }.execute();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return getXmlnsDeclaration(element) != null;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return NAME;
  }

  @Nullable
  private static XmlAttribute getXmlnsDeclaration(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (parent == null) return null;
    for (PsiReference ref : parent.getReferences()) {
      if (ref instanceof SchemaPrefixReference) {
        final PsiElement elem = ref.resolve();
        if (elem != null) {
          final PsiElement attr = elem.getParent();
          if (attr instanceof XmlAttribute) {
            final PsiElement tag = attr.getParent();
            if (tag instanceof XmlTag && ((XmlTag)tag).getAttribute("xmlns") == null) {
              return (XmlAttribute)attr;
            }
          }
        }
      }
    }
    return null;
  }
}
