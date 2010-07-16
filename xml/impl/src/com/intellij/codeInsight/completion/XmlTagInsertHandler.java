/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.macro.MacroFactory;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementDescriptorWithCDataContent;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

public class XmlTagInsertHandler implements InsertHandler<LookupElement> {
  public static final XmlTagInsertHandler INSTANCE = new XmlTagInsertHandler();

  public void handleInsert(InsertionContext context, LookupElement item) {
    Project project = context.getProject();
    Editor editor = context.getEditor();
    // Need to insert " " to prevent creating tags like <tagThis is my text
    final int offset = editor.getCaretModel().getOffset();
    editor.getDocument().insertString(offset, " ");
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    PsiElement current = context.getFile().findElementAt(context.getStartOffset());
    editor.getDocument().deleteString(offset, offset + 1);
    final XmlTag tag = PsiTreeUtil.getContextOfType(current, XmlTag.class, true);

    if (tag == null) return;

    context.setAddCompletionChar(false);

    final XmlElementDescriptor descriptor = tag.getDescriptor();
    if (XmlUtil.getTokenOfType(tag, XmlTokenType.XML_TAG_END) == null &&
        XmlUtil.getTokenOfType(tag, XmlTokenType.XML_EMPTY_ELEMENT_END) == null) {

      Template t = TemplateManager.getInstance(project).getActiveTemplate(editor);
      if (t == null && descriptor != null) {
        insertIncompleteTag(context.getCompletionChar(), editor, project, descriptor, tag);
      }
    }
    else if (context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();

      int caretOffset = editor.getCaretModel().getOffset();

      PsiElement otherTag = PsiTreeUtil.getParentOfType(context.getFile().findElementAt(caretOffset), XmlTag.class);

      PsiElement endTagStart = XmlUtil.getTokenOfType(otherTag, XmlTokenType.XML_END_TAG_START);

      if (endTagStart != null) {
        PsiElement sibling = endTagStart.getNextSibling();

        if (sibling.getNode().getElementType() == XmlTokenType.XML_NAME) {
          int sOffset = sibling.getTextRange().getStartOffset();
          int eOffset = sibling.getTextRange().getEndOffset();

          editor.getDocument().deleteString(sOffset, eOffset);
          editor.getDocument().insertString(sOffset, ((XmlTag)otherTag).getName());
        }
      }

      editor.getCaretModel().moveToOffset(caretOffset + 1);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();
    }

    if (context.getCompletionChar() == ' ' && TemplateManager.getInstance(project).getActiveTemplate(editor) != null) {
      return;
    }

    final TailType tailType = LookupItem.handleCompletionChar(editor, item, context.getCompletionChar());
    tailType.processTail(editor, editor.getCaretModel().getOffset());
  }

  private static void insertIncompleteTag(char completionChar, final Editor editor, final Project project, XmlElementDescriptor descriptor, XmlTag tag) {
    TemplateManager templateManager = TemplateManager.getInstance(project);
    Template template = templateManager.createTemplate("", "");

    template.setToIndent(true);

    // temp code
    boolean htmlCode = HtmlUtil.hasHtml(tag.getContainingFile());
    Set<String> notRequiredAttributes = Collections.emptySet();

    if (tag instanceof HtmlTag) {
      final InspectionProfile profile = InspectionProjectProfileManager.getInstance(tag.getProject()).getInspectionProfile();
      LocalInspectionToolWrapper localInspectionToolWrapper = (LocalInspectionToolWrapper) profile.getInspectionTool(
        RequiredAttributesInspection.SHORT_NAME, tag);
      RequiredAttributesInspection inspection = localInspectionToolWrapper != null ?
        (RequiredAttributesInspection) localInspectionToolWrapper.getTool(): null;

      if (inspection != null) {
        StringTokenizer tokenizer = new StringTokenizer(inspection.getAdditionalEntries(0));
        notRequiredAttributes = new HashSet<String>(1);

        while(tokenizer.hasMoreElements()) notRequiredAttributes.add(tokenizer.nextToken());
      }
    }

    boolean toReformat = true;
    boolean weInsertedSomeCodeThatCouldBeInvalidated = false;
    if (htmlCode) {
      toReformat = false;
    }
    template.setToReformat(toReformat);

    XmlAttributeDescriptor[] attributes = descriptor.getAttributesDescriptors(tag);
    StringBuilder indirectRequiredAttrs = null;
    final XmlExtension extension = XmlExtension.getExtension(tag.getContainingFile());
    if (WebEditorOptions.getInstance().isAutomaticallyInsertRequiredAttributes()) {
      for (XmlAttributeDescriptor attributeDecl : attributes) {
        String attributeName = attributeDecl.getName(tag);

        if (attributeDecl.isRequired() && tag.getAttributeValue(attributeName) == null) {
          if (!notRequiredAttributes.contains(attributeName)) {
            if (!extension.isIndirectSyntax(attributeDecl)) {
              template.addTextSegment(" " + attributeName + "=\"");
              Expression expression = new MacroCallNode(MacroFactory.createMacro("complete"));
              template.addVariable(attributeName, expression, expression, true);
              template.addTextSegment("\"");
            }
            else {
              if (indirectRequiredAttrs == null) indirectRequiredAttrs = new StringBuilder();
              indirectRequiredAttrs.append("\n<jsp:attribute name=\"").append(attributeName).append("\"></jsp:attribute>\n");
            }
          }
        }
        else if (attributeDecl.isRequired() && attributeDecl.isFixed() && attributeDecl.getDefaultValue() != null && !htmlCode) {
          template.addTextSegment(" " + attributeName + "=\"" + attributeDecl.getDefaultValue() + "\"");
        }
      }
    }

    if (completionChar == '>' || (completionChar == '/' && indirectRequiredAttrs != null)) {
      template.addTextSegment(">");
      boolean toInsertCDataEnd = false;

      if (descriptor instanceof XmlElementDescriptorWithCDataContent) {
        final XmlElementDescriptorWithCDataContent cDataContainer = (XmlElementDescriptorWithCDataContent)descriptor;

        if (cDataContainer.requiresCdataBracesInContext(tag)) {
          template.addTextSegment("<![CDATA[\n");
          toInsertCDataEnd = true;
        }
      }

      if (indirectRequiredAttrs != null) template.addTextSegment(indirectRequiredAttrs.toString());
      template.addEndVariable();

      if (toInsertCDataEnd) template.addTextSegment("\n]]>");

      if ((!(tag instanceof HtmlTag) || !HtmlUtil.isSingleHtmlTag(tag.getName())) && tag.getAttributes().length == 0) {
        if (WebEditorOptions.getInstance().isAutomaticallyInsertClosingTag()) {
          final String name = descriptor.getName(tag);
          if (name != null) {
            template.addTextSegment("</");
            template.addTextSegment(name);
            template.addTextSegment(">");
          }
        }
      }
    }
    else if (completionChar == '/') {
      template.addTextSegment("/>");
    } else if (completionChar == ' ' && template.getSegmentsCount() == 0) {
      if (WebEditorOptions.getInstance().isAutomaticallyStartAttribute() &&
          (attributes.length > 0 || isTagFromHtml(tag) && !HtmlUtil.isTagWithoutAttributes(tag.getName()))) {
        template.addTextSegment(" ");
        final MacroCallNode completeAttrExpr = new MacroCallNode(MacroFactory.createMacro("complete"));
        template.addVariable("attrComplete", completeAttrExpr, completeAttrExpr, true);
        weInsertedSomeCodeThatCouldBeInvalidated = true;
        template.addTextSegment("=\"");
        template.addEndVariable();
        template.addTextSegment("\"");
      }
    } else if ((completionChar == Lookup.AUTO_INSERT_SELECT_CHAR || completionChar == Lookup.NORMAL_SELECT_CHAR) && WebEditorOptions.getInstance().isAutomaticallyInsertClosingTag() && HtmlUtil.isSingleHtmlTag(tag.getName())) {
      template.addTextSegment(tag instanceof HtmlTag ? ">" : "/>");
    }

    final boolean weInsertedSomeCodeThatCouldBeInvalidated1 = weInsertedSomeCodeThatCouldBeInvalidated;
    templateManager.startTemplate(editor, template, new TemplateEditingAdapter() {
      public void templateFinished(final Template template, boolean brokenOff) {
        final int offset = editor.getCaretModel().getOffset();

        if (weInsertedSomeCodeThatCouldBeInvalidated1 &&
            offset >= 3 &&
            editor.getDocument().getCharsSequence().charAt(offset - 3) == '/') {
          new WriteCommandAction.Simple(project) {
            protected void run() throws Throwable {
              editor.getDocument().replaceString(offset - 2, offset + 1, ">");
            }
          }.execute();
        }
      }

      public void templateCancelled(final Template template) {
        //final int offset = editor.getCaretModel().getOffset();
        //if (weInsertedSomeCodeThatCouldBeInvalidated1) {}
      }
    });
  }

  private static boolean isTagFromHtml(final XmlTag tag) {
    final String ns = tag.getNamespace();
    return XmlUtil.XHTML_URI.equals(ns) || XmlUtil.HTML_URI.equals(ns);
  }
}
