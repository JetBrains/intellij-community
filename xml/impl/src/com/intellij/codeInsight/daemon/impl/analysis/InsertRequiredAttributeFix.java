// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.editorActions.XmlEditUtil;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.lang.ASTNode;
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
import com.intellij.xml.psi.XmlPsiBundle;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InsertRequiredAttributeFix extends LocalQuickFixAndIntentionActionOnPsiElement implements HighPriorityAction {
  private final String myAttrName;
  private final String[] myValues;
  private static final @NonNls String NAME_TEMPLATE_VARIABLE = "name";

  public InsertRequiredAttributeFix(@NotNull XmlTag tag, @NotNull String attrName, String @NotNull ... values) {
    super(tag);
    myAttrName = attrName;
    myValues = values;
  }

  @Override
  public @NotNull String getText() {
    return XmlPsiBundle.message("xml.quickfix.insert.required.attribute.text", myAttrName);
  }

  @Override
  public @NotNull String getFamilyName() {
    return XmlPsiBundle.message("xml.quickfix.insert.required.attribute.family");
  }

  @Override
  public void invoke(final @NotNull Project project,
                     @NotNull PsiFile psiFile,
                     final @Nullable Editor editor,
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
        String quote = XmlEditUtil.getAttributeQuote(psiFile);
        AttributeValuePresentation presentation = XmlExtension.getExtension(psiFile).getAttributeValuePresentation(myTag, myAttrName, quote);

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
    }
    else if (!insertShorthand) {
      template.addTextSegment(valuePostfix);
    }
    
    int textOffset = anchor.getTextOffset();
    if (!anchorIsEmptyTag && indirectSyntax) ++textOffset;
    editor.getCaretModel().moveToOffset(textOffset);
    if (anchorIsEmptyTag && indirectSyntax) {
      editor.getDocument().deleteString(textOffset, textOffset + 2);
    }
    TemplateManager.getInstance(project).startTemplate(editor, template);
  }
}
