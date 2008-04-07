package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingListener;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.macro.MacroFactory;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.jsp.jspXml.JspXmlRootTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
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

class XmlTagInsertHandler extends BasicInsertHandler {
  public XmlTagInsertHandler() {
  }

  public void handleInsert(CompletionContext context,
                           int startOffset,
                           LookupData data,
                           LookupItem item,
                           boolean signatureSelected,
                           char completionChar) {
    super.handleInsert(context, startOffset, data, item, signatureSelected, completionChar);
    Project project = context.project;
    Editor editor = context.editor;
    // Need to insert " " to prevent creating tags like <tagThis is my text
    final int offset = editor.getCaretModel().getOffset();
    editor.getDocument().insertString(offset, " ");
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    PsiElement current = context.file.findElementAt(context.getStartOffset());
    editor.getDocument().deleteString(offset, offset + 1);
    final XmlTag tag = PsiTreeUtil.getContextOfType(current, XmlTag.class, true);

    if (tag == null || tag instanceof JspXmlRootTag) return;

    final XmlElementDescriptor descriptor = tag.getDescriptor();
    if (XmlUtil.getTokenOfType(tag, XmlTokenType.XML_TAG_END) == null &&
        XmlUtil.getTokenOfType(tag, XmlTokenType.XML_EMPTY_ELEMENT_END) == null) {

      Template t = TemplateManager.getInstance(project).getActiveTemplate(editor);
      if (t == null && descriptor != null) {
        insertIncompleteTag(completionChar, editor, project, descriptor, tag);
      }
    }
    else if (completionChar == Lookup.REPLACE_SELECT_CHAR) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();

      int caretOffset = editor.getCaretModel().getOffset();

      PsiElement otherTag = PsiTreeUtil.getParentOfType(context.file.findElementAt(caretOffset), XmlTag.class);

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
    current = context.file.findElementAt(context.getStartOffset());
    if (current != null && current.getPrevSibling()instanceof XmlToken) {
      if (!isClosed(current) && ((XmlToken)current.getPrevSibling()).getTokenType() == XmlTokenType.XML_END_TAG_START) {
        editor.getDocument().insertString(current.getTextRange().getEndOffset(), ">");
        editor.getCaretModel().moveToOffset(editor.getCaretModel().getOffset() + 1);
      }
    }
  }

  private static boolean isClosed(PsiElement current) {
    PsiElement e = current;

    while (e != null) {
      if (e instanceof XmlToken) {
        XmlToken token = (XmlToken)e;
        if (token.getTokenType() == XmlTokenType.XML_TAG_END) return true;
        if (token.getTokenType() == XmlTokenType.XML_EMPTY_ELEMENT_END) return true;
      }

      e = e.getNextSibling();
    }

    return false;
  }

  private static void insertIncompleteTag(char completionChar, final Editor editor, Project project, XmlElementDescriptor descriptor, XmlTag tag) {
    TemplateManager templateManager = TemplateManager.getInstance(project);
    Template template = templateManager.createTemplate("", "");

    template.setToIndent(true);

    // temp code
    boolean htmlCode = HtmlUtil.hasHtml(tag.getContainingFile());
    Set<String> notRequiredAttributes = Collections.emptySet();

    if (tag instanceof HtmlTag) {
      final InspectionProfile profile = InspectionProjectProfileManager.getInstance(tag.getProject()).getInspectionProfile(tag);
      LocalInspectionToolWrapper localInspectionToolWrapper = (LocalInspectionToolWrapper) profile.getInspectionTool(
        RequiredAttributesInspection.SHORT_NAME);
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
    final XmlExtension extension = XmlExtension.getExtension((XmlFile)tag.getContainingFile());
    for (XmlAttributeDescriptor attributeDecl : attributes) {
      String attributeName = attributeDecl.getName(tag);

      if (attributeDecl.isRequired() && tag.getAttributeValue(attributeName) == null) {
        if (!notRequiredAttributes.contains(attributeName)) {
          if (!extension.isIndirectSyntax(attributeDecl)) {
            template.addTextSegment(" " + attributeName + "=\"");
            Expression expression = new MacroCallNode(MacroFactory.createMacro("complete"));
            template.addVariable(attributeName, expression, expression, true);
            template.addTextSegment("\"");
          } else {
            if (indirectRequiredAttrs == null) indirectRequiredAttrs = new StringBuilder();
            indirectRequiredAttrs.append("\n<jsp:attribute name=\"").append(attributeName).append("\"></jsp:attribute>\n");
          }
        }
      }
      else if (attributeDecl.isFixed() && attributeDecl.getDefaultValue() != null && !htmlCode) {
        template.addTextSegment(" " + attributeName + "=\"" + attributeDecl.getDefaultValue() + "\"");
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

      if (!(tag instanceof HtmlTag) || !HtmlUtil.isSingleHtmlTag(tag.getName())) {
        template.addTextSegment("</");
        template.addTextSegment(descriptor.getName(tag));
        template.addTextSegment(">");
      }
    }
    else if (completionChar == '/') {
      template.addTextSegment("/>");
    } else if (completionChar == ' ' && template.getSegmentsCount() == 0) {
      template.addTextSegment(" ");
      if (!isTagFromHtml(tag) || !HtmlUtil.isTagWithoutAttributes(tag.getName())) {
        final MacroCallNode completeAttrExpr = new MacroCallNode(MacroFactory.createMacro("complete"));
        template.addVariable("attrComplete", completeAttrExpr,completeAttrExpr,true);
        weInsertedSomeCodeThatCouldBeInvalidated = true;
        template.addTextSegment("=\"");
        template.addEndVariable();
        template.addTextSegment("\"");
      }
    }

    final boolean weInsertedSomeCodeThatCouldBeInvalidated1 = weInsertedSomeCodeThatCouldBeInvalidated;
    templateManager.startTemplate(editor, template, new TemplateEditingListener() {
      public void templateFinished(final Template template) {
        final int offset = editor.getCaretModel().getOffset();

        if (weInsertedSomeCodeThatCouldBeInvalidated1 &&
            offset >= 3 &&
            editor.getDocument().getCharsSequence().charAt(offset - 3) == '/') {
          editor.getDocument().replaceString(offset - 2, offset + 1, ">");
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
