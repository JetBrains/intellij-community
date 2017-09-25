/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression;
import com.intellij.codeInsight.editorActions.XmlEditUtil;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.macro.CompleteMacro;
import com.intellij.codeInsight.template.macro.CompleteSmartMacro;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.htmlInspections.XmlEntitiesInspection;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.*;
import com.intellij.xml.XmlExtension.AttributeValuePresentation;
import com.intellij.xml.actions.GenerateXmlTagAction;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class XmlTagInsertHandler implements InsertHandler<LookupElement> {
  public static final Key<Boolean> ENFORCING_TAG = Key.create("xml.insert.handler.enforcing.tag");
  public static final XmlTagInsertHandler INSTANCE = new XmlTagInsertHandler();

  @Override
  public void handleInsert(InsertionContext context, LookupElement item) {
    Project project = context.getProject();
    Editor editor = context.getEditor();
    // Need to insert " " to prevent creating tags like <tagThis is my text
    InjectedLanguageUtil.getTopLevelEditor(editor).getDocument().putUserData(ENFORCING_TAG, Boolean.TRUE);
    final int offset = editor.getCaretModel().getOffset();
    editor.getDocument().insertString(offset, " ");
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    PsiElement current = context.getFile().findElementAt(context.getStartOffset());
    editor.getDocument().deleteString(offset, offset + 1);
    InjectedLanguageUtil.getTopLevelEditor(editor).getDocument().putUserData(ENFORCING_TAG, null);

    final XmlTag tag = PsiTreeUtil.getContextOfType(current, XmlTag.class, true);

    if (tag == null) return;

    if (context.getCompletionChar() != Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
      context.setAddCompletionChar(false);
    }

    final XmlElementDescriptor descriptor = tag.getDescriptor();

    if (XmlUtil.getTokenOfType(tag, XmlTokenType.XML_TAG_END) == null &&
        XmlUtil.getTokenOfType(tag, XmlTokenType.XML_EMPTY_ELEMENT_END) == null) {

      if (descriptor != null) {
        insertIncompleteTag(context.getCompletionChar(), editor, tag);
      }
    }
    else if (context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();

      int caretOffset = editor.getCaretModel().getOffset();

      PsiElement otherTag = PsiTreeUtil.getParentOfType(context.getFile().findElementAt(caretOffset), XmlTag.class);

      PsiElement endTagStart = XmlUtil.getTokenOfType(otherTag, XmlTokenType.XML_END_TAG_START);

      if (endTagStart != null) {
        PsiElement sibling = endTagStart.getNextSibling();

        assert sibling != null;
        ASTNode node = sibling.getNode();
        assert node != null;
        if (node.getElementType() == XmlTokenType.XML_NAME) {
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

  public static void insertIncompleteTag(char completionChar,
                                          final Editor editor,
                                          XmlTag tag) {
    XmlElementDescriptor descriptor = tag.getDescriptor();
    final Project project = editor.getProject();
    TemplateManager templateManager = TemplateManager.getInstance(project);
    Template template = templateManager.createTemplate("", "");

    template.setToIndent(true);

    // temp code
    PsiFile containingFile = tag.getContainingFile();
    boolean htmlCode = HtmlUtil.hasHtml(containingFile) || HtmlUtil.supportsXmlTypedHandlers(containingFile);
    template.setToReformat(!htmlCode);

    StringBuilder indirectRequiredAttrs = addRequiredAttributes(descriptor, tag, template, containingFile);
    final boolean chooseAttributeName = addTail(completionChar, descriptor, htmlCode, tag, template, indirectRequiredAttrs);

    templateManager.startTemplate(editor, template, new TemplateEditingAdapter() {
      private RangeMarker myAttrValueMarker;

      @Override
      public void waitingForInput(Template template) {
        int offset = editor.getCaretModel().getOffset();
        myAttrValueMarker = editor.getDocument().createRangeMarker(offset + 1, offset + 4);
      }

      @Override
      public void templateFinished(final Template template, boolean brokenOff) {
        final int offset = editor.getCaretModel().getOffset();

        if (chooseAttributeName && offset > 0) {
          char c = editor.getDocument().getCharsSequence().charAt(offset - 1);
          if (c == '/' || (c == ' ' && brokenOff)) {
            new WriteCommandAction.Simple(project) {
              @Override
              protected void run() throws Throwable {
                editor.getDocument().replaceString(offset, offset + 3, ">");
              }
            }.execute();
          }
        }
      }

      @Override
      public void templateCancelled(final Template template) {
        if (myAttrValueMarker == null) {
          return;
        }

        final UndoManager manager = UndoManager.getInstance(project);
        if (manager.isUndoInProgress() || manager.isRedoInProgress()) {
          return;
        }

        if (chooseAttributeName && myAttrValueMarker.isValid()) {
          final int startOffset = myAttrValueMarker.getStartOffset();
          final int endOffset = myAttrValueMarker.getEndOffset();
          new WriteCommandAction.Simple(project) {
            @Override
            protected void run() throws Throwable {
              editor.getDocument().replaceString(startOffset, endOffset, ">");
            }
          }.execute();
        }
      }
    });
  }

  @Nullable
  private static StringBuilder addRequiredAttributes(XmlElementDescriptor descriptor,
                                                     @Nullable XmlTag tag,
                                                     Template template,
                                                     PsiFile containingFile) {

    boolean htmlCode = HtmlUtil.hasHtml(containingFile) || HtmlUtil.supportsXmlTypedHandlers(containingFile);
    Set<String> notRequiredAttributes = Collections.emptySet();

    if (tag instanceof HtmlTag) {
      final InspectionProfile profile = InspectionProjectProfileManager.getInstance(tag.getProject()).getCurrentProfile();
      XmlEntitiesInspection inspection = (XmlEntitiesInspection)profile.getUnwrappedTool(
        XmlEntitiesInspection.REQUIRED_ATTRIBUTES_SHORT_NAME, tag);

      if (inspection != null) {
        StringTokenizer tokenizer = new StringTokenizer(inspection.getAdditionalEntries());
        notRequiredAttributes = new HashSet<>();

        while(tokenizer.hasMoreElements()) notRequiredAttributes.add(tokenizer.nextToken());
      }
    }

    XmlAttributeDescriptor[] attributes = descriptor.getAttributesDescriptors(tag);
    StringBuilder indirectRequiredAttrs = null;

    if (WebEditorOptions.getInstance().isAutomaticallyInsertRequiredAttributes()) {
      final XmlExtension extension = XmlExtension.getExtension(containingFile);

      for (XmlAttributeDescriptor attributeDecl : attributes) {
        String attributeName = attributeDecl.getName(tag);

        boolean shouldBeInserted = extension.shouldBeInserted(attributeDecl);
        if (!shouldBeInserted) continue;

        AttributeValuePresentation presenter =
          extension.getAttributeValuePresentation(attributeDecl, XmlEditUtil.getAttributeQuote(htmlCode));

        if (tag == null || tag.getAttributeValue(attributeName) == null) {
          if (!notRequiredAttributes.contains(attributeName)) {
            if (!extension.isIndirectSyntax(attributeDecl)) {
              template.addTextSegment(" " + attributeName + "=" + presenter.getPrefix());
              template.addVariable(presenter.showAutoPopup() ? new MacroCallNode(new CompleteMacro()) : new EmptyExpression(), true);
              template.addTextSegment(presenter.getPostfix());
            }
            else {
              if (indirectRequiredAttrs == null) indirectRequiredAttrs = new StringBuilder();
              indirectRequiredAttrs.append("\n<jsp:attribute name=\"").append(attributeName).append("\"></jsp:attribute>\n");
            }
          }
        }
        else if (attributeDecl.isFixed() && attributeDecl.getDefaultValue() != null && !htmlCode) {
          template.addTextSegment(" " + attributeName + "=" +
                                  presenter.getPrefix() + attributeDecl.getDefaultValue() + presenter.getPostfix());
        }
      }
    }
    return indirectRequiredAttrs;
  }

  protected static boolean addTail(char completionChar,
                                   XmlElementDescriptor descriptor,
                                   boolean isHtmlCode,
                                   XmlTag tag,
                                   Template template,
                                   StringBuilder indirectRequiredAttrs) {
    boolean htmlCode = HtmlUtil.hasHtml(tag.getContainingFile()) || HtmlUtil.supportsXmlTypedHandlers(tag.getContainingFile());

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
      template.addTextSegment(closeTag(tag));
    }
    else if (completionChar == ' ' && template.getSegmentsCount() == 0) {
      if (WebEditorOptions.getInstance().isAutomaticallyStartAttribute() &&
          (descriptor.getAttributesDescriptors(tag).length > 0 || isTagFromHtml(tag) && !HtmlUtil.isTagWithoutAttributes(tag.getName()))) {
        completeAttribute(template, htmlCode);
        return true;
      }
    }
    else if (completionChar == Lookup.AUTO_INSERT_SELECT_CHAR || completionChar == Lookup.NORMAL_SELECT_CHAR || completionChar == Lookup.REPLACE_SELECT_CHAR) {
      if (WebEditorOptions.getInstance().isAutomaticallyInsertClosingTag() && isHtmlCode && HtmlUtil.isSingleHtmlTag(tag.getName())) {
        template.addTextSegment(HtmlUtil.isHtmlTag(tag) ? ">" : closeTag(tag));
      }
      else {
        if (needAlLeastOneAttribute(tag) && WebEditorOptions.getInstance().isAutomaticallyStartAttribute() && tag.getAttributes().length == 0
            && template.getSegmentsCount() == 0) {
          completeAttribute(template, htmlCode);
          return true;
        }
        else {
          completeTagTail(template, descriptor, tag.getContainingFile(), tag, true);
        }
      }
    }

    return false;
  }

  @NotNull
  private static String closeTag(XmlTag tag) {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(tag.getProject());
    boolean html = HtmlUtil.isHtmlTag(tag);
    boolean needsSpace = (html && settings.HTML_SPACE_INSIDE_EMPTY_TAG) ||
                         (!html && settings.getCustomSettings(XmlCodeStyleSettings.class).XML_SPACE_INSIDE_EMPTY_TAG);
    return needsSpace ? " />" : "/>";
  }

  private static void completeAttribute(Template template, boolean htmlCode) {
    template.addTextSegment(" ");
    template.addVariable(new MacroCallNode(new CompleteMacro()), true);
    template.addTextSegment("=" + XmlEditUtil.getAttributeQuote(htmlCode));
    template.addEndVariable();
    template.addTextSegment(XmlEditUtil.getAttributeQuote(htmlCode));
  }

  private static boolean needAlLeastOneAttribute(XmlTag tag) {
    for (XmlTagRuleProvider ruleProvider : XmlTagRuleProvider.EP_NAME.getExtensions()) {
      for (XmlTagRuleProvider.Rule rule : ruleProvider.getTagRule(tag)) {
        if (rule.needAtLeastOneAttribute(tag)) {
          return true;
        }
      }
    }

    return false;
  }

  private static boolean addRequiredSubTags(Template template, XmlElementDescriptor descriptor, PsiFile file, XmlTag context) {

    if (!WebEditorOptions.getInstance().isAutomaticallyInsertRequiredSubTags()) return false;
    List<XmlElementDescriptor> requiredSubTags = GenerateXmlTagAction.getRequiredSubTags(descriptor);
    if (!requiredSubTags.isEmpty()) {
      template.addTextSegment(">");
      template.setToReformat(true);
    }
    for (XmlElementDescriptor subTag : requiredSubTags) {
      if (subTag == null) { // placeholder for smart completion
        template.addTextSegment("<");
        template.addVariable(new MacroCallNode(new CompleteSmartMacro()), true);
        continue;
      }
      String qname = subTag.getName();
      if (subTag instanceof XmlElementDescriptorImpl) {
        String prefixByNamespace = context.getPrefixByNamespace(((XmlElementDescriptorImpl)subTag).getNamespace());
        if (StringUtil.isNotEmpty(prefixByNamespace)) {
          qname = prefixByNamespace + ":" + subTag.getName();
        }
      }
      template.addTextSegment("<" + qname);
      addRequiredAttributes(subTag, null, template, file);
      completeTagTail(template, subTag, file, context, false);
    }
    if (!requiredSubTags.isEmpty()) {
      addTagEnd(template, descriptor, context);
    }
    return !requiredSubTags.isEmpty();
  }

  private static void completeTagTail(Template template, XmlElementDescriptor descriptor, PsiFile file, XmlTag context, boolean firstLevel) {
    boolean completeIt = !firstLevel || descriptor.getAttributesDescriptors(null).length == 0;
    switch (descriptor.getContentType()) {
      case XmlElementDescriptor.CONTENT_TYPE_UNKNOWN:
        return;
      case XmlElementDescriptor.CONTENT_TYPE_EMPTY:
        if (completeIt) {
          template.addTextSegment(closeTag(context));
        }
        break;
      case XmlElementDescriptor.CONTENT_TYPE_MIXED:
         if (completeIt) {
           template.addTextSegment(">");
           if (firstLevel) {
             template.addEndVariable();
           }
           else {
             template.addVariable(new MacroCallNode(new CompleteMacro()), true);
           }
           addTagEnd(template, descriptor, context);
         }
         break;
       default:
         if (!addRequiredSubTags(template, descriptor, file, context)) {
           if (completeIt) {
             template.addTextSegment(">");
             template.addEndVariable();
             addTagEnd(template, descriptor, context);
           }
         }
         break;
    }
  }

  private static void addTagEnd(Template template, XmlElementDescriptor descriptor, XmlTag context) {
    template.addTextSegment("</" + descriptor.getName(context) + ">");
  }

  private static boolean isTagFromHtml(final XmlTag tag) {
    final String ns = tag.getNamespace();
    return XmlUtil.XHTML_URI.equals(ns) || XmlUtil.HTML_URI.equals(ns);
  }
}
