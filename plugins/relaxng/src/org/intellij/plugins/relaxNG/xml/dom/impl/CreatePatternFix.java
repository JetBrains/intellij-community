/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.xml.dom.impl;

import org.intellij.plugins.relaxNG.ProjectLoader;
import org.intellij.plugins.relaxNG.xml.dom.RngGrammar;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 17.07.2007
 */
// XXX: the tests rely on this still being an intention action
class CreatePatternFix implements IntentionAction, LocalQuickFix {
  private final PsiReference myReference;

  public CreatePatternFix(PsiReference reference) {
    myReference = reference;
  }

  @NotNull
  public String getText() {
    return "Create Pattern '" + myReference.getCanonicalText() + "'";
  }

  @NotNull
  public String getFamilyName() {
    return "Create Pattern";
  }

  @NotNull
  public String getName() {
    return getText();
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    if (!isAvailable()) {
      return;
    }
    try {
      doFix();
    } catch (IncorrectOperationException e) {
      Logger.getInstance(getClass().getName()).error(e);
    }
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return isAvailable();
  }

  private boolean isAvailable() {
    if (!(myReference instanceof DefinitionReference) || !myReference.getElement().isValid()) {
      return false;
    } else {
      final RngGrammar grammar = ((DefinitionReference)myReference).getScope();
      if (grammar == null) {
        return false;
      } else if (grammar.getXmlTag() == null) {
        return false;
      }
      return true;
    }
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    doFix();
  }

  private void doFix() throws IncorrectOperationException {
    final XmlTag tag = PsiTreeUtil.getParentOfType(myReference.getElement(), XmlTag.class);
    assert tag != null;
    final XmlTag defineTag = tag.createChildTag("define", ProjectLoader.RNG_NAMESPACE, "\n \n", false);
    defineTag.setAttribute("name", myReference.getCanonicalText());

    final RngGrammar grammar = ((DefinitionReference)myReference).getScope();
    if (grammar == null) return;
    final XmlTag root = grammar.getXmlTag();
    if (root == null) return;

    final XmlTag[] tags = root.getSubTags();
    for (XmlTag xmlTag : tags) {
      if (PsiTreeUtil.isAncestor(xmlTag, tag, false)) {
        final XmlElementFactory ef = XmlElementFactory.getInstance(tag.getProject());
        final XmlText text = ef.createDisplayText(" ");
        final PsiElement e = root.addAfter(text, xmlTag);

        root.addAfter(defineTag, e);
        return;
      }
    }
    root.add(defineTag);
  }

  public boolean startInWriteAction() {
    return true;
  }

  public static XmlTag getAncestorTag(XmlTag tag, String name, String namespace) {
    if (tag == null) {
      return null;
    }
    if (tag.getLocalName().equals(name) && tag.getNamespace().equals(namespace)) {
      return tag;
    }
    return getAncestorTag(tag.getParentTag(), name, namespace);
  }
}
