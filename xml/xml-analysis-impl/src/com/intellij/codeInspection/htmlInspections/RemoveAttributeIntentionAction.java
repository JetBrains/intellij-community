/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class RemoveAttributeIntentionAction implements LocalQuickFix {
  private final String myLocalName;

  public RemoveAttributeIntentionAction(final String localName) {
    myLocalName = localName;
  }

  @Override
  @NotNull
  public String getName() {
    return XmlErrorMessages.message("remove.attribute.quickfix.text", myLocalName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return XmlErrorMessages.message("remove.attribute.quickfix.family");
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    PsiElement e = descriptor.getPsiElement();
    final XmlAttribute myAttribute = PsiTreeUtil.getParentOfType(e, XmlAttribute.class);
    if (myAttribute == null) return;

    if (!FileModificationService.getInstance().prepareFileForWrite(myAttribute.getContainingFile())) {
      return;
    }

    new WriteCommandAction(project) {
      @Override
      protected void run(@NotNull final Result result) throws Throwable {
        myAttribute.delete();
      }
    }.execute();
  }
}
