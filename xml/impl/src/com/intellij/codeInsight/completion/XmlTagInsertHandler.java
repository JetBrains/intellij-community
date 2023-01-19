// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression;
import com.intellij.codeInsight.editorActions.XmlEditUtil;
import com.intellij.codeInsight.editorActions.XmlTagNameSynchronizer;
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
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.html.dtd.HtmlAttributeDescriptorImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.XmlExtension.AttributeValuePresentation;
import com.intellij.xml.XmlTagRuleProvider;
import com.intellij.xml.actions.GenerateXmlTagAction;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import kotlin.collections.ArraysKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class XmlTagInsertHandler implements InsertHandler<LookupElement> {
  public static final XmlTagInsertHandler INSTANCE = new XmlTagInsertHandler();

  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
    Project project = context.getProject();
    Editor editor = context.getEditor();
    int startOffset = context.getStartOffset();
    Document document = InjectedLanguageUtil.getTopLevelEditor(editor).getDocument();
    Ref<PsiElement> currentElementRef = Ref.create();
    // Need to insert " " to prevent creating tags like <tagThis is my text
    XmlTagNameSynchronizer.runWithoutCancellingSyncTagsEditing(document, () -> {
      final int offset = editor.getCaretModel().getOffset();
      editor.getDocument().insertString(offset, " ");
      PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
      currentElementRef.set(context.getFile().findElementAt(startOffset));
      editor.getDocument().deleteString(offset, offset + 1);
    });

    final XmlTag tag = PsiTreeUtil.getContextOfType(currentElementRef.get(), XmlTag.class, true);

    if (tag == null) return;

    if (context.getCompletionChar() != Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
      context.setAddCompletionChar(false);
    }

    if (XmlUtil.getTokenOfType(tag, XmlTokenType.XML_TAG_END) == null &&
        XmlUtil.getTokenOfType(tag, XmlTokenType.XML_EMPTY_ELEMENT_END) == null) {

        insertIncompleteTag(context.getCompletionChar(), editor, tag);
    }
    else if (context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();

      int caretOffset = editor.getCaretModel().getOffset();

      XmlTag otherTag = PsiTreeUtil.getParentOfType(context.getFile().findElementAt(caretOffset), XmlTag.class);

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
          editor.getDocument().insertString(sOffset, otherTag.getName());
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
    XmlTag originalElement = CompletionUtil.getOriginalElement(tag);
    XmlElementDescriptor descriptor = originalElement != null ? originalElement.getDescriptor() : tag.getDescriptor();
    if (descriptor == null) return;
    final Project project = editor.getProject();
    TemplateManager templateManager = TemplateManager.getInstance(project);
    Template template = templateManager.createTemplate("", "");

    template.setToIndent(true);

    // temp code
    PsiFile containingFile = tag.getContainingFile();
    boolean fileHasHtml = HtmlUtil.hasHtml(containingFile);
    // Non-html code like Pug embedded in HTML template
    if (fileHasHtml && !tag.getLanguage().isKindOf(XMLLanguage.INSTANCE)) return;
    boolean htmlCode = fileHasHtml || HtmlUtil.supportsXmlTypedHandlers(containingFile);
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
      public void templateFinished(@NotNull final Template template, boolean brokenOff) {
        final int offset = editor.getCaretModel().getOffset();

        if (chooseAttributeName && offset > 0) {
          char c = editor.getDocument().getCharsSequence().charAt(offset - 1);
          if (c == '/' || (c == ' ' && brokenOff)) {
            WriteCommandAction.writeCommandAction(project).run(() -> editor.getDocument().replaceString(offset, offset + 3, ">"));
          }
        }
      }

      @Override
      public void templateCancelled(final Template template) {
        if (myAttrValueMarker == null) {
          return;
        }

        if (UndoManager.getInstance(project).isUndoOrRedoInProgress()) {
          return;
        }

        if (chooseAttributeName && myAttrValueMarker.isValid()) {
          final int startOffset = myAttrValueMarker.getStartOffset();
          final int endOffset = myAttrValueMarker.getEndOffset();
          WriteCommandAction.writeCommandAction(project).run(() -> editor.getDocument().replaceString(startOffset, endOffset, ">"));
        }
      }
    });
  }

  @Nullable
  private static StringBuilder addRequiredAttributes(XmlElementDescriptor descriptor,
                                                     @Nullable XmlTag tag,
                                                     Template template,
                                                     PsiFile containingFile) {

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
          extension.getAttributeValuePresentation(tag, attributeName, XmlEditUtil.getAttributeQuote(containingFile));
        boolean htmlCode = HtmlUtil.hasHtml(containingFile) || HtmlUtil.supportsXmlTypedHandlers(containingFile);
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
    if (completionChar == '>' || (completionChar == '/' && indirectRequiredAttrs != null)) {
      template.addTextSegment(">");

      if (indirectRequiredAttrs != null) template.addTextSegment(indirectRequiredAttrs.toString());
      template.addEndVariable();

      if ((!(tag instanceof HtmlTag) || !HtmlUtil.isSingleHtmlTag(tag, true)) && tag.getAttributes().length == 0) {
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
        completeAttribute(tag.getContainingFile(), template);
        return true;
      }
    }
    else if (completionChar == Lookup.AUTO_INSERT_SELECT_CHAR || completionChar == Lookup.NORMAL_SELECT_CHAR || completionChar == Lookup.REPLACE_SELECT_CHAR) {
      if (WebEditorOptions.getInstance().isAutomaticallyInsertClosingTag() && isHtmlCode && HtmlUtil.isSingleHtmlTag(tag, true)) {
        if (hasOwnAttributes(descriptor, tag)) {
          template.addEndVariable();
        }
        template.addTextSegment(HtmlUtil.isHtmlTag(tag) ? ">" : closeTag(tag));
      }
      else {
        if (needAtLeastOneAttribute(tag) && WebEditorOptions.getInstance().isAutomaticallyStartAttribute() && tag.getAttributes().length == 0
            && template.getSegmentsCount() == 0) {
          completeAttribute(tag.getContainingFile(), template);
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
    CodeStyleSettings settings = CodeStyle.getSettings(tag.getContainingFile());
    boolean html = HtmlUtil.isHtmlTag(tag);
    boolean needsSpace = (html && settings.getCustomSettings(HtmlCodeStyleSettings.class).HTML_SPACE_INSIDE_EMPTY_TAG) ||
                         (!html && settings.getCustomSettings(XmlCodeStyleSettings.class).XML_SPACE_INSIDE_EMPTY_TAG);
    return needsSpace ? " />" : "/>";
  }

  private static void completeAttribute(PsiFile file, Template template) {
    template.addTextSegment(" ");
    template.addVariable(new MacroCallNode(new CompleteMacro()), true);
    template.addTextSegment("=" + XmlEditUtil.getAttributeQuote(file));
    template.addEndVariable();
    template.addTextSegment(XmlEditUtil.getAttributeQuote(file));
  }

  private static boolean needAtLeastOneAttribute(XmlTag tag) {
    for (XmlTagRuleProvider ruleProvider : XmlTagRuleProvider.EP_NAME.getExtensionList()) {
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
    boolean completeIt = (!firstLevel || !canHaveAttributes(descriptor, context))
                         && (file == null || XmlExtension.getExtension(file).shouldCompleteTag(context));
    switch (descriptor.getContentType()) {
      case XmlElementDescriptor.CONTENT_TYPE_UNKNOWN -> {}
      case XmlElementDescriptor.CONTENT_TYPE_EMPTY -> {
        if (completeIt) {
          template.addTextSegment(closeTag(context));
        }
      }
      case XmlElementDescriptor.CONTENT_TYPE_MIXED -> {
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
      }
      default -> {
        if (!addRequiredSubTags(template, descriptor, file, context)) {
          if (completeIt) {
            template.addTextSegment(">");
            template.addEndVariable();
            addTagEnd(template, descriptor, context);
          }
        }
      }
    }
  }

  private static boolean hasOwnAttributes(XmlElementDescriptor descriptor, XmlTag tag) {
    return ContainerUtil.find(descriptor.getAttributesDescriptors(tag),
                              attr -> attr instanceof HtmlAttributeDescriptorImpl && HtmlUtil.isOwnHtmlAttribute(attr)) != null;
  }

  private static boolean canHaveAttributes(XmlElementDescriptor descriptor, XmlTag context) {
    XmlAttributeDescriptor[] attributes = descriptor.getAttributesDescriptors(context);
    int required = WebEditorOptions.getInstance().isAutomaticallyInsertRequiredAttributes() ?
                   ArraysKt.count(attributes, (attribute) -> attribute.isRequired() && context.getAttribute(attribute.getName()) == null) :
                   0;
    return attributes.length - required > 0 ;
  }

  private static void addTagEnd(Template template, XmlElementDescriptor descriptor, XmlTag context) {
    template.addTextSegment("</" + descriptor.getName(context) + ">");
  }

  private static boolean isTagFromHtml(final XmlTag tag) {
    final String ns = tag.getNamespace();
    return XmlUtil.XHTML_URI.equals(ns) || XmlUtil.HTML_URI.equals(ns);
  }
}
