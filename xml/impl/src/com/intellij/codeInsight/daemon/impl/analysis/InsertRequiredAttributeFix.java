/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.*;
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
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.jsp.impl.JspElementDescriptor;
import com.intellij.jsp.impl.TldAttributeDescriptor;
import org.jetbrains.annotations.NonNls;

/**
 * User: anna
 * Date: 18-Nov-2005
 */
public class InsertRequiredAttributeFix implements IntentionAction {
  private final XmlTag myTag;
  private final String myAttrName;
  private String[] myValues;
  @NonNls
  private static final String NAME_TEMPLATE_VARIABLE = "name";

  public InsertRequiredAttributeFix(final XmlTag tag, final String attrName,final String[] values) {
    myTag = tag;
    myAttrName = attrName;
    myValues = values;
  }

  public String getText() {
    return XmlErrorMessages.message("insert.required.attribute.quickfix.text", myAttrName);
  }

  public String getFamilyName() {
    return XmlErrorMessages.message("insert.required.attribute.quickfix.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(final Project project, final Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    ASTNode treeElement = SourceTreeToPsiMap.psiElementToTree(myTag);
    boolean indirectSyntax = false;

    final XmlElementDescriptor descriptor = myTag.getDescriptor();
    if (descriptor instanceof JspElementDescriptor) {
      final XmlAttributeDescriptor attrDescriptor = descriptor.getAttributeDescriptor(myAttrName, myTag);
      if (attrDescriptor instanceof TldAttributeDescriptor && ((TldAttributeDescriptor)attrDescriptor).isIndirectSyntax()) {
        indirectSyntax = true;
      }
    }

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

      public Result calculateResult(ExpressionContext context) {
        return result;
      }

      public Result calculateQuickResult(ExpressionContext context) {
        return null;
      }

      public LookupItem[] calculateLookupItems(ExpressionContext context) {
        final LookupItem items[] = new LookupItem[myValues == null ? 0 : myValues.length];

        if (myValues != null) {
          for (int i = 0; i < items.length; i++) {
            items[i] = LookupItemUtil.objectToLookupItem(myValues[i]);
          }
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
      public void run() {
        ApplicationManager.getApplication().runWriteAction(
          new Runnable() {
            public void run() {
              int textOffset = anchor1.getTextOffset();
              if (!anchorIsEmptyTag && indirectSyntax1) ++textOffset;
              editor.getCaretModel().moveToOffset(textOffset);
              if (anchorIsEmptyTag && indirectSyntax1) {
                editor.getDocument().deleteString(textOffset,textOffset + 2);
              }
              TemplateManager.getInstance(project).startTemplate(editor, template, null);
            }
          }
        );
      }
    };

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      Runnable commandRunnable = new Runnable() {
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

  public boolean startInWriteAction() {
    return true;
  }
}
