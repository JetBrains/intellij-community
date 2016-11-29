/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
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
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ConvertSchemaPrefixToDefaultIntention extends PsiElementBaseIntentionAction {
  public static final String NAME = "Reset to default namespace";

  public ConvertSchemaPrefixToDefaultIntention() {
    setText(NAME);
  }

  @Override
  public void invoke(@NotNull final Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
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
    final ArrayList<XmlTag> tags = new ArrayList<>();
    final ArrayList<XmlAttribute> attrs = new ArrayList<>();
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

    if (!FileModificationService.getInstance().preparePsiElementsForWrite(xmlns.getContainingFile())) return;

    CommandProcessor.getInstance().executeCommand(project, () -> {
      convertTagsAndAttributes(ns, tags, attrs, project);
      ApplicationManager.getApplication().runWriteAction(() -> {
        xmlns.setName("xmlns");
      });
    }, NAME, null);

    new WriteCommandAction(project, NAME, xmlns.getContainingFile()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        xmlns.setName("xmlns");
      }
    }.execute();
  }

  private static void convertTagsAndAttributes(String ns, final List<XmlTag> tags, final List<XmlAttribute> attrs, Project project) {
    final int localNameIndex = ns.length() + 1;
    final int totalCount = tags.size() + attrs.size();

    final SequentialModalProgressTask progressTask = new SequentialModalProgressTask(project, "Changing to default namespace", true);
    progressTask.setTask(new SequentialTask() {
      int tagIndex = 0;
      int attrIndex = 0;

      @Override
      public void prepare() {
      }

      @Override
      public boolean isDone() {
        return tagIndex + attrIndex >= totalCount;
      }

      @Override
      public boolean iteration() {
        progressTask.getIndicator().setFraction(((double) (tagIndex + attrIndex)) / totalCount);
        ApplicationManager.getApplication().runWriteAction(() -> {
          if (tagIndex < tags.size()) {
            XmlTag tag = tags.get(tagIndex++);
            final String s = tag.getName().substring(localNameIndex);
            if (!s.isEmpty()) {
              tag.setName(s);
            }
          }
          else if (attrIndex < attrs.size()) {
            XmlAttribute attr = attrs.get(attrIndex++);
            //noinspection ConstantConditions
            attr.setValue(attr.getValue().substring(localNameIndex));
          }
        });

        return isDone();
      }

      @Override
      public void stop() {

      }
    });
    ProgressManager.getInstance().run(progressTask);
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

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
