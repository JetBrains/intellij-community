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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.TypeOrElementOrAttributeReference;
import com.intellij.psi.impl.source.xml.SchemaPrefixReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public class AddSchemaPrefixIntention extends PsiElementBaseIntentionAction {
  public static final String NAME = "Insert namespace prefix";

  public AddSchemaPrefixIntention() {
    setText(NAME);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return NAME;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final XmlAttribute xmlns = getXmlnsDeclaration(element);
    if (xmlns == null) return;
    final String namespace = xmlns.getValue();
    final XmlTag tag = xmlns.getParent();

    if (tag != null) {
      final Set<String> ns = tag.getLocalNamespaceDeclarations().keySet();
      final String nsPrefix = Messages.showInputDialog(project, "Namespace Prefix:", StringUtil.capitalize(NAME), Messages.getInformationIcon(), "",
                               new InputValidator() {
                                 @Override
                                 public boolean checkInput(String inputString) {
                                   return !ns.contains(inputString);
                                 }

                                 @Override
                                 public boolean canClose(String inputString) {
                                   return checkInput(inputString);
                                 }
                               });
      if (nsPrefix == null) return;
      final List<XmlTag> tags = new ArrayList<>();
      final List<XmlAttributeValue> values = new ArrayList<>();
      new WriteCommandAction(project, NAME, tag.getContainingFile()) {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          tag.accept(new XmlRecursiveElementVisitor() {
            @Override
            public void visitXmlTag(XmlTag tag) {
              if (tag.getNamespace().equals(namespace) && tag.getNamespacePrefix().isEmpty()) {
                tags.add(tag);
              }
              super.visitXmlTag(tag);
            }

            @Override
            public void visitXmlAttributeValue(XmlAttributeValue value) {
              PsiReference ref = null;
              boolean skip = false;
              for (PsiReference reference : value.getReferences()) {
                if (reference instanceof TypeOrElementOrAttributeReference) {
                  ref = reference;
                } else if (reference instanceof SchemaPrefixReference) {
                  skip = true;
                  break;
                }
              }
              if (!skip && ref != null) {
                final PsiElement xmlElement = ref.resolve();
                if (xmlElement instanceof XmlElement) {
                  final XmlTag tag = PsiTreeUtil.getParentOfType(xmlElement, XmlTag.class, false);
                  if (tag != null) {
                    if (tag.getNamespace().equals(namespace)) {
                      if (ref.getRangeInElement().getLength() == value.getValue().length()) { //no ns prefix
                        values.add(value);
                      }
                    }
                  }
                }
              }
            }
          });
          for (XmlAttributeValue value : values) {
            ((XmlAttribute)value.getParent()).setValue(nsPrefix + ":" + value.getValue());
          }
          for (XmlTag xmlTag : tags) {
            xmlTag.setName(nsPrefix + ":" + xmlTag.getLocalName());
          }
          xmlns.setName("xmlns:" + nsPrefix);
        }
      }.execute();
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return getXmlnsDeclaration(element) != null;
  }

  @Nullable
  private static XmlAttribute getXmlnsDeclaration(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof XmlTag) {
      XmlTag tag = (XmlTag)parent;
      if (tag.getNamespacePrefix().isEmpty()) {
        while (tag != null) {
          final XmlAttribute attr = tag.getAttribute("xmlns");
          if (attr != null) return attr;
          tag = tag.getParentTag();
        }
      }
    } else if (parent instanceof XmlAttribute && ((XmlAttribute)parent).getName().equals("xmlns")) {
      return (XmlAttribute)parent;
    }
    return null;
  }
}
