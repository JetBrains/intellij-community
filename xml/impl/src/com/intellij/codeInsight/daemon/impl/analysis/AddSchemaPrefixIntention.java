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
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.SchemaReferencesProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public class AddSchemaPrefixIntention extends PsiElementBaseIntentionAction {
  public AddSchemaPrefixIntention() {
    setText("Insert Namespace Prefix");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getClass().getName();
  }

  @Override
  public void invoke(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
    final XmlAttribute xmlns = (XmlAttribute)element.getParent();
    final String namespace = xmlns.getValue();
    final XmlTag tag = xmlns.getParent();

    if (tag != null) {
      final Set<String> ns = tag.getLocalNamespaceDeclarations().keySet();
      final String nsPrefix = Messages.showInputDialog(project, "Write new namespace prefix:", "Insert Namespace Prefix", Messages.getInformationIcon(), "",
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
      final List<XmlTag> tags = new ArrayList<XmlTag>();
      final List<XmlAttributeValue> values = new ArrayList<XmlAttributeValue>();
      new WriteCommandAction(project, "Introduce Namespace Prefix", tag.getContainingFile()) {
        @Override
        protected void run(Result result) throws Throwable {
          tag.accept(new XmlRecursiveElementVisitor() {
            @Override
            public void visitXmlTag(XmlTag tag) {
              if (namespace.equals(tag.getNamespace()) && tag.getNamespacePrefix().length() == 0) {
                tags.add(tag);
              }
              super.visitXmlTag(tag);
            }

            @Override
            public void visitXmlAttributeValue(XmlAttributeValue value) {
              for (PsiReference reference : value.getReferences()) {
                if (reference instanceof SchemaReferencesProvider.TypeOrElementOrAttributeReference) {
                  final PsiElement tag = reference.resolve();
                  if (tag instanceof XmlTag && namespace.equals(((XmlTag)tag).getNamespace())) {
                    if (reference.getRangeInElement().getLength() == value.getValue().length()) { //no ns prefix
                      values.add(value);
                    }
                  }
                }
              }
            }
          });
          xmlns.setName("xmlns:" + nsPrefix);
          for (XmlTag xmlTag : tags) {
            xmlTag.setName(nsPrefix + ":" + xmlTag.getLocalName());
          }
          for (XmlAttributeValue value : values) {
            ((XmlAttribute)value.getParent()).setValue(nsPrefix + ":" + value.getValue());
          }
        }
      }.execute();
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final PsiElement parent = element.getParent();
    return parent instanceof XmlAttribute && "xmlns".equals(((XmlAttribute)parent).getName());
  }
}
