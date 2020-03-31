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

import com.intellij.codeInsight.daemon.XmlErrorBundle;
import com.intellij.codeInsight.editorActions.XmlEditUtil;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.XmlExtension.AttributeValuePresentation;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InsertRequiredAttributeFix extends LocalQuickFixAndIntentionActionOnPsiElement implements HighPriorityAction {
  private final String myAttrName;
  private final String[] myValues;
  @NonNls
  private static final String NAME_TEMPLATE_VARIABLE = "name";

  public InsertRequiredAttributeFix(@NotNull XmlTag tag, @NotNull String attrName, String @NotNull ... values) {
    super(tag);
    myAttrName = attrName;
    myValues = values;
  }

  @Override
  @NotNull
  public String getText() {
    return XmlErrorBundle.message("insert.required.attribute.quickfix.text", myAttrName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return XmlErrorBundle.message("insert.required.attribute.quickfix.family");
  }

  @Override
  public void invoke(@NotNull final Project project,
                     @NotNull PsiFile file,
                     @Nullable final Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    XmlTag myTag = (XmlTag)startElement;
    ASTNode treeElement = SourceTreeToPsiMap.psiElementToTree(myTag);

    final XmlElementDescriptor descriptor = myTag.getDescriptor();
    if (descriptor == null) {
      return;
    }
    final XmlAttributeDescriptor attrDescriptor = descriptor.getAttributeDescriptor(myAttrName, myTag);
    final boolean indirectSyntax = XmlExtension.getExtension(myTag.getContainingFile()).isIndirectSyntax(attrDescriptor);
    boolean insertShorthand = myTag instanceof HtmlTag && attrDescriptor != null && HtmlUtil.isBooleanAttribute(attrDescriptor, myTag);

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
    String valuePostfix = "\"";
    if (indirectSyntax) {
      if (anchorIsEmptyTag) template.addTextSegment(">");
      template.addTextSegment("<jsp:attribute name=\"" + myAttrName + "\">");
    }
    else {
      template.addTextSegment(" " + myAttrName);
      if (!insertShorthand) {
        String quote = XmlEditUtil.getAttributeQuote(file);
        AttributeValuePresentation presentation = XmlExtension.getExtension(file).getAttributeValuePresentation(myTag, myAttrName, quote);

        valuePostfix = presentation.getPostfix();
        template.addTextSegment("=" + presentation.getPrefix());
      }
    }

    Expression expression = new ConstantNode("").withLookupStrings(myValues);
    if (!insertShorthand) template.addVariable(NAME_TEMPLATE_VARIABLE, expression, expression, true);

    if (indirectSyntax) {
      template.addTextSegment("</jsp:attribute>");
      template.addEndVariable();
      if (anchorIsEmptyTag) template.addTextSegment("</" + myTag.getName() + ">");
    } else if (!insertShorthand) {
      template.addTextSegment(valuePostfix);
    }

    final PsiElement anchor1 = anchor;

    ApplicationManager.getApplication().invokeLater(() -> {
      WriteCommandAction.runWriteCommandAction(project, getText(), getFamilyName(), () -> {
        int textOffset = anchor1.getTextOffset();
        if (!anchorIsEmptyTag && indirectSyntax) ++textOffset;
        editor.getCaretModel().moveToOffset(textOffset);
        if (anchorIsEmptyTag && indirectSyntax) {
          editor.getDocument().deleteString(textOffset,textOffset + 2);
        }
        TemplateManager.getInstance(project).startTemplate(editor, template);
      });
    });
  }
}
