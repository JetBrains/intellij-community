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
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 18-Nov-2005
 */
public class InsertRequiredAttributeFix implements IntentionAction, LocalQuickFix, HighPriorityAction {
  private final XmlTag myTag;
  private final String myAttrName;
  private final String[] myValues;
  @NonNls
  private static final String NAME_TEMPLATE_VARIABLE = "name";

  public InsertRequiredAttributeFix(@NotNull XmlTag tag, @NotNull String attrName,@NotNull String... values) {
    myTag = tag;
    myAttrName = attrName;
    myValues = values;
  }

  @Override
  @NotNull
  public String getText() {
    return XmlErrorMessages.message("insert.required.attribute.quickfix.text", myAttrName);
  }

  @Override
  @NotNull
  public String getName() {
    return getText();
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return XmlErrorMessages.message("insert.required.attribute.quickfix.family");
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    invoke(project, null, myTag.getContainingFile());
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myTag.isValid();
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    ASTNode treeElement = SourceTreeToPsiMap.psiElementToTree(myTag);

    final XmlElementDescriptor descriptor = myTag.getDescriptor();
    if (descriptor == null) {
      return;
    }
    final XmlAttributeDescriptor attrDescriptor = descriptor.getAttributeDescriptor(myAttrName, myTag);
    boolean indirectSyntax = XmlExtension.getExtension(myTag.getContainingFile()).isIndirectSyntax(attrDescriptor);

    PsiElement anchor = SourceTreeToPsiMap.treeElementToPsi(
      XmlChildRole.EMPTY_TAG_END_FINDER.findChild(treeElement)
    );

    final boolean anchorIsEmptyTag = anchor != null;

    if (anchor == null) {
      anchor = SourceTreeToPsiMap.treeElementToPsi(
        XmlChildRole.START_TAG_END_FINDER.findChild(treeElement)
      );
    }

    if (anchor == null) return;

    final Template template = TemplateManager.getInstance(project).createTemplate("", "");
    if (indirectSyntax) {
      if (anchorIsEmptyTag) template.addTextSegment(">");
      template.addTextSegment("<jsp:attribute name=\"" + myAttrName + "\">");
    } else {
      template.addTextSegment(" " + myAttrName + "=\"");
    }

    Expression expression = new Expression() {
      TextResult result = new TextResult("");

      @Override
      public Result calculateResult(ExpressionContext context) {
        return result;
      }

      @Override
      public Result calculateQuickResult(ExpressionContext context) {
        return null;
      }

      @Override
      public LookupElement[] calculateLookupItems(ExpressionContext context) {
        final LookupElement[] items = new LookupElement[myValues.length];

        for (int i = 0; i < items.length; i++) {
          items[i] = LookupElementBuilder.create(myValues[i]);
        }
        return items;
      }
    };
    template.addVariable(NAME_TEMPLATE_VARIABLE, expression, expression, true);
    if (indirectSyntax) {
      template.addTextSegment("</jsp:attribute>");
      template.addEndVariable();
      if (anchorIsEmptyTag) template.addTextSegment("</" + myTag.getName() + ">");
    } else {
      template.addTextSegment("\"");
    }

    final PsiElement anchor1 = anchor;

    final boolean indirectSyntax1 = indirectSyntax;
    final Runnable runnable = new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(
          new Runnable() {
            @Override
            public void run() {
              int textOffset = anchor1.getTextOffset();
              if (!anchorIsEmptyTag && indirectSyntax1) ++textOffset;
              editor.getCaretModel().moveToOffset(textOffset);
              if (anchorIsEmptyTag && indirectSyntax1) {
                editor.getDocument().deleteString(textOffset,textOffset + 2);
              }
              TemplateManager.getInstance(project).startTemplate(editor, template);
            }
          }
        );
      }
    };

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      Runnable commandRunnable = new Runnable() {
        @Override
        public void run() {
          CommandProcessor.getInstance().executeCommand(
            project,
            runnable,
            getText(),
            getFamilyName()
          );
        }
      };

      ApplicationManager.getApplication().invokeLater(commandRunnable);
    }
    else {
      runnable.run();
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
