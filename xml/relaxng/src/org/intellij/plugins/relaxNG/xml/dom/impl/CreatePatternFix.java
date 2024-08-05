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

import com.intellij.codeInsight.intention.FileModifier;
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
import org.intellij.plugins.relaxNG.RelaxNgMetaDataContributor;
import org.intellij.plugins.relaxNG.RelaxngBundle;
import org.intellij.plugins.relaxNG.xml.dom.RngGrammar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// XXX: the tests rely on this still being an intention action
class CreatePatternFix implements IntentionAction, LocalQuickFix {
  private final PsiReference myReference;

  CreatePatternFix(PsiReference reference) {
    myReference = reference;
  }

  @Override
  public @NotNull String getText() {
    return RelaxngBundle.message("relaxng.quickfix.create-pattern.name", myReference.getCanonicalText());
  }

  @Override
  public @NotNull String getFamilyName() {
    return RelaxngBundle.message("relaxng.quickfix.create-pattern.family");
  }

  @Override
  public @NotNull String getName() {
    return getText();
  }

  @Override
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

  @Override
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

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    doFix();
  }

  private void doFix() throws IncorrectOperationException {
    final XmlTag tag = PsiTreeUtil.getParentOfType(myReference.getElement(), XmlTag.class);
    assert tag != null;
    final XmlTag defineTag = tag.createChildTag("define", RelaxNgMetaDataContributor.RNG_NAMESPACE, "\n \n", false);
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

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    PsiElement copy = PsiTreeUtil.findSameElementInCopy(myReference.getElement(), target);
    return new CreatePatternFix(copy.getReference());
  }
}
