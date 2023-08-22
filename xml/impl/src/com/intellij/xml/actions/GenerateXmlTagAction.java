// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.actions;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.actions.SimpleCodeInsightAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.impl.LookupCellRenderer;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.macro.CompleteMacro;
import com.intellij.codeInsight.template.macro.CompleteSmartMacro;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.xml.XmlContentDFA;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.xml.XMLConstants;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public final class GenerateXmlTagAction extends SimpleCodeInsightAction {
  public static final ThreadLocal<String> TEST_THREAD_LOCAL = new ThreadLocal<>();
  private final static Logger LOG = Logger.getInstance(GenerateXmlTagAction.class);

  @Override
  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    if (!EditorModificationUtil.checkModificationAllowed(editor)) return;
    try {
      final XmlTag contextTag = getContextTag(editor, file);
      if (contextTag == null) {
        throw new CommonRefactoringUtil.RefactoringErrorHintException("Caret should be positioned inside a tag");
      }
      XmlElementDescriptor currentTagDescriptor = contextTag.getDescriptor();
      assert currentTagDescriptor != null;
      final XmlElementDescriptor[] descriptors = currentTagDescriptor.getElementsDescriptors(contextTag);
      Arrays.sort(descriptors, Comparator.comparing(PsiMetaData::getName));
      Consumer<XmlElementDescriptor> consumer = (selected) ->
        WriteCommandAction.writeCommandAction(project, file).withName(XmlBundle.message("generate.xml.tag")).run(() -> {
          if (selected == null) return;
          XmlTag newTag = createTag(contextTag, selected);

          PsiElement anchor = getAnchor(contextTag, editor, selected);
          if (anchor == null) { // insert it in the cursor position
            int offset = editor.getCaretModel().getOffset();
            Document document = editor.getDocument();
            document.insertString(offset, newTag.getText());
            PsiDocumentManager.getInstance(project).commitDocument(document);
            newTag = PsiTreeUtil.getParentOfType(file.findElementAt(offset + 1), XmlTag.class, false);
          }
          else {
            newTag = (XmlTag)contextTag.addAfter(newTag, anchor);
          }
          if (newTag != null) {
            generateTag(newTag, editor);
          }
        });
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        XmlElementDescriptor descriptor = ContainerUtil.find(descriptors,
                                                             xmlElementDescriptor -> xmlElementDescriptor.getName().equals(TEST_THREAD_LOCAL.get()));
        consumer.consume(descriptor);
      }
      else {
        JBPopupFactory.getInstance()
          .createPopupChooserBuilder(List.of(descriptors))
          .setRenderer(new MyListCellRenderer())
          .setTitle(XmlBundle.message("choose.tag.name"))
          .setItemChosenCallback(consumer)
          .setNamerForFiltering(o -> o.getName())
          .createPopup()
          .showInBestPositionFor(editor);
      }
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      HintManager.getInstance().showErrorHint(editor, e.getMessage());
    }
  }

  @Nullable
  private static XmlTag getAnchor(@NotNull XmlTag contextTag, Editor editor, XmlElementDescriptor selected) {
    XmlContentDFA contentDFA = XmlContentDFA.getContentDFA(contextTag);
    int offset = editor.getCaretModel().getOffset();
    if (contentDFA == null) {
      return null;
    }
    XmlTag anchor = null;
    boolean previousPositionIsPossible = true;
    for (XmlTag subTag : contextTag.getSubTags()) {
      if (contentDFA.getPossibleElements().contains(selected)) {
        if (subTag.getTextOffset() > offset) {
          break;
        }
        anchor = subTag;
        previousPositionIsPossible = true;
      }
      else {
        previousPositionIsPossible = false;
      }
      contentDFA.transition(subTag);
    }
    return previousPositionIsPossible ? null : anchor;
  }

  public static void generateTag(@NotNull XmlTag newTag, Editor editor) {
    generateRaw(newTag);
    final XmlTag restored = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(newTag);
    if (restored == null) {
      LOG.error("Could not restore tag: " + newTag.getText());
    }
    TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(restored);
    replaceElements(restored, builder);
    builder.run(editor, false);
  }

  private static void generateRaw(final @NotNull XmlTag newTag) {
    XmlElementDescriptor selected = newTag.getDescriptor();
    if (selected == null) return;
    switch (selected.getContentType()) {
      case XmlElementDescriptor.CONTENT_TYPE_EMPTY -> {
        newTag.collapseIfEmpty();
        ASTNode node = newTag.getNode();
        assert node != null;
        ASTNode elementEnd = node.findChildByType(XmlTokenType.XML_EMPTY_ELEMENT_END);
        if (elementEnd == null) {
          LeafElement emptyTagEnd =
            Factory.createSingleLeafElement(XmlTokenType.XML_EMPTY_ELEMENT_END, "/>", 0, 2, null, newTag.getManager());
          node.addChild(emptyTagEnd);
        }
      }
      case XmlElementDescriptor.CONTENT_TYPE_MIXED -> newTag.getValue().setText("");
    }
    for (XmlAttributeDescriptor descriptor : selected.getAttributesDescriptors(newTag)) {
      if (descriptor.isRequired()) {
        newTag.setAttribute(descriptor.getName(), "");
      }
    }
    List<XmlElementDescriptor> tags = getRequiredSubTags(selected);
    for (XmlElementDescriptor descriptor : tags) {
      if (descriptor == null) {
        XmlTag tag = XmlElementFactory.getInstance(newTag.getProject()).createTagFromText("<", newTag.getLanguage());
        newTag.addSubTag(tag, false);
      }
      else {
        XmlTag subTag = newTag.addSubTag(createTag(newTag, descriptor), false);
        generateRaw(subTag);
      }
    }
  }

  public static List<XmlElementDescriptor> getRequiredSubTags(XmlElementDescriptor selected) {
    XmlElementsGroup topGroup = selected.getTopGroup();
    if (topGroup == null) return Collections.emptyList();
    return computeRequiredSubTags(topGroup);
  }

  private static void replaceElements(XmlTag tag, TemplateBuilder builder) {
    for (XmlAttribute attribute : tag.getAttributes()) {
      XmlAttributeValue value = attribute.getValueElement();
      if (value != null) {
        builder.replaceElement(value, TextRange.from(1, 0), new MacroCallNode(new CompleteMacro()));
      }
    }
    if ("<".equals(tag.getText())) {
      builder.replaceElement(tag, TextRange.from(1, 0), new MacroCallNode(new CompleteSmartMacro()));
    }
    else if (tag.getSubTags().length == 0) {
      int i = tag.getText().indexOf("></");
      if (i > 0) {
        builder.replaceElement(tag, TextRange.from(i + 1, 0), new MacroCallNode(new CompleteMacro()));
      }
    }
    for (XmlTag subTag : tag.getSubTags()) {
      replaceElements(subTag, builder);
    }
  }

  private static XmlTag createTag(@NotNull XmlTag contextTag, @NotNull XmlElementDescriptor descriptor) {
    String namespace = getNamespace(descriptor);
    XmlTag tag = contextTag.createChildTag(descriptor.getName(), namespace, null, false);
    PsiElement lastChild = tag.getLastChild();
    assert lastChild != null;
    lastChild.delete(); // remove XML_EMPTY_ELEMENT_END
    return tag;
  }

  private static @NlsSafe String getNamespace(XmlElementDescriptor descriptor) {
    return descriptor instanceof XmlElementDescriptorImpl ? ((XmlElementDescriptorImpl)descriptor).getNamespace() : "";
  }

  @Nullable
  private static XmlTag getContextTag(Editor editor, PsiFile file) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    XmlTag tag = null;
    if (element != null) {
      tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
    }
    if (tag == null) {
      tag = ((XmlFile)file).getRootTag();
    }
    return tag;
  }

  private static List<XmlElementDescriptor> computeRequiredSubTags(XmlElementsGroup group) {

    if (group.getMinOccurs() < 1) return Collections.emptyList();
    switch (group.getGroupType()) {
      case LEAF -> {
        XmlElementDescriptor descriptor = group.getLeafDescriptor();
        
        if (descriptor == null) return Collections.emptyList();
        
        PsiElement declaration = descriptor.getDeclaration();
        // don't add abstract elements to required sub tags list
        if (declaration instanceof XmlTag) {
          XmlTag tag = (XmlTag)descriptor.getDeclaration();
          String abstractValue = tag.getAttributeValue("abstract", XMLConstants.W3C_XML_SCHEMA_NS_URI);
          if ("true".equals(abstractValue)) {
            return Collections.emptyList();
          }
        }
        
        return Collections.singletonList(descriptor);
      }
      case CHOICE -> {
        LinkedHashSet<XmlElementDescriptor> set = null;
        for (XmlElementsGroup subGroup : group.getSubGroups()) {
          List<XmlElementDescriptor> descriptors = computeRequiredSubTags(subGroup);
          if (set == null) {
            set = new LinkedHashSet<>(descriptors);
          }
          else {
            set.retainAll(descriptors);
          }
        }
        if (set == null || set.isEmpty()) {
          return Collections.singletonList(null); // placeholder for smart completion
        }
        return new ArrayList<>(set);
      }
      default -> {
        ArrayList<XmlElementDescriptor> list = new ArrayList<>();
        for (XmlElementsGroup subGroup : group.getSubGroups()) {
          list.addAll(computeRequiredSubTags(subGroup));
        }
        return list;
      }
    }
  }

  private static boolean isInsideTagBody(XmlTag contextTag, @NotNull Editor editor) {
    return contextTag.getValue().getTextRange().contains(editor.getCaretModel().getOffset());
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (!(file instanceof XmlFile)) return false;

    XmlTag contextTag = getContextTag(editor, file);
    return contextTag != null && isInsideTagBody(contextTag, editor) && contextTag.getDescriptor() != null;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private static class MyListCellRenderer implements ListCellRenderer<XmlElementDescriptor> {
    private final JPanel myPanel;
    private final JLabel myNameLabel;
    private final JLabel myNSLabel;

    MyListCellRenderer() {
      myPanel = new JPanel(new BorderLayout());
      myPanel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
      myNameLabel = new JLabel();

      myPanel.add(myNameLabel, BorderLayout.WEST);
      myPanel.add(new JLabel("     "));
      myNSLabel = new JLabel();
      myPanel.add(myNSLabel, BorderLayout.EAST);

      Font font = EditorFontType.getGlobalPlainFont();
      myNameLabel.setFont(font);
      myNSLabel.setFont(font);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends XmlElementDescriptor> list,
                                                  XmlElementDescriptor value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      Color backgroundColor = isSelected ? list.getSelectionBackground() : list.getBackground();

      myNameLabel.setText(value.getName());
      myNameLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
      myPanel.setBackground(backgroundColor);

      myNSLabel.setText(getNamespace(value));
      myNSLabel.setForeground(LookupCellRenderer.getGrayedForeground(isSelected));
      myNSLabel.setBackground(backgroundColor);

      return myPanel;
    }
  }
}
